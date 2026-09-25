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
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class UpgraderGUI implements InventoryHolder {

    private static final int SIZE = 45;
    private static final int INPUT_SLOT = 4;
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
        catalog = new ArrayList<>();
        for (Material m : Material.values()) {
            if (m.isItem() && !m.isAir() && !m.isLegacy() && ItemValueService.isValuable(m)) {
                catalog.add(m);
            }
        }
        catalog.sort((a, b) -> Double.compare(service.getBaseValue(a), service.getBaseValue(b)));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        viewer.openInventory(inventory);
    }

    private ItemStack pane(org.bukkit.Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private void render() {
        ItemStack border = pane(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) inventory.setItem(i, border);

        // input slot stays whatever the player put there
        ItemStack current = inventory.getItem(INPUT_SLOT);
        if (current == null || current.getType() == border.getType()) {
            inventory.setItem(INPUT_SLOT, null);
        }

        renderInfo();
        renderCatalogPage();
        renderControls();
    }

    private ItemStack getInput() {
        ItemStack item = inventory.getItem(INPUT_SLOT);
        if (item == null || item.getType().isAir()) return null;
        return item;
    }

    private void renderInfo() {
        ItemStack input = getInput();
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta meta = info.getItemMeta();
        List<String> lore = new ArrayList<>();
        if (input == null) {
            meta.setDisplayName(ChatColor.YELLOW + "Drop an item in the top slot");
            lore.add(ChatColor.GRAY + "Then pick what you want below.");
        } else {
            double inputValue = service.getValue(input);
            meta.setDisplayName(ChatColor.YELLOW + "Input value: " + ChatColor.WHITE + fmt(inputValue));
            if (selectedTarget != null) {
                double targetValue = service.getBaseValue(selectedTarget);
                double chance = chanceFor(inputValue, targetValue);
                lore.add(ChatColor.GRAY + "Target: " + ChatColor.AQUA + prettyName(selectedTarget));
                lore.add(ChatColor.GRAY + "Target value: " + ChatColor.WHITE + fmt(targetValue));
                lore.add(ChatColor.GRAY + "Success chance: " + ChatColor.GREEN + String.format("%.1f%%", chance * 100));
                lore.add("");
                lore.add(ChatColor.RED + "Fail = item is lost.");
            } else {
                lore.add(ChatColor.GRAY + "Pick a target item below.");
            }
        }
        meta.setLore(lore);
        info.setItemMeta(meta);
        inventory.setItem(INFO_SLOT, info);
    }

    private void renderCatalogPage() {
        int totalPages = Math.max(1, (int) Math.ceil(catalog.size() / (double) CATALOG_SLOTS));
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
        int totalPages = Math.max(1, (int) Math.ceil(catalog.size() / (double) CATALOG_SLOTS));
        inventory.setItem(PREV_SLOT, pane(Material.ARROW, ChatColor.YELLOW + "Previous page (" + (page + 1) + "/" + totalPages + ")"));
        inventory.setItem(NEXT_SLOT, pane(Material.ARROW, ChatColor.YELLOW + "Next page (" + (page + 1) + "/" + totalPages + ")"));

        ItemStack input = getInput();
        boolean ready = input != null && selectedTarget != null && !spinning;
        ItemStack spin = pane(ready ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                spinning ? ChatColor.GOLD + "Spinning..." : (ready ? ChatColor.GREEN + "SPIN" : ChatColor.RED + "Need item + target"));
        inventory.setItem(SPIN_SLOT, spin);
    }

    /** Called by the listener for every click in this GUI. Returns true if the click should be cancelled. */
    public boolean handleClick(int slot, boolean isPlayerInventory) {
        if (isPlayerInventory) return false; // let the player manage their own inventory freely
        if (spinning) return true; // lock the GUI while spinning

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

    /** Called whenever the input slot's contents change, to refresh the info panel + odds. */
    public void onInputChanged() {
        Bukkit.getScheduler().runTask(plugin, this::render);
    }

    private void trySpin() {
        ItemStack input = getInput();
        if (input == null || selectedTarget == null || spinning) return;

        double inputValue = service.getValue(input);
        double targetValue = service.getBaseValue(selectedTarget);
        double chance = chanceFor(inputValue, targetValue);
        boolean success = RANDOM.nextDouble() < chance;

        spinning = true;
        render();
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 1.4f);

        List<Material> reel = catalog.isEmpty() ? List.of(selectedTarget) : catalog;
        new BukkitRunnable() {
            int ticks = 0;
            final int totalTicks = 30;

            @Override
            public void run() {
                if (ticks >= totalTicks) {
                    finishSpin(success);
                    cancel();
                    return;
                }
                Material flicker = reel.get(RANDOM.nextInt(reel.size()));
                ItemStack flickerItem = new ItemStack(flicker);
                inventory.setItem(INPUT_SLOT, flickerItem);
                viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.0f + (ticks / 30f));
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void finishSpin(boolean success) {
        spinning = false;
        if (success) {
            inventory.setItem(INPUT_SLOT, new ItemStack(selectedTarget));
            viewer.sendMessage(ChatColor.GREEN + "Success! Your item became " + prettyName(selectedTarget) + ".");
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        } else {
            inventory.setItem(INPUT_SLOT, null);
            viewer.sendMessage(ChatColor.RED + "It didn't land in the gold zone. Item lost.");
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);
        }
        selectedTarget = null;
        render();
    }

    /** Give the input item back to the player when they close the GUI (unless mid-spin). */
    public void returnInputOnClose() {
        if (spinning) return; // outcome already committed server-side before animation starts
        ItemStack input = getInput();
        if (input != null) {
            inventory.setItem(INPUT_SLOT, null);
            var leftover = viewer.getInventory().addItem(input);
            leftover.values().forEach(item -> viewer.getWorld().dropItem(viewer.getLocation(), item));
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
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }
}
