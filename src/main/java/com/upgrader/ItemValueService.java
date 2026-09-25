package com.upgrader;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Prices every item by walking its crafting/smelting/smithing/stonecutting
 * recipes back down to a small table of raw-material base values -
 * mirroring how the original "Upgrader" mod derives value automatically
 * instead of hand-tuning a number per item.
 */
public class ItemValueService {

    // Seed values for raw materials that have no recipe of their own.
    // Everything else is derived from these by walking recipes.
    private static final Map<Material, Double> BASE_VALUES = new HashMap<>();
    static {
        put("COBBLESTONE", 0.5);
        put("STONE", 0.5);
        put("DIRT", 0.1);
        put("SAND", 0.2);
        put("GRAVEL", 0.2);
        put("OAK_LOG", 1.0);
        put("BIRCH_LOG", 1.0);
        put("SPRUCE_LOG", 1.0);
        put("JUNGLE_LOG", 1.0);
        put("ACACIA_LOG", 1.0);
        put("DARK_OAK_LOG", 1.0);
        put("MANGROVE_LOG", 1.0);
        put("CHERRY_LOG", 1.0);
        put("COAL", 2.0);
        put("RAW_IRON", 3.0);
        put("RAW_GOLD", 5.0);
        put("RAW_COPPER", 1.5);
        put("IRON_INGOT", 4.0);
        put("GOLD_INGOT", 6.0);
        put("COPPER_INGOT", 2.0);
        put("REDSTONE", 1.5);
        put("GLOWSTONE_DUST", 1.5);
        put("LAPIS_LAZULI", 2.0);
        put("QUARTZ", 3.0);
        put("DIAMOND", 32.0);
        put("EMERALD", 24.0);
        put("AMETHYST_SHARD", 4.0);
        put("ANCIENT_DEBRIS", 96.0);
        put("NETHERITE_SCRAP", 100.0);
        put("NETHERITE_INGOT", 216.0); // 4 scrap + 4 gold, plus rarity premium
        put("NETHER_STAR", 500.0);
        put("DRAGON_EGG", 2000.0);
        put("ELYTRA", 800.0);
        put("TRIDENT", 400.0);
        put("STICK", 0.2);
        put("STRING", 0.3);
        put("FEATHER", 0.3);
        put("LEATHER", 1.0);
        put("FLINT", 0.3);
        put("BONE", 0.5);
        put("SLIME_BALL", 1.0);
        put("ENDER_PEARL", 8.0);
        put("BLAZE_ROD", 6.0);
        put("GHAST_TEAR", 10.0);
        put("PRISMARINE_SHARD", 1.5);
        put("PRISMARINE_CRYSTALS", 2.0);
        put("HONEYCOMB", 1.0);
        put("WHEAT", 0.3);
    }

    private static void put(String materialName, double value) {
        Material m = Material.matchMaterial(materialName);
        if (m != null) BASE_VALUES.put(m, value);
    }

    // Materials whose value lives entirely in NBT - excluded as upgrade
    // targets, same as the original mod does for potions/enchanted books/etc.
    public static boolean isValuable(Material m) {
        if (m == null || m.isAir() || !m.isItem()) return false;
        String n = m.name();
        return !(n.contains("POTION") || n.equals("ENCHANTED_BOOK")
                || n.contains("SPAWN_EGG") || n.equals("WRITTEN_BOOK")
                || n.equals("TIPPED_ARROW") || n.equals("PLAYER_HEAD")
                || n.equals("FIREWORK_ROCKET") || n.equals("SUSPICIOUS_STEW"));
    }

    private final Map<Material, Double> cache = new HashMap<>();
    private final Set<Material> resolving = new HashSet<>();

