package com.fruityspikes.whaleborne.server.loot_modifiers;



import java.util.function.Supplier;
import com.google.common.base.Suppliers;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;

// https://github.com/GrowthcraftCE/Growthcraft-1.20/blob/release/src/main/java/growthcraft/lib/loot/AddLootTableModifier.java
public class AddLootTableModifier extends LootModifier {
    public static final Supplier<MapCodec<AddLootTableModifier>> CODEC = Suppliers.memoize(() ->
            RecordCodecBuilder.mapCodec(inst -> codecStart(inst)
                    .and(ResourceLocation.CODEC.fieldOf("lootTable").forGetter((m) -> m.lootTable))
                    .apply(inst, AddLootTableModifier::new)));

    private final ResourceLocation lootTable;

    public AddLootTableModifier(LootItemCondition[] conditionsIn, ResourceLocation lootTable) {
        super(conditionsIn);
        this.lootTable = lootTable;
    }

    private static final ThreadLocal<Boolean> RECURSION_GUARD = ThreadLocal.withInitial(() -> false);

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (RECURSION_GUARD.get()) {
            return generatedLoot;
        }
        
        try {
            RECURSION_GUARD.set(true);
            LootTable extraTable = context.getLevel().getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, this.lootTable));
            com.fruityspikes.whaleborne.Whaleborne.LOGGER.info("Injecting loot from table: {}", this.lootTable);
            extraTable.getRandomItems(context, generatedLoot::add);
        } finally {
            RECURSION_GUARD.set(false);
        }
        
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
