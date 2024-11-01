package de.shockbase.levelborder.config;

import de.shockbase.levelborder.Levelborder;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PlayerConfig {

    private static File file;
    private static FileConfiguration fileConfiguration;

    public static void setup(Player player) {
        JavaPlugin plugin = Levelborder.getInstance();

        file = new File(plugin.getDataFolder(), player.getUniqueId() + ".yml");
        if (!file.exists()) {
            try {
                if (file.createNewFile()) {
                    Bukkit.getLogger().info("[LevelBorder] new config file created for: " + player.getName());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        fileConfiguration = YamlConfiguration.loadConfiguration(file);
    }

    public static FileConfiguration getPlayerConfig() {
        return fileConfiguration;
    }

    public static void save() {
        try {
            fileConfiguration.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void reset(Player player) {
        JavaPlugin plugin = Levelborder.getInstance();
        String playerName = player.getName();

        long startTime = PlayerConfig.getPlayerConfig().getLong("startTime");
        Location worldOrigin = PlayerConfig.getPlayerConfig().getLocation("borderCenter");
        Location netherOrigin = PlayerConfig.getPlayerConfig().getLocation("netherSpawnLocation");
        Location endOrigin = PlayerConfig.getPlayerConfig().getLocation("endSpawnLocation");
        int minBorderRadius = PlayerConfig.getPlayerConfig().getInt("minBorderRadius");
        int level = PlayerConfig.getPlayerConfig().getInt("level");

        int radius = minBorderRadius + level;
        long currentTime = System.currentTimeMillis() / 1000;
        int time = (int) (currentTime - startTime) + 10;

        List<Object> exclude = getLivingEntitiesTypesList();

        if (worldOrigin != null) {
            resetChunksByOrigin(worldOrigin, radius, time, exclude);
        }

        if (netherOrigin != null) {
            resetChunksByOrigin(netherOrigin, radius, time, exclude);
        }

        if (endOrigin != null) {
            resetChunksByOrigin(endOrigin, radius, time, exclude);
        }

        deleteFile(plugin.getDataFolder().getPath(), player.getUniqueId() + ".yml", playerName + " configuration deleted");
        deleteFile(Bukkit.getServer().getWorldContainer().getPath(), player.getWorld().getName() + "/playerdata/" + player.getUniqueId() + ".dat", playerName + " player data deleted");
        deleteFile(Bukkit.getServer().getWorldContainer().getPath(), player.getWorld().getName() + "/stats/" + player.getUniqueId() + ".json", playerName + " player data deleted");
    }

    private static void deleteFile(String parent, String child, String info) {
        File playerStats = new File(parent, child);
        if (playerStats.exists()) {
            if (playerStats.delete()) {
                Bukkit.getLogger().info("[LevelBorder] " + info);
            }
        }
    }

    private static void resetChunksByOrigin(Location origin, int radius, int time, List<Object> exclude) {
        Runnable runnable = () -> {
            try {
                CoreProtectAPI CoreProtect = getCoreProtect();
                if (CoreProtect != null) { //Ensure we have access to the API
                    List<String[]> lookup = CoreProtect.performRollback(time, null, null, null, exclude, null, radius, origin);

                    if (lookup != null) {
                        Bukkit.getLogger().info("[LevelBorder] " + lookup.size() + " changes rolled back in " + origin.getWorld().getName());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        };
        Thread thread = new Thread(runnable);
        thread.start();
    }

    private static CoreProtectAPI getCoreProtect() {
        Plugin plugin = Bukkit.getServer().getPluginManager().getPlugin("CoreProtect");

        if (!(plugin instanceof CoreProtect)) {
            return null;
        }

        CoreProtectAPI CoreProtect = ((CoreProtect) plugin).getAPI();
        if (!CoreProtect.isEnabled()) {
            return null;
        }

        if (CoreProtect.APIVersion() < 7) {
            return null;
        }

        return CoreProtect;
    }

    private static List<Object> getLivingEntitiesTypesList() {
        List<Object> list = new ArrayList<>();
        //A
        list.add(EntityType.AXOLOTL);
        //B
        list.add(EntityType.BAT);
        list.add(EntityType.BEE);
        list.add(EntityType.BLAZE);
        //C
        list.add(EntityType.CAT);
        list.add(EntityType.CAVE_SPIDER);
        list.add(EntityType.CHICKEN);
        list.add(EntityType.COD);
        list.add(EntityType.COW);
        list.add(EntityType.CREEPER);
        //D
        list.add(EntityType.DOLPHIN);
        list.add(EntityType.DONKEY);
        list.add(EntityType.DROWNED);
        //E
        list.add(EntityType.ENDERMAN);
        list.add(EntityType.ENDER_DRAGON);
        list.add(EntityType.ELDER_GUARDIAN);
        list.add(EntityType.EVOKER);
        list.add(EntityType.ENDERMITE);
        //F
        list.add(EntityType.FOX);
        //G
        list.add(EntityType.GIANT);
        list.add(EntityType.GHAST);
        list.add(EntityType.GOAT);
        list.add(EntityType.GLOW_SQUID);
        list.add(EntityType.GUARDIAN);
        //H
        list.add(EntityType.HUSK);
        list.add(EntityType.HORSE);
        list.add(EntityType.HOGLIN);
        //I
        list.add(EntityType.IRON_GOLEM);
        list.add(EntityType.ILLUSIONER);
        //J,K,L
        list.add(EntityType.LLAMA);
        //M
        //list.add(EntityType.MUSHROOM_COW);
        list.add(EntityType.MAGMA_CUBE);
        list.add(EntityType.MULE);
        //N,O
        list.add(EntityType.OCELOT);
        //P
        list.add(EntityType.PANDA);
        list.add(EntityType.PARROT);
        list.add(EntityType.PHANTOM);
        list.add(EntityType.PIG);
        list.add(EntityType.PIGLIN);
        list.add(EntityType.PIGLIN_BRUTE);
        list.add(EntityType.PILLAGER);
        list.add(EntityType.POLAR_BEAR);
        //Q,R
        list.add(EntityType.RABBIT);
        list.add(EntityType.RAVAGER);
        //S
        list.add(EntityType.SPIDER);
        list.add(EntityType.SKELETON);
        list.add(EntityType.SKELETON_HORSE);
        list.add(EntityType.SLIME);
        list.add(EntityType.SALMON);
        list.add(EntityType.SHEEP);
        list.add(EntityType.SILVERFISH);
        //list.add(EntityType.SNOWMAN);
        list.add(EntityType.SQUID);
        list.add(EntityType.STRAY);
        list.add(EntityType.STRIDER);
        //T
        list.add(EntityType.TURTLE);
        list.add(EntityType.TRADER_LLAMA);
        //U,V
        list.add(EntityType.VILLAGER);
        list.add(EntityType.VINDICATOR);
        list.add(EntityType.VEX);
        //W
        list.add(EntityType.WANDERING_TRADER);
        list.add(EntityType.WITCH);
        list.add(EntityType.WITHER);
        list.add(EntityType.WITHER_SKELETON);
        list.add(EntityType.WOLF);
        //X,Y,Z
        list.add(EntityType.ZOMBIE);
        list.add(EntityType.ZOMBIE_HORSE);
        list.add(EntityType.ZOGLIN);
        list.add(EntityType.ZOMBIFIED_PIGLIN);
        list.add(EntityType.ZOMBIE_VILLAGER);

        return list;
    }
}


