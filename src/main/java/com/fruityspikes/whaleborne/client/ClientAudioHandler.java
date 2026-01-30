package com.fruityspikes.whaleborne.client;

import com.fruityspikes.whaleborne.Config;
import com.fruityspikes.whaleborne.Whaleborne;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Intercepts Hullback ambient sounds on the client side to apply user-configured volume.
 * This allows each player to customize sound distance without affecting others.
 * 
 * The server sends sounds with a fixed default volume (3.0f).
 * This handler REPLACES that sound instance with one using the client's preference.
 */
@EventBusSubscriber(modid = Whaleborne.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class ClientAudioHandler {

    // Only intercept the ambient sound
    private static final String AMBIENT_SOUND_PATH = "entity.hullback.ambient";

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance original = event.getSound();
        if (original == null) return;
        
        ResourceLocation soundLocation = original.getLocation();
        
        // Only process the hullback ambient sound from our mod
        if (!soundLocation.getNamespace().equals(Whaleborne.MODID)) {
            return;
        }
        if (!soundLocation.getPath().equals(AMBIENT_SOUND_PATH)) {
            return;
        }
        
        // Get user's configured volume (defaults to 3.0 if config not yet loaded)
        float userVolume = Config.soundDistance > 0 ? (float) Config.soundDistance : 3.0f;
        
        // If user wants 0 volume, cancel the sound entirely
        if (userVolume <= 0.01f) {
            event.setSound(null);
            return;
        }
        
        // Replace with a wrapper that implements SoundInstance directly
        // This avoids calling methods like getPitch() in the constructor which can cause NPEs
        // if the original sound hasn't been resolved yet.
        event.setSound(new VolumeOverrideSound(original, userVolume));
    }
    
    /**
     * A SoundInstance wrapper that delegates EVERYTHING to the original but overrides volume.
     * We implement the interface directly to avoid inheritance issues/side effects from AbstractSoundInstance.
     */
    private static class VolumeOverrideSound implements SoundInstance {
        private final SoundInstance original;
        private final float customVolume;
        
        public VolumeOverrideSound(SoundInstance original, float volume) {
            this.original = original;
            this.customVolume = volume;
        }

        @Override
        public ResourceLocation getLocation() {
            return original.getLocation();
        }

        @Override
        public WeighedSoundEvents resolve(SoundManager manager) {
            return original.resolve(manager);
        }

        @Override
        public Sound getSound() {
            return original.getSound();
        }

        @Override
        public SoundSource getSource() {
            return original.getSource();
        }

        @Override
        public boolean isLooping() {
            return original.isLooping();
        }

        @Override
        public boolean isRelative() {
            return original.isRelative();
        }

        @Override
        public int getDelay() {
            return original.getDelay();
        }

        @Override
        public float getVolume() {
            return this.customVolume;
        }

        @Override
        public float getPitch() {
            return original.getPitch();
        }

        @Override
        public double getX() {
            return original.getX();
        }

        @Override
        public double getY() {
            return original.getY();
        }

        @Override
        public double getZ() {
            return original.getZ();
        }

        @Override
        public Attenuation getAttenuation() {
            return original.getAttenuation();
        }
    }
}

