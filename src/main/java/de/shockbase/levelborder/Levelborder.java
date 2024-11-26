package de.shockbase.levelborder;

import com.github.yannicklamprecht.worldborder.api.*;
import de.shockbase.levelborder.commands.LevelBorderCommands;
import de.shockbase.levelborder.config.PlayerConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class Levelborder extends JavaPlugin implements Listener {

    private static final int DEFAULT_MIN_BORDER_RADIUS = 5;
    private static final int BORDER_CHANGE_DURATION_MS = 500;
    private static final int SPAWN_TREE_HEIGHT_OFFSET = 100;

    private WorldBorderApi worldBorderApi;

    // Region: Plugin Lifecycle
    public static JavaPlugin getInstance() {
        return (JavaPlugin) Bukkit.getPluginManager().getPlugin("Levelborder");
    }

    @Override
    public void onEnable() {
        initializePlugin();
    }

    @Override
    public void onDisable() {
        saveConfig();
    }

    private void initializePlugin() {
        saveConfig();
        initializeCommands();
        initializeWorldBorderApi();
        initializeWorldSettings();
    }

    private void initializeCommands() {
        Objects.requireNonNull(getCommand("lb")).setExecutor(new LevelBorderCommands());
    }

    private void initializeWorldBorderApi() {
        RegisteredServiceProvider<WorldBorderApi> worldBorderApiProvider =
                getServer().getServicesManager().getRegistration(WorldBorderApi.class);

        if (worldBorderApiProvider == null) {
            handleWorldBorderApiMissing();
            return;
        }

        this.worldBorderApi = worldBorderApiProvider.getProvider();
        getServer().getPluginManager().registerEvents(this, this);
    }

    private void handleWorldBorderApiMissing() {
        getLogger().info("[LevelBorder] WorldBorderAPI not found!");
        getServer().getPluginManager().disablePlugin(this);
    }

    private void initializeWorldSettings() {
        World world = getServer().getWorld("world");
        if (world != null) {
            world.setGameRule(GameRule.SPECTATORS_GENERATE_CHUNKS, false);
            world.setGameRule(GameRule.SPAWN_RADIUS, 1000000);
        }
    }

    // Region: Player Event Handlers
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        initializePlayerConfig(player);
        handlePlayerGameState(player);
        showPlayerGreetings(player);
    }

    private void initializePlayerConfig(Player player) {
        PlayerConfig.setup(player);
        setupDefaultPlayerConfig(player);
        PlayerConfig.save();
    }

    private void setupDefaultPlayerConfig(Player player) {
        PlayerConfig.getPlayerConfig().addDefault("borderCenter", player.getLocation());
        PlayerConfig.getPlayerConfig().addDefault("minBorderRadius", DEFAULT_MIN_BORDER_RADIUS);
        PlayerConfig.getPlayerConfig().addDefault("elapsedTime", 0);
        PlayerConfig.getPlayerConfig().addDefault("growSpawnTree", true);
        PlayerConfig.getPlayerConfig().addDefault("spawnTreeGrown", false);
        PlayerConfig.getPlayerConfig().addDefault("startTime", System.currentTimeMillis() / 1000);
        PlayerConfig.getPlayerConfig().addDefault("dead", false);
        PlayerConfig.getPlayerConfig().addDefault("level", player.getLevel());
        PlayerConfig.getPlayerConfig().addDefault("overWorldName", player.getWorld().getName());
        PlayerConfig.getPlayerConfig().options().copyDefaults(true);
    }

    private void handlePlayerGameState(Player player) {
        if (PlayerConfig.getPlayerConfig().getBoolean("dead")) {
            setPlayerSpectatorMode(player);
        } else {
            initializePlayerWorld(player);
        }
    }

    private void setPlayerSpectatorMode(Player player) {
        player.setGameMode(GameMode.SPECTATOR);
    }

    private void initializePlayerWorld(Player player) {
        setWorldBorder(player, PlayerConfig.getPlayerConfig().getLocation("borderCenter"));
        handleSpawnTreeGeneration(player);
    }

    private void handleSpawnTreeGeneration(Player player) {
        if (PlayerConfig.getPlayerConfig().getBoolean("growSpawnTree")
                && !PlayerConfig.getPlayerConfig().getBoolean("spawnTreeGrown")) {
            if (generateSpawnTree(player)) {
                updateSpawnTreeConfig(player);
            }
        }
    }

    private void updateSpawnTreeConfig(Player player) {
        PlayerConfig.getPlayerConfig().set("spawnTreeGrown", true);
        PlayerConfig.getPlayerConfig().set("borderCenter", player.getLocation());
        PlayerConfig.save();
    }

    // Region: World Border Management
    private void setWorldBorder(Player player, @Nullable Location playerBorderCenter) {
        playerBorderCenter.subtract(0.5,0,0.5);
        handlePersistentWorldBorder(player);
        int borderSize = calculateBorderSize(player);
        this.worldBorderApi.setBorder(player, borderSize, playerBorderCenter);
    }

    private void handlePersistentWorldBorder(Player player) {
        if (!(this.worldBorderApi instanceof PersistentWorldBorderApi)) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {
            PersistentWorldBorderApi persistentApi = (PersistentWorldBorderApi) worldBorderApi;
            WorldBorderData borderData = persistentApi.getWorldBorderData(player);
            if (borderData != null) {
                IWorldBorder worldBorder = worldBorderApi.getWorldBorder(player);
                borderData.applyAll(worldBorder);
                worldBorder.send(player, WorldBorderAction.INITIALIZE);
            }
        }, 20);
    }

    private int calculateBorderSize(Player player) {
        return (player.getLevel() + PlayerConfig.getPlayerConfig().getInt("minBorderRadius")) * 2;
    }

    private void removeWorldBorder(Player player) {
        this.worldBorderApi.resetWorldBorderToGlobal(player);
    }

    private void changeWorldBorder(Player player, int oldWorldSize, int newWorldSize) {
        IWorldBorder worldBorder = this.worldBorderApi.getWorldBorder(player);
        worldBorder.lerp(oldWorldSize, newWorldSize, BORDER_CHANGE_DURATION_MS);
        worldBorder.send(player, WorldBorderAction.LERP_SIZE);
    }

    // Region: UI Components
    private void showPlayerGreetings(Player player) {
        if (player.getGameMode() == GameMode.SPECTATOR) {
            showRestartInfo(player);
        } else {
            showWelcomeMessage(player);
        }
    }

    private void showWelcomeMessage(Player player) {
        String welcomeText = player.hasPlayedBefore() ? "Welcome back, " : "Welcome, ";
        Title title = Title.title(
                Component.text(welcomeText).color(TextColor.color(0xFFD700))
                        .append(Component.text(player.getName()).decorate(TextDecoration.BOLD))
                        .append(Component.text("!")),
                Component.text("Your world will expand according to your exp level.")
                        .color(TextColor.color(0x55FFFF))
                        .append(Component.text(" Good luck!").decorate(TextDecoration.BOLD))
        );
        player.showTitle(title);
    }

    private void showRestartInfo(Player player) {
        Title title = Title.title(
                Component.text("Welcome back, ").color(TextColor.color(0xFFD700))
                        .append(Component.text(player.getName()).decorate(TextDecoration.BOLD))
                        .append(Component.text("!")),
                Component.text("You are in SPECTATOR mode. Enjoy the show!")
                        .color(TextColor.color(0xFF0000))
        );
        player.showTitle(title);

        Component resetMessage = Component.text("Type ")
                .color(TextColor.color(0xFF0000))
                .append(Component.text("/lb reset")
                        .decorate(TextDecoration.ITALIC)
                        .decorate(TextDecoration.BOLD))
                .append(Component.text(" to try again.")
                        .color(TextColor.color(0xFF0000)));
        player.sendMessage(resetMessage);
    }

    // Region: World Generation
    private boolean generateSpawnTree(Player player) {
        Block blockUnderPlayer = player.getWorld().getBlockAt(
                player.getLocation().subtract(0, 1, 0));
        blockUnderPlayer.setType(Material.DIRT);

        // Temporarily teleport player up to avoid tree generation issues
        player.teleport(player.getLocation().add(0, SPAWN_TREE_HEIGHT_OFFSET, 0));

        boolean spawnTreeGrown = player.getWorld().generateTree(
                blockUnderPlayer.getLocation().add(0, 1, 0),
                TreeType.TREE
        );

        // Teleport player to top of new tree
        Location playerLocation = blockUnderPlayer.getLocation()
                .toHighestLocation()
                .add(0.5, 1, 0.5);
        player.teleport(playerLocation);
        player.setRespawnLocation(playerLocation, true);

        return spawnTreeGrown;
    }

    // Region: Other Event Handlers
    @EventHandler
    public void onPlayerLevelChangeEvent(PlayerLevelChangeEvent event) {
        Player player = event.getPlayer();
        int minBorderRadius = PlayerConfig.getPlayerConfig().getInt("minBorderRadius");
        int currentBorderRadius = (event.getOldLevel() + minBorderRadius) * 2;
        int newBorderRadius = (event.getNewLevel() + minBorderRadius) * 2;
        changeWorldBorder(player, currentBorderRadius, newBorderRadius);
        PlayerConfig.getPlayerConfig().set("level", event.getNewLevel());
        PlayerConfig.save();
    }

    @EventHandler
    public void onPlayerDeathEvent(PlayerDeathEvent event) {
        Player player = event.getEntity();
        setPlayerSpectatorMode(player);
        PlayerConfig.getPlayerConfig().set("dead", true);
        PlayerConfig.save();
        removeWorldBorder(player);
    }

    @EventHandler
    public void onPlayerQuitEvent(PlayerQuitEvent event) {
        Player player = event.getPlayer();
    }

    @EventHandler
    public void onPlayerRespawnEvent(PlayerRespawnEvent event) {
        showRestartInfo(event.getPlayer());
    }

    @EventHandler
    public void onPlayerChangedWorldEvent(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        String worldName = player.getWorld().getName();
        handleWorldChange(player, worldName);
    }

    private void handleWorldChange(Player player, String worldName) {
        if (worldName.equalsIgnoreCase(PlayerConfig.getPlayerConfig().getString("overWorldName"))) {
            setWorldBorder(player, PlayerConfig.getPlayerConfig().getLocation("borderCenter"));
            return;
        }

        if (worldName.equals(PlayerConfig.getPlayerConfig().getString("netherWorldName"))) {
            PlayerConfig.getPlayerConfig().addDefault("netherSpawnLocation", player.getLocation());
            setWorldBorder(player, PlayerConfig.getPlayerConfig().getLocation("netherSpawnLocation"));
            return;
        }

        if (worldName.equals(PlayerConfig.getPlayerConfig().getString("endWorldName"))) {
            PlayerConfig.getPlayerConfig().addDefault("endSpawnLocation", player.getLocation());
            setWorldBorder(player, PlayerConfig.getPlayerConfig().getLocation("endSpawnLocation"));
        }
    }

    @EventHandler
    public void onPlayerPortalEvent(PlayerPortalEvent event) {
        String worldName = event.getTo().getWorld().getName();
        if (worldName.endsWith("_nether")) {
            PlayerConfig.getPlayerConfig().addDefault("netherWorldName", worldName);
            PlayerConfig.save();
        } else if (worldName.endsWith("_the_end")) {
            PlayerConfig.getPlayerConfig().addDefault("endWorldName", worldName);
            PlayerConfig.save();
        }
    }
}