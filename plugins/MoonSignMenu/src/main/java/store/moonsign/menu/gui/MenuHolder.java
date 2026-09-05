package store.moonsign.menu.gui;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class MenuHolder implements InventoryHolder {
    public enum Type { MAIN, PLAYER_SELECT, TP_MODE }

    private final Type type;
    private final UUID targetId;
    private final Inventory inventory;

    public MenuHolder(Type type, UUID targetId, int size, String title) {
        this.type = type;
        this.targetId = targetId;
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    public Type type() {
        return type;
    }

    public UUID targetId() {
        return targetId;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
