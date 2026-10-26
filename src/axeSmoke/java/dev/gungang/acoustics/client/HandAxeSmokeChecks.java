package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.GadgetItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.world.item.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Actual Forge OBJ baking and renderer captures, excluded from release JARs. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class HandAxeSmokeChecks {
    private static final boolean RAPIER=Boolean.getBoolean("gungang.rapierPreview");
    private static final boolean SCANNER=Boolean.getBoolean("gungang.scannerPreview");
    private static final boolean ORBITAL=Boolean.getBoolean("gungang.orbitalPreview");
    private static ItemStack stack(){return new ItemStack(RAPIER?GadgetItems.RAPIER.get():ORBITAL?GadgetItems.ORBITAL_STRIKE.get():SCANNER?GadgetItems.SCANNER.get():GadgetItems.HAND_AXE.get());}
    private static String marker(){return ORBITAL?"GUNGANG_ORBITAL_CLIENT":SCANNER?"GUNGANG_SCANNER_CLIENT":"GUNGANG_HAND_AXE_CLIENT";}
    private static int expectedQuads(){return RAPIER?1750:ORBITAL?17496:SCANNER?7104:84;}
    private static java.util.Set<String> textures(){return RAPIER?java.util.Set.of("gungang:item/rapier"):ORBITAL?java.util.Set.of("gungang:item/orbital_strike"):SCANNER?java.util.Set.of("gungang:item/infrared_scanner_0","gungang:item/infrared_scanner_1"):java.util.Set.of("gungang:item/hand_axe");}
    private static int ticks;
    private static boolean opened;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        try {
            if(!opened) {
                if(mc.screen==null||mc.getOverlay()!=null)return;
                var model=mc.getItemRenderer().getModel(stack(),null,null,0);
                var quads=model.getQuads(null,null,net.minecraft.util.RandomSource.create());
                if(quads.size()!=expectedQuads())throw new AssertionError("Unexpected baked triangles: "+quads.size());
                for(var quad:quads)if(!textures().contains(quad.getSprite().contents().name().toString()))
                    throw new AssertionError("Wrong/missing texture: "+quad.getSprite().contents().name());
                mc.options.guiScale().set(2);mc.resizeDisplay();mc.setScreen(new AxeScreen());opened=true;
            }else if(++ticks==40) {
                var path=java.nio.file.Path.of(ORBITAL?"../ui-smoke/orbital-strike.png":SCANNER?"../ui-smoke/infrared-scanner.png":"../ui-smoke/hand-axe.png");java.nio.file.Files.createDirectories(path.getParent());
                try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}
                if(ORBITAL||SCANNER){System.out.println(marker()+"_PASS quads="+expectedQuads()+" texture=pass screenshot="+path);mc.stop();}
                else mc.setScreen(new MotionScreen());
            }else if(ticks==80) {
                var path=java.nio.file.Path.of(RAPIER?"../ui-smoke/rapier-motion.png":"../ui-smoke/hand-axe-motion.png");
                try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}
                System.out.println(marker()+"_PASS quads="+expectedQuads()+" texture=pass screenshot="+path);mc.stop();
            }
        }catch(Throwable error){error.printStackTrace();System.out.println(marker()+"_FAIL");mc.stop();}
    }
    /** First-person chop frames at 70 FOV (top two rows) and the third-person arm seen from the side (bottom row). */
    private static final class MotionScreen extends Screen {
        private static final float[] FIRST={-1,0,.24f,.34f,.46f,.59f,.76f,.90f,1,2};
        private static final String[] FIRST_LABELS={"carry (offhand)","ready",".24 wind-up",".34 accelerate",".46 strike",".59 follow-through",".76 recover",".90 ready","1 ready","throw charge"};
        private static final float[] THIRD={0,.24f,.46f,.59f,.76f};
        MotionScreen(){super(net.minecraft.network.chat.Component.literal("Hand axe motion"));}
        @Override public void render(GuiGraphics g,int mx,int my,float partial) {
            g.fill(0,0,width,height,0xff263747);
            var model=new net.minecraft.client.model.PlayerModel<net.minecraft.client.player.AbstractClientPlayer>(minecraft.getEntityModels().bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER),false);
            var skin=new net.minecraft.resources.ResourceLocation("minecraft","textures/entity/player/wide/steve.png");
            int cw=width/5,ch=height/3;
            for(int i=0;i<FIRST.length;i++) {
                int x=(i%5)*cw,y=(i/5)*ch;
                g.fill(x+1,y+1,x+cw-1,y+ch-1,0xff5f7d93);g.fill(x+cw/2,y+ch/2-3,x+cw/2+1,y+ch/2+4,0xffff4444);g.fill(x+cw/2-3,y+ch/2,x+cw/2+4,y+ch/2+1,0xffff4444);
                g.flush();
                firstPerson(x,y,cw,ch,model,skin,FIRST[i]);
                g.drawString(font,FIRST_LABELS[i],x+4,y+4,0xffffffff);
            }
            for(int i=0;i<THIRD.length;i++) {
                int x=i*cw,y=2*ch;
                g.drawString(font,"3rd "+THIRD[i],x+4,y+4,0xffffffff);
                g.flush();var pose=g.pose();pose.pushPose();pose.translate(x+cw/2f,y+ch*.42f,150);pose.scale(ch*.36f,ch*.36f,ch*.36f);
                pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90));
                for(var part:new net.minecraft.client.model.geom.ModelPart[]{model.body,model.rightArm,model.leftArm})part.resetPose();
                model.rightArm.xRot=-.3f;
                HandAxeAnimation.poseArm(model,true,THIRD[i],false);
                model.rightSleeve.copyFrom(model.rightArm);model.leftSleeve.copyFrom(model.leftArm);model.jacket.copyFrom(model.body);
                var buffers=g.bufferSource();com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
                model.renderToBuffer(pose,buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(skin)),15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,1,1,1,1);
                model.translateToHand(net.minecraft.world.entity.HumanoidArm.RIGHT,pose);
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90));pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));pose.translate(1/16f,.125f,-.625f);
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(HandAxeAnimation.wrist(THIRD[i])));
                minecraft.getItemRenderer().renderStatic(stack(),ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,pose,buffers,null,0);
                g.flush();pose.popPose();
            }
        }
        private void firstPerson(int x,int y,int w,int h,net.minecraft.client.model.PlayerModel<?> model,net.minecraft.resources.ResourceLocation skin,float t) {
            var window=minecraft.getWindow();double scale=window.getGuiScale();
            int px=(int)(x*scale),py=(int)(y*scale),pw=(int)(w*scale),ph=(int)(h*scale);
            var old=new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());
            com.mojang.blaze3d.systems.RenderSystem.viewport(px,window.getHeight()-py-ph,pw,ph);
            com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT,Minecraft.ON_OSX);
            var view=com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();view.pushPose();view.setIdentity();com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
            com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(new org.joml.Matrix4f().perspective((float)Math.toRadians(70),(float)pw/ph,.05f,100),com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);
            com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
            var pose=new com.mojang.blaze3d.vertex.PoseStack();var buffers=minecraft.renderBuffers().bufferSource();
            var root=new org.joml.Matrix4f(pose.last().pose());
            if(RAPIER){RapierAnimation.firstPerson(pose,true,0,Math.max(0,Math.min(1,t)));pose.pushPose();pose.scale(.8f,.8f,.8f);HandAxeAnimation.renderForearm(pose,buffers,15728880,model,skin,true,false,root);pose.popPose();pose.scale(1.3f,1.3f,1.3f);pose.translate(0,.65,0);minecraft.getItemRenderer().renderStatic(stack(),ItemDisplayContext.NONE,15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,pose,buffers,null,0);} else HandAxeAnimation.firstPerson(pose,true,t>1?0:t,t>1?1:0,0);
            if(!RAPIER)HandAxeAnimation.renderGrip(pose,buffers,15728880,stack(),model,skin,true,false,root,null,null,0);
            buffers.endBatch();
            com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(old,com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
            view.popPose();com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
            com.mojang.blaze3d.systems.RenderSystem.viewport(0,0,window.getWidth(),window.getHeight());
        }
    }
    private static final class AxeScreen extends Screen {
        AxeScreen(){super(net.minecraft.network.chat.Component.literal("Hand axe model verification"));}
        @Override public void render(GuiGraphics g,int mx,int my,float partial) {
            g.fill(0,0,width,height,0xff263747);
            var contexts=new ItemDisplayContext[]{ItemDisplayContext.FIXED,ItemDisplayContext.FIXED,ItemDisplayContext.GUI,
                    ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,ItemDisplayContext.FIRST_PERSON_LEFT_HAND,ItemDisplayContext.THIRD_PERSON_RIGHT_HAND};
            String[] labels={"Front","Rear","Inventory","First person R (model pose)","First person L (model pose)","Third person (model pose)"};
            for(int i=0;i<contexts.length;i++) {
                int col=i%3,row=i/3;float x=width*(col+.5f)/3,y=height*(row+.5f)/2;
                g.drawString(font,labels[i],(int)x-65,(int)(height*row/2f)+12,0xffffffff);
                g.flush();var pose=g.pose();pose.pushPose();pose.translate(x,y+12,150);pose.scale(85,-85,85);
                if(i==1)pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
                if(i>=3)pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(35));
                com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
                minecraft.getItemRenderer().renderStatic(stack(),contexts[i],15728880,
                        net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,pose,g.bufferSource(),null,0);
                g.flush();pose.popPose();
            }
        }
    }
}
