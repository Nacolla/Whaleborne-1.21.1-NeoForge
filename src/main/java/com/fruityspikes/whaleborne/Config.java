package com.fruityspikes.whaleborne;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;

import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = Whaleborne.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.Builder COMMON_BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ARMOR_PROGRESS = CLIENT_BUILDER
            .comment("Shows hullback building and damage progress. Turning this off solve incompatibility issues with shaders but will sacrifice some visual flair")
            .define("hullbackArmorProgress", true);

    public static final ModConfigSpec.DoubleValue SOUND_DISTANCE = COMMON_BUILDER
            .comment("Determines how far hullback sounds travel.")
            .defineInRange("hullbackSoundDistance", 3f, 0f, 5f);

    static final ModConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();
    static final ModConfigSpec COMMON_SPEC = COMMON_BUILDER.build();

    public static boolean armorProgress;
    public static double soundDistance;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
        if (event.getConfig().getSpec() == CLIENT_SPEC) {
            armorProgress = ARMOR_PROGRESS.get();
        } else if (event.getConfig().getSpec() == COMMON_SPEC) {
            soundDistance = SOUND_DISTANCE.get();
        }
    }
}