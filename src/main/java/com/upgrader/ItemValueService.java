package com.upgrader;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Prices every item. Order of priority:
 *   1. config.yml "values" overrides
 *   2. the built-in table below (raw materials + things that can't be crafted)
 *   3. name patterns (smithing templates, music discs, ores, ...)
 *   4. derived from the cheapest crafting / smelting / smithing recipe
 *   5. a generic guess
 */
public class ItemValueService {

    private static final Map<Material, Double> BASE_VALUES = new HashMap<>();

    private static void put(double value, String... names) {
        for (String name : names) {
            Material m = Material.matchMaterial(name);
            if (m != null) BASE_VALUES.put(m, value);
        }
    }

    static {
        // ---- cheap natural blocks / drops ----
        put(0.1, "DIRT", "SNOWBALL", "WHEAT_SEEDS", "BEETROOT_SEEDS", "MELON_SEEDS", "PUMPKIN_SEEDS");
        put(0.2, "SAND", "GRAVEL", "NETHERRACK", "MUD", "STICK", "BAMBOO", "KELP");
        put(0.3, "RED_SAND", "GRASS_BLOCK", "STRING", "FEATHER", "FLINT", "WHEAT", "EGG", "ROTTEN_FLESH",
                "SUGAR_CANE", "MELON_SLICE", "SWEET_BERRIES", "VINE");
        put(0.5, "COBBLESTONE", "STONE", "END_STONE", "SOUL_SAND", "SOUL_SOIL", "BLACKSTONE", "BASALT",
                "TUFF", "DRIPSTONE_BLOCK", "PODZOL", "CLAY_BALL", "CARROT", "POTATO", "BEETROOT",
                "COCOA_BEANS", "GLOW_BERRIES", "BROWN_MUSHROOM", "RED_MUSHROOM", "CACTUS", "LILY_PAD",
                "BONE", "RABBIT_HIDE");
        put(0.6, "COBBLED_DEEPSLATE");
        put(0.7, "DEEPSLATE", "COD", "SALMON");
        put(1.0, "OAK_LOG", "BIRCH_LOG", "SPRUCE_LOG", "JUNGLE_LOG", "ACACIA_LOG", "DARK_OAK_LOG",
                "MANGROVE_LOG", "CHERRY_LOG", "PALE_OAK_LOG", "LEATHER", "SLIME_BALL", "HONEYCOMB",
                "SPIDER_EYE", "BEEF", "PORKCHOP", "CHICKEN", "MUTTON", "RABBIT", "INK_SAC", "PUMPKIN",
                "ICE", "CALCITE", "SEA_PICKLE", "SCULK", "RESIN_CLUMP");
        put(1.5, "RAW_COPPER", "REDSTONE", "GLOWSTONE_DUST", "NETHER_WART", "PRISMARINE_SHARD");
        put(2.0, "COAL", "LAPIS_LAZULI", "COPPER_INGOT", "APPLE", "GUNPOWDER", "PRISMARINE_CRYSTALS",
                "TROPICAL_FISH", "MYCELIUM");
        put(3.0, "RAW_IRON", "QUARTZ", "PUFFERFISH", "CHORUS_FRUIT", "GLOW_INK_SAC", "HONEY_BOTTLE",
                "WIND_CHARGE");

        // ---- metals and gems ----
        put(4.0, "IRON_INGOT", "AMETHYST_SHARD");
        put(5.0, "RAW_GOLD");
        put(6.0, "GOLD_INGOT", "BLAZE_ROD", "PHANTOM_MEMBRANE");
        put(8.0, "ENDER_PEARL", "OBSIDIAN", "BREEZE_ROD", "RABBIT_FOOT", "ARMADILLO_SCUTE", "TURTLE_SCUTE",
                "SCUTE", "TORCHFLOWER_SEEDS", "PITCHER_POD", "DISC_FRAGMENT_5");
        put(10.0, "GHAST_TEAR");
        put(15.0, "EXPERIENCE_BOTTLE", "TRIAL_KEY", "SCULK_SENSOR");
        put(20.0, "CRYING_OBSIDIAN");
        put(24.0, "EMERALD");
        put(25.0, "NAME_TAG");
        put(30.0, "OMINOUS_BOTTLE");
        put(32.0, "DIAMOND");
        put(40.0, "SADDLE", "GOAT_HORN", "SHULKER_SHELL", "NAUTILUS_SHELL", "SPONGE", "WET_SPONGE",
                "OMINOUS_TRIAL_KEY");
        put(50.0, "ECHO_SHARD");
        put(60.0, "SKELETON_SKULL", "ZOMBIE_HEAD", "CREEPER_HEAD", "PIGLIN_HEAD", "SCULK_SHRIEKER",
                "SCULK_CATALYST", "WITHER_ROSE", "CREAKING_HEART");
        put(80.0, "SNIFFER_EGG");
        put(96.0, "ANCIENT_DEBRIS");
        put(100.0, "NETHERITE_SCRAP");

        // ---- rare / endgame things that cannot be crafted ----
        put(150.0, "HEAVY_CORE", "WITHER_SKELETON_SKULL", "HEART_OF_THE_SEA");
        put(216.0, "NETHERITE_INGOT");
        put(250.0, "TOTEM_OF_UNDYING");
        put(350.0, "MACE");
        put(400.0, "TRIDENT", "ENCHANTED_GOLDEN_APPLE");
        put(500.0, "NETHER_STAR");
        put(600.0, "DRAGON_HEAD");
        put(800.0, "ELYTRA");
        put(2000.0, "DRAGON_EGG");
    }

    private static final Set<String> RARE_DISCS = new HashSet<>(Arrays.asList(
            "MUSIC_DISC_PIGSTEP", "MUSIC_DISC_OTHERSIDE", "MUSIC_DISC_RELIC", "MUSIC_DISC_CREATOR",
            "MUSIC_DISC_CREATOR_MUSIC_BOX", "MUSIC_DISC_PRECIPICE", "MUSIC_DISC_TEARS",
            "MUSIC_DISC_LAVA_CHICKEN"));

    /** Items nobody should be able to put in or pick as a target (admin / unobtainable). */
    private static final Set<String> BLOCKED_NAMES = new HashSet<>(Arrays.asList(
            "SPAWNER", "TRIAL_SPAWNER", "VAULT",
            "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK", "COMMAND_BLOCK_MINECART",
            "BARRIER", "LIGHT", "STRUCTURE_BLOCK", "STRUCTURE_VOID", "JIGSAW", "TEST_BLOCK",
            "TEST_INSTANCE_BLOCK", "DEBUG_STICK", "KNOWLEDGE_BOOK", "BEDROCK", "END_PORTAL_FRAME",
            "REINFORCED_DEEPSLATE", "PETRIFIED_OAK_SLAB", "BUDDING_AMETHYST", "CHORUS_PLANT",
            "SUSPICIOUS_SAND", "SUSPICIOUS_GRAVEL"));

    private final Map<Material, Double> overrides = new HashMap<>();
    private final Set<String> extraBlocked = new HashSet<>();
    private final Map<Material, Double> cache = new HashMap<>();
    private final Set<Material> resolving = new HashSet<>();

    /** Loads "values" and "blocked" from config.yml. Returns a list of problems to log. */
    public List<String> loadConfig(FileConfiguration config) {
        List<String> problems = new ArrayList<>();
        overrides.clear();
        extraBlocked.clear();
        cache.clear();

        ConfigurationSection values = config.getConfigurationSection("values");
        if (values != null) {
            for (String key : values.getKeys(false)) {
                Material m = Material.matchMaterial(key);
                double v = values.getDouble(key, -1.0);
                if (m == null) {
                    problems.add("config.yml values: unknown item '" + key + "' - ignored");
                } else if (v <= 0) {
                    problems.add("config.yml values: '" + key + "' needs a number above 0 - ignored");
                } else {
                    overrides.put(m, v);
                }
            }
        }
        for (String name : config.getStringList("blocked")) {
            String upper = name.trim().toUpperCase(Locale.ROOT);
            if (Material.matchMaterial(upper) == null) {
                problems.add("config.yml blocked: unknown item '" + name + "' - ignored");
            } else {
                extraBlocked.add(upper);
            }
        }
        return problems;
    }

    /** True if this material can never be used as an input or a target. */
    public boolean isBlocked(Material m) {
        if (m == null || m.isLegacy() || m.isAir() || !m.isItem()) return true;
        String n = m.name();
        return BLOCKED_NAMES.contains(n) || n.startsWith("INFESTED_") || extraBlocked.contains(n);
    }

    /** True if this material may appear in the target catalog. */
    public boolean isValidTarget(Material m) {
        if (isBlocked(m)) return false;
        String n = m.name();
        // items whose real identity lives in NBT/components, so the base item is meaningless
        return !(n.contains("POTION") || n.equals("ENCHANTED_BOOK") || n.contains("SPAWN_EGG")
                || n.equals("WRITTEN_BOOK") || n.equals("TIPPED_ARROW") || n.equals("PLAYER_HEAD")
                || n.equals("FIREWORK_ROCKET") || n.equals("SUSPICIOUS_STEW"));
    }

    /** Value of one plain item of this material. */
    public double getBaseValue(Material material) {
        Double cached = cache.get(material);
        if (cached != null) return cached;

        Double fixed = overrides.get(material);
        if (fixed == null) fixed = BASE_VALUES.get(material);
        if (fixed == null) fixed = patternValue(material);
        if (fixed != null) {
            cache.put(material, fixed);
            return fixed;
        }

        if (resolving.contains(material)) {
            // recipe cycle - guess instead of recursing forever
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

    /** Value of ONE item from this stack: durability and enchantments included. */
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

        for (int level : stack.getEnchantments().values()) {
            value += level * 4.0;
        }
        return value;
    }

    /** Value of the whole stack (per-item value x amount). */
    public double getTotalValue(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return 0.0;
        return getValue(stack) * stack.getAmount();
    }

    private Double patternValue(Material m) {
        String n = m.name();
        if (n.equals("NETHERITE_UPGRADE_SMITHING_TEMPLATE")) return 150.0;
        if (n.endsWith("_SMITHING_TEMPLATE")) return 60.0;
        if (n.endsWith("_POTTERY_SHERD")) return 20.0;
        if (n.endsWith("_BANNER_PATTERN")) return 20.0;
        if (n.startsWith("MUSIC_DISC_")) return RARE_DISCS.contains(n) ? 100.0 : 60.0;
        if (n.endsWith("_SPAWN_EGG")) return 50.0;

        if (n.endsWith("_HORSE_ARMOR")) {
            if (n.startsWith("NETHERITE")) return 250.0;
            if (n.startsWith("DIAMOND")) return 60.0;
            if (n.startsWith("GOLDEN")) return 30.0;
            if (n.startsWith("IRON")) return 20.0;
            return 8.0;
        }

        if (n.endsWith("_BUCKET")) { // plain BUCKET doesn't match (no underscore) and is craftable
            if (n.equals("WATER_BUCKET")) return 13.0;
            if (n.equals("MILK_BUCKET")) return 14.0;
            if (n.equals("POWDER_SNOW_BUCKET")) return 15.0;
            if (n.equals("LAVA_BUCKET")) return 16.0;
            return 20.0; // fish / axolotl / tadpole buckets
        }

        if (n.startsWith("STRIPPED_")) return 1.0; // stripped logs have no recipe
        if (n.endsWith("_LOG")) return 1.0;
        if (n.endsWith("_STEM")) return 1.5;       // crimson / warped

        if (n.endsWith("_ORE")) return oreValue(n);
        return null;
    }

    private double oreValue(String n) {
        if (n.contains("DIAMOND")) return 40.0;
        if (n.contains("EMERALD")) return 30.0;
        if (n.contains("LAPIS")) return 6.0;
        if (n.contains("REDSTONE")) return 6.0;
        if (n.contains("GOLD")) return 8.0;
        if (n.contains("IRON")) return 6.0;
        if (n.contains("COPPER")) return 3.0;
        if (n.contains("COAL")) return 3.0;
        return 5.0;
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
