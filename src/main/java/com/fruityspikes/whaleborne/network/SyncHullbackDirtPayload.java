package com.fruityspikes.whaleborne.network;

import com.fruityspikes.whaleborne.Whaleborne;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.network.RegistryFriendlyByteBuf;

import javax.annotation.Nullable;

public record SyncHullbackDirtPayload(int entityId, CompoundTag dirtData, int arrayType, boolean isBottom) implements CustomPacketPayload {
    public static final Type<SyncHullbackDirtPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Whaleborne.MODID, "sync_hullback_dirt"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncHullbackDirtPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, SyncHullbackDirtPayload::entityId,
            ByteBufCodecs.COMPOUND_TAG, SyncHullbackDirtPayload::dirtData,
            ByteBufCodecs.INT, SyncHullbackDirtPayload::arrayType,
            ByteBufCodecs.BOOL, SyncHullbackDirtPayload::isBottom,
            SyncHullbackDirtPayload::new
    );

    public SyncHullbackDirtPayload(int entityId, BlockState[][] dirtArray, int arrayType, boolean isBottom) {
        this(entityId, serializeDirtArray(dirtArray), arrayType, isBottom);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buf, SyncHullbackDirtPayload payload) {
        buf.writeInt(payload.entityId);
        buf.writeByte(payload.arrayType);
        buf.writeBoolean(payload.isBottom);
        buf.writeNbt(payload.dirtData);
    }

    public static SyncHullbackDirtPayload decode(RegistryFriendlyByteBuf buf) {
        int entityId = buf.readInt();
        int arrayType = buf.readByte();
        boolean isBottom = buf.readBoolean();
        CompoundTag dirtData = buf.readNbt();
        return new SyncHullbackDirtPayload(entityId, dirtData, arrayType, isBottom);
    }

    public static void handle(SyncHullbackDirtPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPacketHandler.handleDirtSync(payload));
    }

    private static CompoundTag serializeDirtArray(BlockState[][] array) {
        CompoundTag tag = new CompoundTag();
        if (array == null || array.length == 0) {
            tag.putInt("width", 0);
            tag.putInt("height", 0);
            return tag;
        }

        tag.putInt("width", array.length);
        tag.putInt("height", array[0].length);

        for (int x = 0; x < array.length; x++) {
            ListTag column = new ListTag();
            if (array[x] != null) {
                for (int y = 0; y < array[x].length; y++) {
                    column.add(NbtUtils.writeBlockState(array[x][y] != null ? array[x][y] : Blocks.AIR.defaultBlockState()));
                }
            }
            tag.put("x" + x, column);
        }
        return tag;
    }

    public static BlockState[][] deserializeDirtArray(@Nullable CompoundTag tag) {
        if (tag == null || !tag.contains("width") || !tag.contains("height")) {
            return new BlockState[0][0];
        }

        int width = tag.getInt("width");
        int height = tag.getInt("height");
        BlockState[][] array = new BlockState[width][height];

        for (int x = 0; x < width; x++) {
            String key = "x" + x;
            if (tag.contains(key)) {
                ListTag column = tag.getList(key, 10);
                for (int y = 0; y < Math.min(column.size(), height); y++) {
                    array[x][y] = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), column.getCompound(y));
                }
            }
        }
        return array;
    }
}
