package com.fruityspikes.whaleborne.network;

import com.fruityspikes.whaleborne.Whaleborne;
import com.fruityspikes.whaleborne.server.entities.HullbackEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ToggleControlPayload(int entityId, boolean vectorControl) implements CustomPacketPayload {

    public static final Type<ToggleControlPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Whaleborne.MODID, "toggle_control"));

    public static final StreamCodec<ByteBuf, ToggleControlPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ToggleControlPayload::entityId,
            ByteBufCodecs.BOOL, ToggleControlPayload::vectorControl,
            ToggleControlPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ToggleControlPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Entity entity = player.level().getEntity(payload.entityId);
                if (entity instanceof HullbackEntity hullback) {
                    // SAFETY: Only allow toggle if the player is piloting (uses getRootVehicle for Helm compat)
                    if (player.getRootVehicle() == hullback) {
                         hullback.setVectorControl(payload.vectorControl);
                    }
                }
            }
        });
    }
}
