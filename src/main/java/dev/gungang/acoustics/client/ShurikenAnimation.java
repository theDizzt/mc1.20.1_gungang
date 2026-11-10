package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.ShurikenItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * First person 33 shuriken: the bare forearm holds a star up between the fingers. A throw cocks the wrist back, flicks
 * it forward (the star leaves the hand) and a fresh star is drawn back in; a reload drops the hand out of view and
 * brings it back spinning a new star; a wall-climb step reaches up. The off-hand secondary is not drawn meanwhile
 * (third person: {@link dev.gungang.acoustics.mixin.RapierOffhandLayerMixin}).
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class ShurikenAnimation {
    /** Fist poses in camera space for the right hand: x, y, z, pitch, yaw, roll (mirrored for the left). */
    private static final float[] READY={.38f,-.42f,-.72f,8,12,-10},WIND={.30f,-.26f,-.52f,-28,26,-34},RELEASE={.16f,-.32f,-1.05f,38,-6,12},
            LOW={.52f,-1.10f,-.55f,42,22,-12},REACH={.40f,-.12f,-.80f,-42,8,-4};
    private static final int THROW_TICKS=7,CLIMB_TICKS=5;
    /** Star size in hand and where it sits above the fist (fist units). */
    private static final float SCALE=.42f,LIFT=.17f,FACE_YAW=22;
    private static long thrownAt=Long.MIN_VALUE/2,climbedAt=Long.MIN_VALUE/2;

    static void thrown(){var level=Minecraft.getInstance().level;if(level!=null)thrownAt=level.getGameTime();}
    static void climbed(){var level=Minecraft.getInstance().level;if(level!=null)climbedAt=level.getGameTime();}

    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void offhand(RenderHandEvent event){
        var player=Minecraft.getInstance().player;
        if(event.getHand()==InteractionHand.OFF_HAND&&player!=null&&ShurikenItem.is(player.getMainHandItem()))event.setCanceled(true);
    }
    @SubscribeEvent public static void hand(RenderHandEvent event){
        if(event.getHand()!=InteractionHand.MAIN_HAND||!ShurikenItem.is(event.getItemStack()))return;
        var mc=Minecraft.getInstance();var player=mc.player;if(player==null||mc.level==null)return;
        boolean right=player.getMainArm()==HumanoidArm.RIGHT;
        float partial=event.getPartialTick();double now=mc.level.getGameTime()+partial;
        var stack=event.getItemStack();
        float[] p=READY.clone();boolean star=true;float spin=0,grow=1;
        long reloadEnd=ShurikenItem.reloadEnd(stack);
        if(reloadEnd>0){
            float r=Mth.clamp(1-(float)((reloadEnd-now)/ShurikenItem.reloadTicks(stack)),0,1);
            if(r<.3f)blend(p,LOW,smooth(r/.3f));
            else if(r<.6f)set(p,LOW);
            else{set(p,LOW);blend(p,READY,smooth((r-.6f)/.4f));spin=360*(1-smooth((r-.6f)/.4f));}
            star=r<.25f||r>=.6f;
        }else{
            float t=(float)((now-thrownAt)/THROW_TICKS);
            if(t>=0&&t<1){
                if(t<.25f)blend(p,WIND,smooth(t/.25f));
                else if(t<.45f){set(p,WIND);blend(p,RELEASE,smooth((t-.25f)/.2f));}
                else{set(p,RELEASE);blend(p,READY,smooth((t-.45f)/.55f));}
                // Released at the snap; a new star slides between the fingers on the way back.
                star=t<.35f||t>=.75f;
                if(t>=.75f)grow=smooth((t-.75f)/.25f);
            }
            float c=(float)((now-climbedAt)/CLIMB_TICKS);
            if(c>=0&&c<1)blend(p,REACH,(float)Math.sin(c*Math.PI));
        }
        // A slow breath while idle, and the usual lowering while switching to it.
        float breath=(float)Math.sin((player.tickCount+partial)*.08f);
        p[1]+=breath*.006f-.6f*event.getEquipProgress();
        var pose=event.getPoseStack();pose.pushPose();
        var root=new Matrix4f(pose.last().pose());
        int side=right?1:-1;
        pose.translate(side*p[0],p[1],p[2]);
        pose.mulPose(Axis.YP.rotationDegrees(side*p[4]));
        pose.mulPose(Axis.XP.rotationDegrees(p[3]));
        pose.mulPose(Axis.ZP.rotationDegrees(side*p[5]));
        if(mc.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)
            HandAxeAnimation.renderForearm(pose,event.getMultiBufferSource(),event.getPackedLight(),renderer.getModel(),player.getSkinTextureLocation(),right,"slim".equals(player.getModelName()),root);
        if(star&&grow>0){
            pose.pushPose();
            pose.translate(0,LIFT,0);
            pose.mulPose(Axis.YP.rotationDegrees(side*FACE_YAW));
            pose.mulPose(Axis.ZP.rotationDegrees(side*spin));
            // The item renderer centres the one-block model on this point itself.
            float scale=SCALE*grow;pose.scale(scale,scale,scale);
            mc.getItemRenderer().renderStatic(player,stack,ItemDisplayContext.NONE,!right,pose,event.getMultiBufferSource(),player.level(),event.getPackedLight(),OverlayTexture.NO_OVERLAY,player.getId());
            pose.popPose();
        }
        pose.popPose();event.setCanceled(true);
    }
    private static void set(float[] to,float[] from){System.arraycopy(from,0,to,0,6);}
    private static void blend(float[] p,float[] target,float t){for(int i=0;i<6;i++)p[i]=Mth.lerp(t,p[i],target[i]);}
    private static float smooth(float t){t=Mth.clamp(t,0,1);return t*t*(3-2*t);}
    private ShurikenAnimation(){}
}
