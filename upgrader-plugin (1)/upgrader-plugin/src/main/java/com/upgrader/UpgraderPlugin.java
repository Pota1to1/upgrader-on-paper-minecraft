package com.upgrader;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class UpgraderPlugin extends JavaPlugin {

    private ItemValueService valueService;
    private NamespacedKey itemKey;
    private final Map<UUID, UpgraderGUI> openGuis = new HashMap<>();

    @Override
    public void onEnable() {
        this.valueService = new ItemValueService();
        this.itemKey = new NamespacedKey(this, "upgrader_item");

        getServer().getPluginManager().registerEvents(new UpgraderListener(this), this);
        registerRecipe();

        getLogger().info("Upgrader enabled - pricing is derived live from registered recipes.");
    }

    private void registerRecipe() {
        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(this, "craft_upgrader"), createUpgraderItem());
        recipe.shape("GDG", "DAD", "GDG");
        recipe.setIngredient('G', Material.GOLD_INGOT);
        recipe.setIngredient('D', Material.DIAMOND);
        recipe.setIngredient('A', Material.ANVIL);
        getServer().addRecipe(recipe);
    }

    public ItemStack createUpgraderItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Upgrader");
        meta.setLore(java.util.List.of(
                ChatColor.GRAY + "Right-click to gamble an item",
                ChatColor.GRAY + "for a better one.",
                ChatColor.DARK_GRAY + "Odds are based on item value."
        ));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isUpgraderItem(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return false;
        Byte tag = stack.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.BYTE);
        return tag != null && tag == (byte) 1;
    }

    public ItemValueService getValueService() {
        return valueService;
    }

    public void registerOpenGui(Player player, UpgraderGUI gui) {
        openGuis.put(player.getUniqueId(), gui);
    }

    public void unregisterOpenGui(Player player) {
        openGuis.remove(player.getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("upgrader")) return false;
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can run this command.");
            return true;
        }
        if (!player.hasPermission("upgrader.give")) {
            player.sendMessage(ChatColor.RED + "You don't have permission for that.");
            return true;
        }
        var leftover = player.getInventory().addItem(createUpgraderItem());
        leftover.values().forEach(item -> player.getWorld().dropItem(player.getLocation(), item));
        player.sendMessage(ChatColor.GREEN + "You received an Upgrader.");
        return true;
    }
}
