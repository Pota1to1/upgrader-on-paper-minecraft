package com.upgrader;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryAction;
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

        // nothing can be touched while the wheel is spinning
        if (gui.isSpinning()) {
            event.setCancelled(true);
            return;
        }

        // double-click "collect to cursor" could pull glass panes out of the menu
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }

        boolean clickedTop = event.getClickedInventory() != null
                && event.getClickedInventory().getHolder() instanceof UpgraderGUI;

        // refuse blocked items right away (cursor placement and shift-click);
        // anything that sneaks through other ways is caught by the GUI's own check
        ItemStack incoming = null;
        if (clickedTop && event.getSlot() == UpgraderGUI.INPUT_SLOT) {
            InventoryAction action = event.getAction();
            if (action == InventoryAction.PLACE_ALL || action == InventoryAction.PLACE_ONE
                    || action == InventoryAction.PLACE_SOME || action == InventoryAction.SWAP_WITH_CURSOR) {
                incoming = event.getCursor();
            }
        } else if (!clickedTop && event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            incoming = event.getCurrentItem();
        }
        if (incoming != null && !gui.isAllowedInput(incoming)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.sendMessage(ChatColor.RED + "That item can't be used in the Upgrader.");
            }
            return;
        }

        boolean cancel = gui.handleClick(event.getSlot(), !clickedTop);
        if (cancel) {
            event.setCancelled(true);
        } else {
            // input slot may have changed - re-check and refresh odds next tick
            gui.onInputChanged();
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof UpgraderGUI gui)) return;
        if (gui.isSpinning()) {
            event.setCancelled(true);
            return;
        }
        // block dragging across the border/catalog area; only the input slot may be dragged into
        for (int slot : event.getRawSlots()) {
            if (slot < event.getInventory().getSize() && slot != UpgraderGUI.INPUT_SLOT) {
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
