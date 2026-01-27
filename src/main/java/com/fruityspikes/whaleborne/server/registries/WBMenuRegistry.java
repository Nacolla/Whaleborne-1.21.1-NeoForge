package com.fruityspikes.whaleborne.server.registries;

import com.fruityspikes.whaleborne.Whaleborne;
import com.fruityspikes.whaleborne.client.menus.CannonMenu;
import com.fruityspikes.whaleborne.client.menus.HullbackMenu;
import com.fruityspikes.whaleborne.server.entities.CannonEntity;
import com.fruityspikes.whaleborne.server.items.WhaleEquipment;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class WBMenuRegistry {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Whaleborne.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<CannonMenu>> CANNON_MENU =
            MENUS.register("cannon_menu", () ->
                    IMenuTypeExtension.create(CannonMenu::fromNetwork));
    public static final DeferredHolder<MenuType<?>, MenuType<HullbackMenu>> HULLBACK_MENU =
            MENUS.register("hullback_menu", () ->
                    IMenuTypeExtension.create(HullbackMenu::fromNetwork));
}
