package com.fruityspikes.whaleborne.server.data;

import com.fruityspikes.whaleborne.Whaleborne;
import com.fruityspikes.whaleborne.server.entities.components.hullback.SeatLayout;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads per-material hull configurations from datapack JSON files.
 * Directory: data/{namespace}/whaleborne/hull_config/{name}.json
 *
 * Follows the same pattern as HullbackDirtManager.
 */
public class HullConfigManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<Item, HullConfig> CONFIGS = new HashMap<>();
    private static final Map<Item, SeatLayout> SEAT_LAYOUTS = new HashMap<>();

    public HullConfigManager() {
        super(GSON, "whaleborne/hull_config");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager, ProfilerFiller profiler) {
        CONFIGS.clear();
        SEAT_LAYOUTS.clear();

        entries.forEach((id, json) -> {
            HullConfig config = HullConfig.CODEC.parse(JsonOps.INSTANCE, json).result()
                    .orElseGet(() -> {
                        Whaleborne.LOGGER.error("Failed to parse hull config {}", id);
                        return null;
                    });

            if (config != null) {
                Item item = BuiltInRegistries.ITEM.get(config.item());
                if (item != null) {
                    CONFIGS.put(item, config);

                    // Parse seats array if present
                    if (json.isJsonObject()) {
                        JsonObject obj = json.getAsJsonObject();
                        if (obj.has("seats")) {
                            SeatLayout layout = parseSeatLayout(obj.getAsJsonArray("seats"), id);
                            if (layout != null) {
                                SEAT_LAYOUTS.put(item, layout);
                            }
                        }
                    }
                } else {
                    Whaleborne.LOGGER.warn("Hull config {} references unknown item: {}", id, config.item());
                }
            }
        });

        Whaleborne.LOGGER.info("Loaded {} hull configs ({} with custom seats)", CONFIGS.size(), SEAT_LAYOUTS.size());
    }

    /**
     * Parse a "seats" JSON array into a SeatLayout.
     * Format: [{"offset": [x, y, z], "part_pos": "body", "part_rot": "body"}, ...]
     * Optional "fluke_seat" field at array level defaults to last seat with part_pos "fluke".
     */
    private static SeatLayout parseSeatLayout(JsonArray seatsArray, ResourceLocation configId) {
        try {
            List<SeatLayout.SeatDef> defs = new ArrayList<>();
            int flukeSeatIndex = -1;

            for (int i = 0; i < seatsArray.size() && i < SeatLayout.MAX_SEATS; i++) {
                JsonObject seatObj = seatsArray.get(i).getAsJsonObject();
                JsonArray offset = seatObj.getAsJsonArray("offset");

                String posPartName = seatObj.has("part_pos") ? seatObj.get("part_pos").getAsString() : "body";
                String rotPartName = seatObj.has("part_rot") ? seatObj.get("part_rot").getAsString() : posPartName;

                int posPartIndex = partNameToIndex(posPartName);
                int rotPartIndex = partNameToIndex(rotPartName);

                if (posPartIndex < 0) {
                    Whaleborne.LOGGER.warn("Hull config {} seat {}: unknown part_pos '{}', skipping", configId, i, posPartName);
                    continue;
                }
                if (rotPartIndex < 0) rotPartIndex = posPartIndex;

                defs.add(new SeatLayout.SeatDef(
                        new Vec3(offset.get(0).getAsDouble(), offset.get(1).getAsDouble(), offset.get(2).getAsDouble()),
                        posPartIndex, rotPartIndex
                ));

                // Track last fluke seat for smoothing
                if (posPartIndex == 4) flukeSeatIndex = defs.size() - 1;
            }

            return defs.isEmpty() ? null : new SeatLayout(defs.toArray(new SeatLayout.SeatDef[0]), flukeSeatIndex);
        } catch (Exception e) {
            Whaleborne.LOGGER.warn("Failed to parse seats in hull config {}: {}", configId, e.getMessage());
            return null;
        }
    }

    private static int partNameToIndex(String name) {
        return switch (name.toLowerCase()) {
            case "nose" -> 0;
            case "head" -> 1;
            case "body" -> 2;
            case "tail" -> 3;
            case "fluke" -> 4;
            default -> -1;
        };
    }

    /**
     * Get the hull config for an item. Returns a dynamically generated default if no datapack JSON exists.
     */
    public static HullConfig getConfig(Item item) {
        HullConfig config = CONFIGS.get(item);
        if (config != null) return config;

        // Generate default based on block properties
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        float resistance = HullConfig.DEFAULT_RESISTANCE;
        if (item instanceof BlockItem blockItem) {
            try {
                BlockState state = blockItem.getBlock().defaultBlockState();
                // getDestroySpeed(null, null) is safe for vanilla blocks but modded
                // blocks may override and access the parameters — wrap in try-catch
                float destroySpeed = state.getDestroySpeed(null, null);
                if (destroySpeed > 0) {
                    resistance = destroySpeed;
                }
            } catch (Exception e) {
                // Modded block threw exception — use default resistance
            }
        }

        // Cache the default so we don't recompute every frame
        HullConfig defaultConfig = HullConfig.createDefault(itemId, resistance);
        CONFIGS.put(item, defaultConfig);
        return defaultConfig;
    }

    /**
     * Get max planks for an item. Convenience method.
     */
    public static int getMaxPlanks(Item item) {
        return getConfig(item).planksRequired();
    }

    /**
     * Get resistance for an item. Convenience method.
     */
    public static float getResistance(Item item) {
        return getConfig(item).resistance();
    }

    /**
     * Get armor model name for an item. Convenience method.
     */
    public static String getArmorModel(Item item) {
        return getConfig(item).armorModel();
    }

    /** Get effective block chance (direct or resistance/70). */
    public static float getBlockChance(Item item) {
        return getConfig(item).getEffectiveBlockChance();
    }

    /** Get swim speed bonus for an item. */
    public static float getSwimSpeedBonus(Item item) {
        return getConfig(item).swimSpeedBonus();
    }

    /** Get seat layout for an item. Returns default 7-seat layout if none defined. */
    public static SeatLayout getSeatLayout(Item item) {
        SeatLayout layout = SEAT_LAYOUTS.get(item);
        return layout != null ? layout : SeatLayout.defaultLayout();
    }
}
