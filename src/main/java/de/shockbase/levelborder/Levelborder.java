package de.shockbase.levelborder;

import com.github.yannicklamprecht.worldborder.api.*;
import com.google.common.collect.Maps;
import de.shockbase.levelborder.commands.LevelBorderCommands;
import de.shockbase.levelborder.config.PlayerConfig;
import de.shockbase.levelborder.timer.Timer;
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

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class Levelborder extends JavaPlugin implements Listener {

    private final Map<UUID, Timer> timers = Maps.newHashMap();
    private WorldBorderApi worldBorderApi;

    public static JavaPlugin getInstance() {
        return (JavaPlugin) Bukkit.getPluginManager().getPlugin("Levelborder");
    }

    @Override
    public void onEnable() {
        // Plugin startup logic
        saveConfig();

        //commands
        Objects.requireNonNull(getCommand("lb")).setExecutor(new LevelBorderCommands());

        RegisteredServiceProvider<WorldBorderApi> worldBorderApiRegisteredServiceProvider = getServer().getServicesManager().getRegistration(WorldBorderApi.class);

        if (worldBorderApiRegisteredServiceProvider == null) {
            getLogger().info("[LevelBorder] WorldBorderAPI not found!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.worldBorderApi = worldBorderApiRegisteredServiceProvider.getProvider();
        getServer().getPluginManager().registerEvents(this, this);
        World world = getServer().getWorld("world");
        Objects.requireNonNull(world).setGameRule(GameRule.SPECTATORS_GENERATE_CHUNKS, false);
        Objects.requireNonNull(world).setGameRule(GameRule.SPAWN_RADIUS, 1000000);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        saveConfig();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        PlayerConfig.setup(player);
        PlayerConfig.getPlayerConfig().addDefault("borderCenter", player.getLocation());
        PlayerConfig.getPlayerConfig().addDefault("minBorderRadius", 5);
        PlayerConfig.getPlayerConfig().addDefault("elapsedTime", 0);
        PlayerConfig.getPlayerConfig().addDefault("growSpawnTree", true);
        PlayerConfig.getPlayerConfig().addDefault("spawnTreeGrown", false);
        PlayerConfig.getPlayerConfig().addDefault("startTime", System.currentTimeMillis() / 1000);
        PlayerConfig.getPlayerConfig().addDefault("dead", false);
        PlayerConfig.getPlayerConfig().addDefault("level", player.getLevel());
        PlayerConfig.getPlayerConfig().addDefault("overWorldName", player.getWorld().getName());
        PlayerConfig.getPlayerConfig().options().copyDefaults(true);
        PlayerConfig.save();

        Timer timer = new Timer();
        timer.setElapsedTime(PlayerConfig.getPlayerConfig().getInt("elapsedTime"));
        timer.start(player);

        if (PlayerConfig.getPlayerConfig().getBoolean("dead")) {
            player.setGameMode(GameMode.SPECTATOR);
            timer.pause();
        } else {

            setWorldBorder(player, PlayerConfig.getPlayerConfig().getLocation("borderCenter"));

            if (PlayerConfig.getPlayerConfig().getBoolean("growSpawnTree") && !PlayerConfig.getPlayerConfig().getBoolean("spawnTreeGrown")) {
                if (generateSpawnTree(player)) {
                    PlayerConfig.getPlayerConfig().set("spawnTreeGrown", true);
                    PlayerConfig.getPlayerConfig().set("borderCenter", player.getLocation());
                    PlayerConfig.save();
                }
            }
        }

        this.timers.put(player.getUniqueId(), timer);
        showPlayerGreetings(player);
    }

    @EventHandler
    public void onPlayerLevelChangeEvent(PlayerLevelChangeEvent event) {
        Player player = event.getPlayer();
        int minBorderRadius = PlayerConfig.getPlayerConfig().getInt("minBorderRadius");
        changeWorldBorder(player, event.getOldLevel() * 2 + minBorderRadius, event.getNewLevel() * 2 + minBorderRadius);
        PlayerConfig.getPlayerConfig().set("level", event.getNewLevel());
        PlayerConfig.save();
    }

    @EventHandler
    public void onPlayerDeathEvent(PlayerDeathEvent event) {
        Player player = event.getEntity();
        player.setGameMode(GameMode.SPECTATOR);
        PlayerConfig.getPlayerConfig().set("dead", true);
        PlayerConfig.save();
        Timer timer = timers.get(player.getUniqueId());
        timer.pause();
        removeWorldBorder(player);
    }

    @EventHandler
    public void onPlayerQuitEvent(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Timer timer = timers.get(player.getUniqueId());
        PlayerConfig.getPlayerConfig().set("elapsedTime", timer.getElapsedTime());
        PlayerConfig.save();
        timer.stop();
    }

    @EventHandler
    public void onPlayerRespawnEvent(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        showRestartInfo(player);
    }

    @EventHandler
    public void onPlayerChangedWorldEvent(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        String worldName = player.getWorld().getName();

        if (worldName.equalsIgnoreCase(PlayerConfig.getPlayerConfig().getString("overWorldName"))) {
            setWorldBorder(player, PlayerConfig.getPlayerConfig().getLocation("borderCenter"));
        }

        if (worldName.equals(PlayerConfig.getPlayerConfig().getString("netherWorldName"))) {
            PlayerConfig.getPlayerConfig().addDefault("netherSpawnLocation", player.getLocation());
            setWorldBorder(player, PlayerConfig.getPlayerConfig().getLocation("netherSpawnLocation"));
        }

        if (worldName.equals(PlayerConfig.getPlayerConfig().getString("endWorldName"))) {
            PlayerConfig.getPlayerConfig().addDefault("endSpawnLocation", player.getLocation());
            setWorldBorder(player, PlayerConfig.getPlayerConfig().getLocation("endSpawnLocation"));
        }
    }

    @EventHandler
    public void onPlayerPortalEvent(PlayerPortalEvent event) {
        //Player player = event.getPlayer();
        //Location fromWorld = event.getFrom();
        Location toWorld = event.getTo();

        if (toWorld.getWorld().getName().endsWith("_nether")) {
            PlayerConfig.getPlayerConfig().addDefault("netherWorldName", toWorld.getWorld().getName());
            PlayerConfig.save();
        }

        if (toWorld.getWorld().getName().endsWith("_the_end")) {
            PlayerConfig.getPlayerConfig().addDefault("endWorldName", toWorld.getWorld().getName());
            PlayerConfig.save();
        }

        //Bukkit.getLogger().info(player.getName() + " enters " + toWorld.getWorld().getName() + " at " + toWorld + " from " + fromWorld.getWorld().getName());
    }

    private void setWorldBorder(Player player, @Nullable Location playerBorderCenter) {
        if (this.worldBorderApi instanceof PersistentWorldBorderApi) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                PersistentWorldBorderApi persistentWorldBorderApi = ((PersistentWorldBorderApi) worldBorderApi);
                WorldBorderData worldBorderData = persistentWorldBorderApi.getWorldBorderData(player);
                if (worldBorderData != null) {
                    IWorldBorder worldBorder = worldBorderApi.getWorldBorder(player);
                    worldBorderData.applyAll(worldBorder);
                    worldBorder.send(player, WorldBorderAction.INITIALIZE);
                }
            }, 20);
        }
        this.worldBorderApi.setBorder(player, player.getLevel() * 2 + PlayerConfig.getPlayerConfig().getInt("minBorderRadius"), playerBorderCenter);
    }

    private void removeWorldBorder(Player player) {
        this.worldBorderApi.resetWorldBorderToGlobal(player);
    }

    private void changeWorldBorder(Player player, int oldWorldSize, int newWorldSize) {
        IWorldBorder iWorldBorder = this.worldBorderApi.getWorldBorder(player);
        iWorldBorder.lerp(oldWorldSize, newWorldSize, 500);
        iWorldBorder.send(player, WorldBorderAction.LERP_SIZE);
    }

    private void showPlayerGreetings(Player player) {
        Title title;
        if (player.getGameMode() == GameMode.SPECTATOR) {
            showRestartInfo(player);
        } else {
            if (player.hasPlayedBefore()) {
                title = Title.title(
                        Component.text("Welcome back, ").color(TextColor.color(0xFFD700)).append(Component.text(player.getName()).decorate(TextDecoration.BOLD)).append(Component.text("!")),
                        Component.text("Your world will expand according to your exp level.").color(TextColor.color(0x55FFFF)).append(Component.text(" Good luck!").decorate(TextDecoration.BOLD))
                );
            } else {
                title = Title.title(
                        Component.text("Welcome, ").color(TextColor.color(0xFFD700)).append(Component.text(player.getName()).decorate(TextDecoration.BOLD)).append(Component.text("!")),
                        Component.text("Your world will expand according to your exp level.").color(TextColor.color(0x55FFFF)).append(Component.text(" Good luck!").decorate(TextDecoration.BOLD))
                );
            }
            player.showTitle(title);
        }
    }

    private void showRestartInfo(Player player) {
        Title title;
        title = Title.title(
                Component.text("Welcome back, ").color(TextColor.color(0xFFD700)).append(Component.text(player.getName()).decorate(TextDecoration.BOLD)).append(Component.text("!")),
                Component.text("You are in SPECTATOR mode. Enjoy the show!").color(TextColor.color(0xFF0000))
        );
        player.showTitle(title);
        player.sendMessage(Component.text("Type ").color(TextColor.color(0xFF0000)).append(Component.text("/lb reset").decorate(TextDecoration.ITALIC).decorate(TextDecoration.BOLD)).append(Component.text(" to try again.").color(TextColor.color(0xFF0000))));
    }

    private boolean generateSpawnTree(Player player) {
        Block blockUnderPlayer = player.getWorld().getBlockAt(player.getLocation().subtract(0, 1, 0));
        blockUnderPlayer.setType(Material.DIRT);
        player.teleport(player.getLocation().add(0, 100, 0));
        boolean spawnTreeGrown = player.getWorld().generateTree(blockUnderPlayer.getLocation().add(0, 1, 0), TreeType.TREE);
        Location playerLocation = blockUnderPlayer.getLocation().toHighestLocation().add(0.5, 1, 0.5);
        player.teleport(playerLocation);

        // Replace deprecated setBedSpawnLocation with setRespawnLocation
        player.setRespawnLocation(playerLocation, true);

        return spawnTreeGrown;
    }
}