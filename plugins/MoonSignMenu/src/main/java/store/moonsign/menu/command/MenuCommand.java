package store.moonsign.menu.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import store.moonsign.menu.MoonSignMenuPlugin;

public final class MenuCommand implements CommandExecutor {
    private final MoonSignMenuPlugin plugin;

    public MenuCommand(MoonSignMenuPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command is player-only.");
            return true;
        }
        plugin.openMenu(player);
        return true;
    }
}
