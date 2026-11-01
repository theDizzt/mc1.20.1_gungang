package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID,value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class HotSpringModelChecks {
    private static int ticks;
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent e){
        if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(mc.screen==null||mc.getOverlay()!=null)return;
        try{
            if(ticks++==0){
                for(var block:java.util.List.of(HotSpringContent.NOZZLE.get(),HotSpringContent.TOAD.get(),HotSpringContent.GEYSER.get())){
                    for(var state:block.getStateDefinition().getPossibleStates()){
                        var model=mc.getBlockRenderer().getBlockModel(state);var quads=model.getQuads(state,null,net.minecraft.util.RandomSource.create());
                        if(model==mc.getModelManager().getMissingModel()||quads.isEmpty()||quads.stream().anyMatch(q->q.getSprite().contents().name().getPath().equals("missingno")))throw new AssertionError("Missing thermal block model/texture: "+state);
                    }
                }
                var bucket=mc.getItemRenderer().getModel(new net.minecraft.world.item.ItemStack(HotSpringContent.BUCKET.get()),null,null,0);
                if(bucket==mc.getModelManager().getMissingModel())throw new AssertionError("Missing spring bucket");
                var atlas=mc.getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS);
                for(String texture:java.util.List.of("hot_spring_still","hot_spring_flow"))if(atlas.apply(new net.minecraft.resources.ResourceLocation("gungang","block/"+texture)).contents().name().getPath().equals("missingno"))throw new AssertionError("Missing water texture");
                mc.options.guiScale().set(2);mc.resizeDisplay();mc.setScreen(new Preview());
            }
            if(ticks==45){
                var path=java.nio.file.Path.of("../ui-smoke/hot-spring.png");java.nio.file.Files.createDirectories(path.getParent());
                try(var im=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){im.writeToFile(path);}
                System.out.println("GUNGANG_HOT_SPRING_CLIENT_PASS all_states bucket animated_fluid_textures");mc.stop();
            }
        }catch(Throwable ex){ex.printStackTrace();System.out.println("GUNGANG_HOT_SPRING_CLIENT_FAIL");mc.stop();}
    }
    private static final class Preview extends Screen {
        Preview(){super(Component.literal("Hot spring collection"));}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            g.fill(0,0,width,height,0xFF203744);g.drawCenteredString(font,"HOT SPRING / MINERAL WATER & BATH FIXTURES",width/2,12,0xDEFAFF);
            int y=height*2/3;
            draw(g,HotSpringContent.TOAD.get().defaultBlockState(),width/5,y,90);
            draw(g,HotSpringContent.NOZZLE.get().defaultBlockState(),width/2,y,75);
            g.pose().pushPose();g.pose().translate(width*4/5,y-6,150);g.pose().scale(31,-31,31);g.pose().mulPose(com.mojang.math.Axis.XP.rotationDegrees(35));g.pose().mulPose(com.mojang.math.Axis.YP.rotationDegrees(30));
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
                g.pose().pushPose();g.pose().translate(x-.5,0,z-.5);Minecraft.getInstance().getBlockRenderer().renderSingleBlock(HotSpringContent.GEYSER.get().defaultBlockState().setValue(ThermalEmitterBlock.PART,(z+1)*3+x+1),g.pose(),g.bufferSource(),0xF000F0,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);g.pose().popPose();
            }g.flush();g.pose().popPose();
            g.drawCenteredString(font,"BATH TOAD",width/5,y+37,0xFFFFFF);g.drawCenteredString(font,"SPRING NOZZLE",width/2,y+37,0xFFFFFF);g.drawCenteredString(font,"3 x 8 x 3 GEYSER",width*4/5,y+37,0xFFFFFF);
            g.pose().pushPose();g.pose().translate(width/2-16,height-40,0);g.pose().scale(2,2,2);g.renderItem(new net.minecraft.world.item.ItemStack(HotSpringContent.BUCKET.get()),0,0);g.pose().popPose();
        }
    }
    private static void draw(GuiGraphics g,net.minecraft.world.level.block.state.BlockState state,int x,int y,int size){
        g.pose().pushPose();g.pose().translate(x,y,150);g.pose().scale(size,-size,size);g.pose().mulPose(com.mojang.math.Axis.XP.rotationDegrees(22));g.pose().mulPose(com.mojang.math.Axis.YP.rotationDegrees(155));g.pose().translate(-.5,0,-.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state,g.pose(),g.bufferSource(),0xF000F0,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);g.flush();g.pose().popPose();
    }
}
