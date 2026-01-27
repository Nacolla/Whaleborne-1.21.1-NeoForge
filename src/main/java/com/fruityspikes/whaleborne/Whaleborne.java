package com.fruityspikes.whaleborne;

import com.fruityspikes.whaleborne.client.ClientProxy;
import com.fruityspikes.whaleborne.client.menus.CannonScreen;
import com.fruityspikes.whaleborne.client.menus.HullbackScreen;
import com.fruityspikes.whaleborne.client.models.*;
import com.fruityspikes.whaleborne.client.renderers.*;
import com.fruityspikes.whaleborne.server.entities.AnchorEntity;
import com.fruityspikes.whaleborne.server.entities.HelmEntity;
import com.fruityspikes.whaleborne.server.entities.HullbackEntity;
import com.fruityspikes.whaleborne.server.particles.WBSmokeProvider;
import com.fruityspikes.whaleborne.server.registries.*;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.ModContainer;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Whaleborne.MODID)
public class Whaleborne
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "whaleborne";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static CommonProxy PROXY = FMLLoader.getDist().isClient() ? new ClientProxy() : new CommonProxy();

    public static final ResourceLocation ANCHOR_GUI = ResourceLocation.fromNamespaceAndPath(Whaleborne.MODID, "textures/gui/anchor.png");
    public Whaleborne(IEventBus modEventBus, ModContainer modContainer)
    {

        modEventBus.addListener(this::commonSetup);

        WBEntityRegistry.ENTITY_TYPES.register(modEventBus);
        WBBlockRegistry.BLOCKS.register(modEventBus);
        WBItemRegistry.ITEMS.register(modEventBus);
        WBMenuRegistry.MENUS.register(modEventBus);
        WBSoundRegistry.SOUND_EVENTS.register(modEventBus);
        WBLootModifierRegistry.LOOT_MODIFIER_SERIALIZERS.register(modEventBus);
        WBParticleRegistry.PARTICLE_TYPES.register(modEventBus);
        WBCreativeTabsRegistry.CREATIVE_MODE_TABS.register(modEventBus);

        NeoForge.EVENT_BUS.register(this);

        PROXY.init();
        modEventBus.addListener(this::addCreative);
        modEventBus.addListener(this::registerSpawnPlacements);
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
    }
    private void commonSetup(final FMLCommonSetupEvent event)
    {
        // Some common setup code
        //LOGGER.info("HELLO FROM COMMON SETUP");

        //if (Config.logDirtBlock)
        //    LOGGER.info("DIRT BLOCK >> {}", ForgeRegistries.BLOCKS.getKey(Blocks.DIRT));

        //LOGGER.info(Config.magicNumberIntroduction + Config.magicNumber);

        //Config.items.forEach((item) -> LOGGER.info("ITEM >> {}", item.toString()));
    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event)
    {
        //if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS)
        //    event.accept(EXAMPLE_BLOCK_ITEM);
    }

    public void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(
            WBEntityRegistry.HULLBACK.get(),
            SpawnPlacementTypes.IN_WATER,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            HullbackEntity::checkHullbackSpawnRules,
            RegisterSpawnPlacementsEvent.Operation.REPLACE
        );
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }


    @SubscribeEvent
    public void register(AddReloadListenerEvent event) {
        event.addListener(Whaleborne.PROXY.getHullbackDirtManager());
    }
    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void registerOverlays(RegisterGuiLayersEvent event) {
            event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(MODID, "anchor_overlay"), (guiGraphics, partialTick) -> {
                int width = Minecraft.getInstance().getWindow().getGuiScaledWidth();
                int height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
                Player player = Minecraft.getInstance().player;

                if (player.getVehicle() instanceof HelmEntity) {
                    if (player.getRootVehicle() instanceof HullbackEntity hullback) {
                        for (Entity passenger : hullback.getPassengers()) {
                            if (passenger instanceof AnchorEntity) {
                                    int j = width / 2 - 12;
                                    int k = height - 28 - 13;

                                    guiGraphics.pose().pushPose();
                                    guiGraphics.blit(ANCHOR_GUI, j, k, 0, 0, hullback.hasAnchorDown() ? 24 : 0, 24, 24, 24, 48);
                                    guiGraphics.pose().popPose();
                                    break;
                            }
                        }
                    }
                }
            });
            //event.registerAboveAll("whaleborne_cannon_overlay", (gui, poseStack, partialTick, width, height) -> {
            //    Player player = Minecraft.getInstance().player;
//
            //    if (player.getVehicle() instanceof CannonEntity cannonEntity) {
//
            //        int j = width / 2;
            //        int k = height / 2 - 8;
//
            //        poseStack.pose().pushPose();
            //        poseStack.renderItem(cannonEntity.inventory.getItem(0), j - 32, k);
            //        poseStack.renderItem(cannonEntity.inventory.getItem(1), j + 16, k);
            //       // poseStack.blit(ANCHOR_GUI, j, k, 0, 0, hullback.hasAnchorDown() ? 24 : 0, 24, 24, 24, 48);
            //        poseStack.pose().popPose();
            //    }
            //});
        }

        @SubscribeEvent
        public static void registerScreens(RegisterMenuScreensEvent event) {
            event.register(WBMenuRegistry.CANNON_MENU.get(), CannonScreen::new);
            event.register(WBMenuRegistry.HULLBACK_MENU.get(), HullbackScreen::new);
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            //Screen registration moved to registerScreens
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }
        @SubscribeEvent
        public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(WBEntityModelLayers.HULLBACK, HullbackModel::createBodyLayer);
            event.registerLayerDefinition(WBEntityModelLayers.HULLBACK_ARMOR, HullbackArmorModel::createBodyLayer);
            event.registerLayerDefinition(WBEntityModelLayers.SAIL, SailModel::createBodyLayer);
            event.registerLayerDefinition(WBEntityModelLayers.HELM, HelmModel::createBodyLayer);
            event.registerLayerDefinition(WBEntityModelLayers.MAST, MastModel::createBodyLayer);
            event.registerLayerDefinition(WBEntityModelLayers.CANNON, CannonModel::createBodyLayer);
            event.registerLayerDefinition(WBEntityModelLayers.ANCHOR, AnchorModel::createBodyLayer);
            event.registerLayerDefinition(WBEntityModelLayers.ANCHOR_HEAD, AnchorHeadModel::createBodyLayer);
        }

        @SubscribeEvent
        public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(WBEntityRegistry.HULLBACK.get(), HullbackRenderer::new);
            event.registerEntityRenderer(WBEntityRegistry.SAIL.get(), SailRenderer::new);
            event.registerEntityRenderer(WBEntityRegistry.MAST.get(), MastRenderer::new);
            event.registerEntityRenderer(WBEntityRegistry.CANNON.get(), CannonRenderer::new);
            event.registerEntityRenderer(WBEntityRegistry.HELM.get(), HelmRenderer::new);
            event.registerEntityRenderer(WBEntityRegistry.ANCHOR.get(), AnchorRenderer::new);
            event.registerEntityRenderer(WBEntityRegistry.ANCHOR_HEAD.get(), AnchorHeadRenderer::new);
            event.registerEntityRenderer(WBEntityRegistry.HULLBACK_PLATFORM.get(), NothingRenderer::new);
        }

        @SubscribeEvent
        public static void registerParticleFactories(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(WBParticleRegistry.SMOKE.get(), WBSmokeProvider::new);
        }


    }
}
