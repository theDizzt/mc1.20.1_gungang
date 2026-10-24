package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.GadgetItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class TurretModelSmokeChecks {
    private static int ticks;
    private static boolean shown,done;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event)throws Exception {
        if(event.phase!=TickEvent.Phase.END||done)return;var mc=Minecraft.getInstance();
        org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        try{
            if(!shown){
                if(mc.screen==null||mc.getOverlay()!=null)return;
                for(var id:java.util.List.of(TurretRenderer.BASE,TurretRenderer.HEAD)){
                    var model=mc.getModelManager().getModel(id);
                    if(model==mc.getModelManager().getMissingModel()||model.getQuads(null,null,net.minecraft.util.RandomSource.create()).isEmpty())throw new AssertionError("Missing model "+id);
                }
                var item=mc.getItemRenderer().getModel(new ItemStack(GadgetItems.TURRET.get()),null,null,0);
                if(item==mc.getModelManager().getMissingModel())throw new AssertionError("Missing turret item model");
                mc.options.guiScale().set(2);mc.resizeDisplay();mc.setScreen(new Preview());shown=true;return;
            }
            if(++ticks<25)return;
            try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(java.nio.file.Path.of("preview.png"));}
            System.out.println("GUNGANG_TURRET_MODEL_PASS base head item texture screenshot");done=true;mc.stop();
        }catch(Throwable failure){failure.printStackTrace();System.out.println("GUNGANG_TURRET_MODEL_FAIL");done=true;mc.stop();}
    }
    private static final class Preview extends Screen {
        Preview(){super(Component.literal("Turret model"));}
        @Override public void render(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float partial){
            g.fill(0,0,width,height,0xFF202832);g.drawString(font,"IRON-NEST / ID 90",16,16,0xFFFFFFFF);
            var mc=Minecraft.getInstance();var pose=g.pose();pose.pushPose();
            pose.translate(width*.5,height*.7,150);pose.scale(220,-220,220);
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(20));pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(145));
            pose.translate(-.5,0,-.5);
            for(var id:java.util.List.of(TurretRenderer.BASE,TurretRenderer.HEAD)){
                mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(),g.bufferSource().getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS)),null,
                        mc.getModelManager().getModel(id),1,1,1,15728880,OverlayTexture.NO_OVERLAY);
            }
            pose.popPose();g.flush();
        }
    }
}
