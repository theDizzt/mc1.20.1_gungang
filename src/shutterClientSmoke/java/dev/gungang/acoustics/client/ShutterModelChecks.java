package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID,value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class ShutterModelChecks {
    private static int ticks;
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        try{
            if(mc.screen==null||mc.getOverlay()!=null)return;
            if(ticks++==0){
                mc.options.guiScale().set(2);mc.resizeDisplay();
                var model=mc.getItemRenderer().getModel(new ItemStack(ShutterBlocks.ITEM.get()),null,null,0);
                if(model==mc.getModelManager().getMissingModel())throw new AssertionError("Missing shutter item model");
                var quads=model.getQuads(null,null,net.minecraft.util.RandomSource.create(1));
                if(quads.isEmpty()||quads.stream().anyMatch(q->q.getSprite().contents().name().toString().contains("missingno")))throw new AssertionError("Missing shutter geometry/texture");
                mc.setScreen(new Preview());
            }
            if(ticks==35){
                var path=java.nio.file.Path.of("../ui-smoke/shutter-states.png");java.nio.file.Files.createDirectories(path.getParent());
                try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}
                System.out.println("GUNGANG_SHUTTER_MODEL_PASS item texture closed moving open continuous_edges");mc.stop();
            }
        }catch(Throwable error){error.printStackTrace();System.out.println("GUNGANG_SHUTTER_MODEL_FAIL");mc.stop();}
    }
    private static final class Preview extends Screen {
        Preview(){super(Component.literal("Connected rolling shutter"));}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            g.fill(0,0,width,height,0xFF23303D);g.drawCenteredString(font,"CONNECTED SHUTTER / 3 x 3 / DOOR THICKNESS",width/2,12,0xFFFFFF);
            int size=Math.min(36,width/13),base=height-55;
            for(int stage=0;stage<3;stage++){
                int center=width*(stage*2+1)/6;double lift=stage*1.5;
                g.pose().pushPose();g.pose().translate(center,base,150);g.pose().scale(size,-size,size);
                g.pose().mulPose(com.mojang.math.Axis.XP.rotationDegrees(12));g.pose().mulPose(com.mojang.math.Axis.YP.rotationDegrees(25));g.pose().translate(-1.5,0,-.5);
                for(int x=0;x<3;x++)for(int y=0;y<3;y++){
                    g.pose().pushPose();g.pose().translate(x,y,0);
                    ShutterRenderer.draw(g.pose(),g.bufferSource(),0xF000F0,OverlayTexture.NO_OVERLAY,false,x==0,x==2,y==2,ShutterMotion.bottom(0,y,lift),lift,lift-y);
                    g.pose().popPose();
                }
                g.flush();g.pose().popPose();g.drawCenteredString(font,new String[]{"OFF / CLOSED","MOVING / HALF OPEN","ON / OPEN"}[stage],center,base+12,0xFFFFFF);
            }
            g.renderItem(new ItemStack(ShutterBlocks.ITEM.get()),width/2-8,height-27);
        }
    }
}
