package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = GungangAcoustics.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    @SubscribeEvent public static void layers(net.minecraftforge.client.event.EntityRenderersEvent.AddLayers event) {
        for(String skin:event.getSkins()) {
            net.minecraft.client.renderer.entity.player.PlayerRenderer renderer=event.getSkin(skin);
            if(renderer!=null)renderer.addLayer(new ShieldRenderLayer(renderer));
        }
    }
    @SubscribeEvent public static void renderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(dev.gungang.acoustics.server.PropEntity.TYPE.get(),PropRenderer::new);
        event.registerEntityRenderer(dev.gungang.acoustics.server.EscortBlocks.CART.get(),EscortRenderer::new);
        event.registerEntityRenderer(dev.gungang.acoustics.server.EscortBlocks.RAIL.get(),EscortRenderer.Rail::new);
        event.registerEntityRenderer(dev.gungang.acoustics.server.GimmickBlocks.PUDDLE_OVERLAY.get(),PuddleRenderer::new);
        event.registerEntityRenderer(dev.gungang.acoustics.server.GimmickBlocks.ACID_PUDDLE_OVERLAY.get(),PuddleRenderer::new);
        event.registerBlockEntityRenderer(dev.gungang.acoustics.server.StationBlocks.ENTITY.get(),StationRenderer::new);
        event.registerEntityRenderer(dev.gungang.acoustics.server.GadgetItems.DEPLOYABLE.get(),DeployableRenderer::new);
        event.registerEntityRenderer(dev.gungang.acoustics.server.GadgetItems.MOLOTOV_PROJECTILE.get(),net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
        event.registerEntityRenderer(dev.gungang.acoustics.server.GadgetItems.ORDNANCE.get(),OrdnanceRenderer::new);
    }
    @SubscribeEvent public static void blockColors(net.minecraftforge.client.event.RegisterColorHandlersEvent.Block event) {
        // In bomb mode a gate takes the colour of the side currently using its spawn (attackers / defenders).
        event.register((state,level,pos,tint)->gateColor(dev.gungang.acoustics.server.StationBlocks.ALPHA_GATE.get()),dev.gungang.acoustics.server.StationBlocks.ALPHA_GATE.get());
        event.register((state,level,pos,tint)->gateColor(dev.gungang.acoustics.server.StationBlocks.BRAVO_GATE.get()),dev.gungang.acoustics.server.StationBlocks.BRAVO_GATE.get());
        event.register((state,level,pos,tint)->level!=null&&pos!=null?net.minecraft.client.renderer.BiomeColors.getAverageWaterColor(level,pos):0x3F76E4,dev.gungang.acoustics.server.GimmickBlocks.PUDDLE.get());
        event.register((state,level,pos,tint)->LobbyClient.options.color("ALPHA"),dev.gungang.acoustics.server.StationBlocks.ALPHA.get());
        event.register((state,level,pos,tint)->LobbyClient.options.color("BRAVO"),dev.gungang.acoustics.server.StationBlocks.BRAVO.get());
    }
    private static int gateColor(dev.gungang.acoustics.server.SpawnGateBlock gate){
        String rule=MatchHud.gateRule();
        return LobbyClient.options.color("ALPHA".equals(rule)||"BRAVO".equals(rule)?gate.owner(rule):gate.team);
    }
    @SubscribeEvent public static void itemColors(net.minecraftforge.client.event.RegisterColorHandlersEvent.Item event) {
        event.register((stack,tint)->LobbyClient.options.color("ALPHA"),dev.gungang.acoustics.server.StationBlocks.ALPHA_GATE.get());
        event.register((stack,tint)->LobbyClient.options.color("BRAVO"),dev.gungang.acoustics.server.StationBlocks.BRAVO_GATE.get());
        event.register(new net.minecraftforge.client.model.DynamicFluidContainerModel.Colors(),dev.gungang.acoustics.server.GimmickBlocks.ACID_BUCKET.get());
        event.register((stack,tint)->0x3F76E4,dev.gungang.acoustics.server.GimmickBlocks.PUDDLE.get());
        event.register((stack,tint)->tint==0?0xCC2020:0xFFFFFF,dev.gungang.acoustics.server.GadgetItems.MOLOTOV.get());
        event.register((stack,tint)->tint==0?0xFFE033:0xFFFFFF,dev.gungang.acoustics.server.GadgetItems.ADRENALINE.get());
        event.register((stack,tint)->LobbyClient.options.color("ALPHA"),dev.gungang.acoustics.server.StationBlocks.ALPHA.get());
        event.register((stack,tint)->LobbyClient.options.color("BRAVO"),dev.gungang.acoustics.server.StationBlocks.BRAVO.get());
    }
    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent event) {
        event.register(OffhandMacro.ATTACK);
        event.register(OffhandMacro.USE);
        event.register(LobbyClient.LOADOUT);
        event.register(LobbyClient.LOBBY);
        event.register(ItemNbtCopy.COPY);
    }
    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent event) {
        event.registerBelow(VanillaGuiOverlay.HOTBAR.id(), "damage_vignette", DamageVignette::render);
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "shooter_hud", ShooterHud::render);
    }
    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        dev.gungang.acoustics.server.PropDimensions.clientProp=PropHuntClient::prop;
        dev.gungang.acoustics.server.SpawnGateBlock.clientRule=MatchHud::gateRule;
        event.enqueueWork(()->{
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(dev.gungang.acoustics.server.StationBlocks.ALPHA_GATE.get(),net.minecraft.client.renderer.RenderType.translucent());
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(dev.gungang.acoustics.server.StationBlocks.BRAVO_GATE.get(),net.minecraft.client.renderer.RenderType.translucent());
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(dev.gungang.acoustics.server.GimmickBlocks.ACID_SOURCE.get(),net.minecraft.client.renderer.RenderType.translucent());
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(dev.gungang.acoustics.server.GimmickBlocks.ACID_FLOWING.get(),net.minecraft.client.renderer.RenderType.translucent());
        });
        event.enqueueWork(()->net.minecraft.client.renderer.item.ItemProperties.register(
                dev.gungang.acoustics.server.GadgetItems.SHIELD.get(),new net.minecraft.resources.ResourceLocation("gungang","blocking"),
                (stack,level,entity,seed)->entity instanceof net.minecraft.world.entity.player.Player player
                        &&player.getMainHandItem()==stack
                        &&dev.gungang.acoustics.server.CombatGadgets.holdingShield(player)?1:0));
        ModList.get().getModContainerById(GungangAcoustics.ID).orElseThrow().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new AcousticConfigScreen(parent)));
    }
    private ClientSetup() { }
}
