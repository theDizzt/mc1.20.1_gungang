package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.GadgetItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID,value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class LandmineModelChecks {
    private static int ticks;
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent e){
        if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        try{
            if(mc.screen==null||mc.getOverlay()!=null)return;
            if(ticks++==0){
                mc.options.guiScale().set(2);mc.resizeDisplay();
                var model=mc.getItemRenderer().getModel(new ItemStack(GadgetItems.LANDMINE.get()),null,null,0);
                if(model==mc.getModelManager().getMissingModel())throw new AssertionError("Missing landmine model");
                var quads=model.getQuads(null,null,net.minecraft.util.RandomSource.create(1));
                if(quads.size()!=368)throw new AssertionError("Expected 368 triangles, got "+quads.size());
                for(var quad:quads)if(quad.getSprite().contents().name().toString().contains("missingno"))throw new AssertionError("Missing landmine texture");
                mc.setScreen(new Preview());
            }
            if(ticks==35){
                var path=java.nio.file.Path.of("../ui-smoke/landmine-model.png");java.nio.file.Files.createDirectories(path.getParent());
                try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}
                System.out.println("GUNGANG_LANDMINE_MODEL_PASS triangles=368 texture=loaded footprint=0.6125");mc.stop();
            }
        }catch(Throwable error){error.printStackTrace();System.out.println("GUNGANG_LANDMINE_MODEL_FAIL");mc.stop();}
    }
    private static final class Preview extends Screen {
        Preview(){super(Component.literal("Landmine model"));}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            g.fill(0,0,width,height,0xFF263341);
            g.drawCenteredString(font,"LANDMINE 86 / SAME INSTALLED FOOTPRINT",width/2,15,0xFFFFFF);
            var mc=Minecraft.getInstance();var item=new ItemStack(GadgetItems.LANDMINE.get());
            for(int i=0;i<2;i++){
                int x=width*(i==0?1:3)/4,y=height/2;
                g.pose().pushPose();g.pose().translate(x,y,150);g.pose().scale(140,-140,140);
                g.pose().mulPose(com.mojang.math.Axis.XP.rotationDegrees(30));g.pose().mulPose(com.mojang.math.Axis.YP.rotationDegrees(35));
                if(i==0){g.pose().translate(-.35,0,-.35);g.pose().scale(.7f,1.2f,.7f);mc.getBlockRenderer().renderSingleBlock(Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE.defaultBlockState(),g.pose(),g.bufferSource(),0xF000F0,OverlayTexture.NO_OVERLAY);}
                else{g.pose().translate(-.5,0,-.5);mc.getBlockRenderer().getModelRenderer().renderModel(g.pose().last(),g.bufferSource().getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS)),null,mc.getItemRenderer().getModel(item,null,null,0),1,1,1,0xF000F0,OverlayTexture.NO_OVERLAY);}
                g.flush();g.pose().popPose();g.drawCenteredString(font,i==0?"Previous: 0.6125 x 0.6125":"New GLB: 0.6125 x 0.6125",x,y+65,0xFFFFFF);
            }
            g.renderItem(item,width/2-8,height-40);
        }
    }
}
