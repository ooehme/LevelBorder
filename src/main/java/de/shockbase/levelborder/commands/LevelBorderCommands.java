package de.shockbase.levelborder.commands;

import de.shockbase.levelborder.config.PlayerConfig;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class LevelBorderCommands implements TabExecutor {
    private static final String RESET_COMMAND = "reset";
    private static final String RESET_MESSAGE = "Reset done. Please reconnect.";

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            return false;
        }

        if (args.length < 1) {
            return false;
        }

        return handlePlayerCommand((Player) sender, args[0]);
    }

    private boolean handlePlayerCommand(Player player, String command) {
        if (!command.equalsIgnoreCase(RESET_COMMAND)) {
            return false;
        }

        resetPlayer(player);
        return true;
    }

    private void resetPlayer(Player player) {
        player.kick(Component.text(RESET_MESSAGE));
        PlayerConfig.reset(player);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                @NotNull String[] args) {
        if (args.length == 1) {
            return Collections.singletonList(RESET_COMMAND);
        }

        return Collections.emptyList();
    }
}