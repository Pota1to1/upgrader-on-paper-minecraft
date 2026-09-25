package com.upgrader;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class UpgraderListener implements Listener {

    private final UpgraderPlugin plugin;

    public UpgraderListener(UpgraderPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack hand = event.getItem();
        if (!plugin.isUpgraderItem(hand)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        UpgraderGUI gui = new UpgraderGUI(plugin, player);
        plugin.registerOpenGui(player, gui);
        gui.open();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof UpgraderGUI gui)) return;
        boolean clickedTop = event.getClickedInventory() != null
                && event.getClickedInventory().getHolder() instanceof UpgraderGUI;
        boolean cancel = gui.handleClick(event.getSlot(), !clickedTop);
        if (cancel) {
            event.setCancelled(true);
        } else {
            // input slot changed (item placed/removed) - refresh odds next tick
            gui.onInputChanged();
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof UpgraderGUI gui)) return;
        // block dragging across the border/catalog area; only allow single-slot input drops
        for (int slot : event.getRawSlots()) {
            if (slot < event.getInventory().getSize() && slot != 4) {
                event.setCancelled(true);
                return;
            }
        }
        gui.onInputChanged();
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof UpgraderGUI gui)) return;
        gui.returnInputOnClose();
        plugin.unregisterOpenGui((Player) event.getPlayer());
    }
}
