package de.shockbase.levelborder.config;

import de.shockbase.levelborder.Levelborder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PlayerConfig {
    private static final Logger LOGGER = Bukkit.getLogger();
    private static final String PLUGIN_PREFIX = "[LevelBorder] ";

    // Configuration keys
    private static final String KEY_BORDER_CENTER = "borderCenter";
    private static final String KEY_NETHER_SPAWN = "netherSpawnLocation";
    private static final String KEY_END_SPAWN = "endSpawnLocation";
    private static final String KEY_MIN_BORDER_RADIUS = "minBorderRadius";
    private static final String KEY_LEVEL = "level";

    private static File configFile;
    private static FileConfiguration fileConfiguration;

    /**
     * Sets up the player configuration file
     * @param player The player to create/load configuration for
     */
    public static void setup(Player player) {
        JavaPlugin plugin = Levelborder.getInstance();

        configFile = new File(plugin.getDataFolder(), player.getUniqueId() + ".yml");
        if (!configFile.exists()) {
            createNewConfigFile(player);
        }

        fileConfiguration = YamlConfiguration.loadConfiguration(configFile);
    }

    /**
     * Creates a new configuration file for the player
     * @param player The player to create configuration for
     */
    private static void createNewConfigFile(Player player) {
        try {
            if (configFile.createNewFile()) {
                logInfo("New config file created for: " + player.getName());
            }
        } catch (IOException e) {
            logError("Failed to create config file for player: " + player.getName(), e);
        }
    }

    /**
     * @return The current player configuration
     */
    public static FileConfiguration getPlayerConfig() {
        return fileConfiguration;
    }

    /**
     * Saves the current configuration to file
     */
    public static void save() {
        try {
            fileConfiguration.save(configFile);
        } catch (IOException e) {
            logError("Failed to save player configuration", e);
        }
    }

    /**
     * Resets a player's configuration and world data
     * @param player The player to reset
     */
    public static void reset(Player player) {
        try {
            JavaPlugin plugin = Levelborder.getInstance();
            PlayerData playerData = loadPlayerData();
            deletePlayerFiles(plugin, player);

        } catch (Exception e) {
            logError("Error during player reset: " + player.getName(), e);
        }
    }

    /**
     * Loads player data from configuration
     * @return PlayerData object containing loaded configuration
     */
    private static PlayerData loadPlayerData() {
        return new PlayerData(
                fileConfiguration.getLocation(KEY_BORDER_CENTER),
                fileConfiguration.getLocation(KEY_NETHER_SPAWN),
                fileConfiguration.getLocation(KEY_END_SPAWN),
                fileConfiguration.getInt(KEY_MIN_BORDER_RADIUS),
                fileConfiguration.getInt(KEY_LEVEL)
        );
    }

    /**
     * Deletes player-related files
     * @param plugin JavaPlugin instance
     * @param player Player whose files to delete
     */
    private static void deletePlayerFiles(JavaPlugin plugin, Player player) {
        String playerUUID = player.getUniqueId().toString();
        String worldName = player.getWorld().getName();
        String containerPath = Bukkit.getServer().getWorldContainer().getPath();

        deleteFile(plugin.getDataFolder().getPath(), playerUUID + ".yml",
                player.getName() + " configuration deleted");
        deleteFile(containerPath, worldName + "/playerdata/" + playerUUID + ".dat",
                player.getName() + " player data deleted");
        deleteFile(containerPath, worldName + "/stats/" + playerUUID + ".json",
                player.getName() + " stats deleted");
    }

    /**
     * Deletes a specific file
     * @param parent Parent directory path
     * @param child Child file path
     * @param successMessage Message to log on successful deletion
     */
    private static void deleteFile(String parent, String child, String successMessage) {
        File file = new File(parent, child);
        if (file.exists() && file.delete()) {
            logInfo(successMessage);
        }
    }

    /**
     * Helper method for logging info messages
     * @param message Message to log
     */
    private static void logInfo(String message) {
        LOGGER.info(PLUGIN_PREFIX + message);
    }

    /**
     * Helper method for logging error messages
     * @param message Message to log
     * @param e Exception that occurred
     */
    private static void logError(String message, Exception e) {
        LOGGER.log(Level.SEVERE, PLUGIN_PREFIX + message, e);
    }

    /**
     * Data class to hold player configuration
     */
    private record PlayerData(Location worldOrigin, Location netherOrigin, Location endOrigin, int minBorderRadius,
                                  int level) {
    }
}