package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.GadgetItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public class ArrowGrenadeModelSmokeChecks {
    private static int ticks;private static boolean shown,done;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||done)return;var mc=Minecraft.getInstance();
        org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        try {
            if(!shown){
                if(mc.screen==null||mc.getOverlay()!=null)return;
                var model=mc.getItemRenderer().getModel(new ItemStack(GadgetItems.ARROW_GRENADE.get()),null,null,0);
                var quads=model.getQuads(null,null,net.minecraft.util.RandomSource.create());
                if(model==mc.getModelManager().getMissingModel()||quads.isEmpty())throw new AssertionError("Missing grenade geometry");
                for(var quad:quads)if(quad.getSprite().contents().name().getPath().contains("missingno"))throw new AssertionError("Missing grenade texture");
                mc.options.guiScale().set(2);mc.resizeDisplay();mc.setScreen(new Preview());shown=true;return;
            }
            if(++ticks<25)return;
            try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(java.nio.file.Path.of("preview.png"));}
            System.out.println("ARROW_GRENADE_MODEL_PASS");done=true;mc.stop();
        }catch(Throwable failure){failure.printStackTrace();System.out.println("ARROW_GRENADE_MODEL_FAIL");done=true;mc.stop();}
    }
    private static class Preview extends Screen {
        Preview(){super(Component.literal("Arrow grenade"));}
        @Override public void render(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float partial){
            g.fill(0,0,width,height,0xFF202832);g.drawString(font,"ARROW GRENADE / ID 79",16,16,0xFFFFFFFF);
            var pose=g.pose();pose.pushPose();pose.translate(width*.5,height*.55,150);pose.scale(200,-200,200);
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(15));pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(135));
            Minecraft.getInstance().getItemRenderer().renderStatic(new ItemStack(GadgetItems.ARROW_GRENADE.get()),ItemDisplayContext.FIXED,15728880,OverlayTexture.NO_OVERLAY,pose,g.bufferSource(),null,0);
            pose.popPose();g.flush();
        }
    }
}
