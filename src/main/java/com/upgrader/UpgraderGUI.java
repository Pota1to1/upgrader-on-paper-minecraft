package com.upgrader;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class UpgraderGUI implements InventoryHolder {

    private static final int SIZE = 45;
    public static final int INPUT_SLOT = 4;
    private static final int INFO_SLOT = 13;
    private static final int PREV_SLOT = 36;
    private static final int SPIN_SLOT = 40;
    private static final int NEXT_SLOT = 44;
    private static final int CATALOG_START = 18; // 2 rows of 9 = 18 slots (18-35)
    private static final int CATALOG_SLOTS = 18;

    private static List<Material> catalog;
    private static final Random RANDOM = new Random();

    private final UpgraderPlugin plugin;
    private final ItemValueService service;
    private final Player viewer;
    private final Inventory inventory;

    private Material selectedTarget = null;
    private int page = 0;
    private boolean spinning = false;
    private boolean closed = false;

    public UpgraderGUI(UpgraderPlugin plugin, Player viewer) {
        this.plugin = plugin;
        this.service = plugin.getValueService();
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, SIZE, ChatColor.GOLD + "Upgrader");
        ensureCatalog(service);
        render();
    }

    private static synchronized void ensureCatalog(ItemValueService service) {
        if (catalog != null) return;
        List<Material> list = new ArrayList<>();
        for (Material m : Material.values()) {
            if (service.isValidTarget(m)) list.add(m);
        }
        list.sort((a, b) -> Double.compare(service.getBaseValue(a), service.getBaseValue(b)));
        catalog = list;
    }

    /** Forces the catalog to be rebuilt (used after a config reload). */
    public static synchronized void resetCatalog() {
        catalog = null;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        viewer.openInventory(inventory);
    }

    private ItemStack pane(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private void render() {
        // capture whatever the player currently has in the input slot BEFORE
        // redrawing the border, or we'd overwrite it and lose it
        ItemStack existingInput = inventory.getItem(INPUT_SLOT);
        if (existingInput != null && existingInput.getType().isAir()) existingInput = null;

        ItemStack border = pane(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) {
            if (i == INPUT_SLOT) continue;
            inventory.setItem(i, border);
        }
        inventory.setItem(INPUT_SLOT, existingInput);

        renderInfo();
        renderCatalogPage();
        renderControls();
    }

    private ItemStack getInput() {
        ItemStack item = inventory.getItem(INPUT_SLOT);
        if (item == null || item.getType().isAir()) return null;
        return item;
    }

    /** Can this stack be dropped into the input slot? */
    public boolean isAllowedInput(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return true;
        return !service.isBlocked(stack.getType()) && !plugin.isUpgraderItem(stack);
    }

    private void renderInfo() {
        ItemStack input = getInput();
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta meta = info.getItemMeta();
        List<String> lore = new ArrayList<>();
        if (input == null) {
            meta.setDisplayName(ChatColor.YELLOW + "Drop an item (or a stack) in the top slot");
            lore.add(ChatColor.GRAY + "Then pick what you want below.");
        } else {
            double total = service.getTotalValue(input);
            meta.setDisplayName(ChatColor.YELLOW + "Input value: " + ChatColor.WHITE + fmt(total));
            if (input.getAmount() > 1) {
                lore.add(ChatColor.GRAY + "" + input.getAmount() + " x " + fmt(service.getValue(input)) + " each");
            }
            if (selectedTarget != null) {
                double targetValue = service.getBaseValue(selectedTarget);
                double chance = chanceFor(total, targetValue);
                lore.add(ChatColor.GRAY + "Target: " + ChatColor.AQUA + prettyName(selectedTarget));
                lore.add(ChatColor.GRAY + "Target value: " + ChatColor.WHITE + fmt(targetValue));
                lore.add(ChatColor.GRAY + "Success chance: " + ChatColor.GREEN + String.format("%.1f%%", chance * 100));
                lore.add("");
                lore.add(ChatColor.RED + (input.getAmount() > 1 ? "Fail = the whole stack is lost." : "Fail = item is lost."));
            } else {
                lore.add(ChatColor.GRAY + "Pick a target item below.");
            }
        }
        meta.setLore(lore);
        info.setItemMeta(meta);
        inventory.setItem(INFO_SLOT, info);
    }

    private int totalPages() {
        return Math.max(1, (int) Math.ceil(catalog.size() / (double) CATALOG_SLOTS));
    }

    private void renderCatalogPage() {
        int totalPages = totalPages();
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        int start = page * CATALOG_SLOTS;
        for (int i = 0; i < CATALOG_SLOTS; i++) {
            int slot = CATALOG_START + i;
            int idx = start + i;
            if (idx >= catalog.size()) {
                inventory.setItem(slot, pane(Material.GRAY_STAINED_GLASS_PANE, " "));
                continue;
            }
            Material mat = catalog.get(idx);
            ItemStack display = new ItemStack(mat);
            ItemMeta meta = display.getItemMeta();
            double value = service.getBaseValue(mat);
            boolean selected = mat == selectedTarget;
            meta.setDisplayName((selected ? ChatColor.GREEN + "\u2713 " : ChatColor.AQUA + "") + prettyName(mat));
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Value: " + ChatColor.WHITE + fmt(value));
            lore.add(ChatColor.YELLOW + "Click to select as target");
            meta.setLore(lore);
            display.setItemMeta(meta);
            inventory.setItem(slot, display);
        }
    }

    private void renderControls() {
        int totalPages = totalPages();
        String pageText = ChatColor.GRAY + "Page " + (page + 1) + " of " + totalPages;

        ItemStack prev = pane(Material.ARROW, ChatColor.YELLOW + "Previous page");
        ItemMeta prevMeta = prev.getItemMeta();
        prevMeta.setLore(List.of(pageText));
        prev.setItemMeta(prevMeta);
        inventory.setItem(PREV_SLOT, prev);

        ItemStack next = pane(Material.ARROW, ChatColor.YELLOW + "Next page");
        ItemMeta nextMeta = next.getItemMeta();
        nextMeta.setLore(List.of(pageText));
        next.setItemMeta(nextMeta);
        inventory.setItem(NEXT_SLOT, next);

        ItemStack input = getInput();
        boolean ready = input != null && selectedTarget != null && !spinning;
        ItemStack spin = pane(ready ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                spinning ? ChatColor.GOLD + "Spinning..." : (ready ? ChatColor.GREEN + "SPIN" : ChatColor.RED + "Need item + target"));
        inventory.setItem(SPIN_SLOT, spin);
    }

    /** Called by the listener for every click in this GUI. Returns true if the click should be cancelled. */
    public boolean handleClick(int slot, boolean isPlayerInventory) {
        if (isPlayerInventory) return false; // let the player manage their own inventory freely
        if (spinning) return true;

        if (slot == INPUT_SLOT) return false; // allow placing/removing the input item

        if (slot == PREV_SLOT) {
            page--;
            render();
            return true;
        }
        if (slot == NEXT_SLOT) {
            page++;
            render();
            return true;
        }
        if (slot == SPIN_SLOT) {
            trySpin();
            return true;
        }
        if (slot >= CATALOG_START && slot < CATALOG_START + CATALOG_SLOTS) {
            int idx = page * CATALOG_SLOTS + (slot - CATALOG_START);
            if (idx < catalog.size()) {
                selectedTarget = catalog.get(idx);
                render();
            }
            return true;
        }
        return true; // border / info slots are not interactive
    }

    /** Called whenever the input slot may have changed: re-checks it and refreshes the odds. */
    public void onInputChanged() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (spinning || closed) return;
            validateInput();
            render();
        });
    }

    /** Safety net: if something not allowed ended up in the input slot, hand it back. */
    private void validateInput() {
        ItemStack input = getInput();
        if (input != null && !isAllowedInput(input)) {
            inventory.setItem(INPUT_SLOT, null);
            giveToViewer(input);
            viewer.sendMessage(ChatColor.RED + "That item can't be used in the Upgrader.");
        }
    }

    private void trySpin() {
        ItemStack input = getInput();
        if (input == null || selectedTarget == null || spinning) return;

        double inputValue = service.getTotalValue(input);
        double targetValue = service.getBaseValue(selectedTarget);
        double chance = chanceFor(inputValue, targetValue);
        boolean success = RANDOM.nextDouble() < chance;
        final Material target = selectedTarget;

        spinning = true;
        render();
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 1.4f);

        List<Material> reel = catalog.isEmpty() ? List.of(target) : catalog;
        new BukkitRunnable() {
            int ticks = 0;
            final int totalTicks = 30;

            @Override
            public void run() {
                if (ticks >= totalTicks) {
                    finishSpin(success, target);
                    cancel();
                    return;
                }
                Material flicker = reel.get(RANDOM.nextInt(reel.size()));
                inventory.setItem(INPUT_SLOT, new ItemStack(flicker));
                viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.0f + (ticks / 30f));
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void finishSpin(boolean success, Material target) {
        spinning = false;

        if (closed) {
            // the player closed the menu mid-spin: the roll was already made, so deliver it directly
            if (viewer.isOnline()) {
                if (success) {
                    giveToViewer(new ItemStack(target));
                    viewer.sendMessage(ChatColor.GREEN + "Success! Your item became " + prettyName(target) + ".");
                } else {
                    viewer.sendMessage(ChatColor.RED + "It didn't land in the gold zone. Item lost.");
                }
            }
            return;
        }

        if (success) {
            inventory.setItem(INPUT_SLOT, new ItemStack(target));
            viewer.sendMessage(ChatColor.GREEN + "Success! Your item became " + prettyName(target) + ".");
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        } else {
            inventory.setItem(INPUT_SLOT, null);
            viewer.sendMessage(ChatColor.RED + "It didn't land in the gold zone. Item lost.");
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);
        }
        selectedTarget = null;
        render();
    }

    private void giveToViewer(ItemStack item) {
        var leftover = viewer.getInventory().addItem(item);
        leftover.values().forEach(rest -> viewer.getWorld().dropItem(viewer.getLocation(), rest));
    }

    /** Give the input item back to the player when they close the GUI (unless mid-spin). */
    public void returnInputOnClose() {
        closed = true;
        if (spinning) return; // the result is delivered when the spin finishes
        ItemStack input = getInput();
        if (input != null) {
            inventory.setItem(INPUT_SLOT, null);
            giveToViewer(input);
        }
    }

    public boolean isSpinning() {
        return spinning;
    }

    static double chanceFor(double inputValue, double targetValue) {
        if (targetValue <= 0) targetValue = 0.01;
        double chance = 0.9 * (inputValue / targetValue);
        return Math.max(0.001, Math.min(0.9, chance));
    }

    private static String fmt(double v) {
        return String.format("%.1f", v);
    }

    private static String prettyName(Material m) {
        String[] parts = m.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }
}
