package com.fruityspikes.whaleborne.network;

import com.fruityspikes.whaleborne.Whaleborne;
import com.fruityspikes.whaleborne.server.entities.CannonEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CannonFirePayload(int entityId, int power) implements CustomPacketPayload {
    public static final Type<CannonFirePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Whaleborne.MODID, "cannon_fire"));

    public static final StreamCodec<ByteBuf, CannonFirePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, CannonFirePayload::entityId,
            ByteBufCodecs.INT, CannonFirePayload::power,
            CannonFirePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CannonFirePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sender) {
                Entity entity = sender.level().getEntity(payload.entityId());
                if (entity instanceof CannonEntity cannon) {
                    if (cannon.getFirstPassenger() == sender) {
                        cannon.fireCannon(payload.power());
                    }
                }
            }
        });
    }
}