    /** Base value of a material with a full stack of durability, ignoring NBT. */
    public double getBaseValue(Material material) {
        Double cached = cache.get(material);
        if (cached != null) return cached;

        if (BASE_VALUES.containsKey(material)) {
            double v = BASE_VALUES.get(material);
            cache.put(material, v);
            return v;
        }

        if (resolving.contains(material)) {
            // Recipe cycle guard - fall back to a rarity guess rather than recurse forever.
            return rarityGuess(material);
        }
        resolving.add(material);

        double best = Double.MAX_VALUE;
        List<Recipe> recipes = Bukkit.getRecipesFor(new ItemStack(material));
        for (Recipe recipe : recipes) {
            Double cost = costOfRecipe(recipe);
            if (cost != null && cost < best) best = cost;
        }

        resolving.remove(material);

        double result = (best == Double.MAX_VALUE) ? rarityGuess(material) : best;
        cache.put(material, result);
        return result;
    }

    /** Value of an actual item stack (single item), factoring in remaining durability. */
    public double getValue(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return 0.0;
        double value = getBaseValue(stack.getType());

        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof Damageable dmg && stack.getType().getMaxDurability() > 0) {
            int max = stack.getType().getMaxDurability();
            int damage = dmg.getDamage();
            double remaining = Math.max(0.0, (max - damage) / (double) max);
            value *= Math.max(0.05, remaining);
        }
        return value;
    }

    private Double costOfRecipe(Recipe recipe) {
        if (recipe instanceof ShapedRecipe sr) {
            Map<Character, RecipeChoice> choices = sr.getChoiceMap();
            double sum = 0;
            for (String row : sr.getShape()) {
                for (char c : row.toCharArray()) {
                    if (c == ' ') continue;
                    RecipeChoice choice = choices.get(c);
                    if (choice == null) continue;
                    Double v = minChoiceValue(choice);
                    if (v == null) return null;
                    sum += v;
                }
            }
            int amount = Math.max(1, sr.getResult().getAmount());
            return sum / amount;

        } else if (recipe instanceof ShapelessRecipe sr) {
            double sum = 0;
            for (RecipeChoice choice : sr.getChoiceList()) {
                Double v = minChoiceValue(choice);
                if (v == null) return null;
                sum += v;
            }
            int amount = Math.max(1, sr.getResult().getAmount());
            return sum / amount;

        } else if (recipe instanceof CookingRecipe<?> cr) {
            Double v = minChoiceValue(cr.getInputChoice());
            if (v == null) return null;
            int amount = Math.max(1, cr.getResult().getAmount());
            return (v + 0.2) / amount; // small fuel/time overhead

        } else if (recipe instanceof StonecuttingRecipe sc) {
            Double v = minChoiceValue(sc.getInputChoice());
            if (v == null) return null;
            int amount = Math.max(1, sc.getResult().getAmount());
            return v / amount;

        } else if (recipe instanceof SmithingTransformRecipe st) {
            Double base = minChoiceValue(st.getBase());
            Double addition = minChoiceValue(st.getAddition());
            Double template = minChoiceValue(st.getTemplate());
            if (base == null || addition == null || template == null) return null;
            return base + addition + template;
        }
        return null;
    }

    private Double minChoiceValue(RecipeChoice choice) {
        if (choice == null) return null;
        if (choice instanceof RecipeChoice.MaterialChoice mc) {
            double min = Double.MAX_VALUE;
            for (Material m : mc.getChoices()) {
                min = Math.min(min, getBaseValue(m));
            }
            return min == Double.MAX_VALUE ? null : min;
        } else if (choice instanceof RecipeChoice.ExactChoice ec) {
            double min = Double.MAX_VALUE;
            for (ItemStack stack : ec.getChoices()) {
                min = Math.min(min, getValue(stack));
            }
            return min == Double.MAX_VALUE ? null : min;
        }
        return null;
    }

    private double rarityGuess(Material material) {
        String n = material.name();
        if (n.contains("NETHERITE")) return 150.0;
        if (n.contains("DIAMOND")) return 30.0;
        if (n.contains("GOLD") || n.contains("GOLDEN")) return 8.0;
        if (n.contains("IRON")) return 5.0;
        if (n.contains("COPPER")) return 2.0;
        if (n.contains("STONE")) return 1.0;
        if (n.contains("WOOD") || n.contains("OAK") || n.contains("PLANK")) return 0.5;
        return 3.0; // generic fallback
    }

    public void clearCache() {
        cache.clear();
    }
}
