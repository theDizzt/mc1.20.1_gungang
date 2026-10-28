package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.HandAxeItem;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Throwing hatchet carry and charge poses. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class HandAxeAnimation {
    private static final float[] READY={.48f,-.46f,-.82f,-10,28,-12};
    private static final float[] THROW={.45f,-.20f,-.90f,30,40,-25};
    /** Carried in the offhand beside a gun: lower and further out than the ready stance so it stays out of the sights. */
    private static final float[] CARRY={.64f,-.72f,-1.02f,-6,40,-6};
    /** Where the forearm comes from (camera space, right hand), well below the bottom-right of the view. */
    private static final float[] ELBOW={.70f,-1.25f,-.25f};
    /** Grip point on the OBJ handle (model units), the model scale in hand, and the turn that faces the blade forward. */
    private static final float GRIP_Y=.2f,SCALE=1.1f,BLADE_YAW=-90;

    @SubscribeEvent public static void hand(RenderHandEvent event) {
        if(!HandAxeItem.is(event.getItemStack()))return;
        var mc=Minecraft.getInstance();var player=mc.player;if(player==null)return;
        boolean right=(event.getHand()==InteractionHand.MAIN_HAND)==(player.getMainArm()==HumanoidArm.RIGHT);
        boolean charging=player.isUsingItem()&&player.getUsedItemHand()==event.getHand();
        float charge=charging?Mth.clamp((player.getTicksUsingItem()+event.getPartialTick())/10f,0,1):0;
        var pose=event.getPoseStack();pose.pushPose();
        var root=new Matrix4f(pose.last().pose());
        float swing=0;
        boolean carry=event.getHand()==InteractionHand.OFF_HAND&&swing<=0&&!charging;
        firstPerson(pose,right,carry?-1:swing,charge,swing>0?0:event.getEquipProgress());
        PlayerModel<AbstractClientPlayer> model=mc.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer?renderer.getModel():null;
        renderGrip(pose,event.getMultiBufferSource(),event.getPackedLight(),event.getItemStack(),model,player.getSkinTextureLocation(),
                right,"slim".equals(player.getModelName()),root,player,player.level(),player.getId());
        pose.popPose();event.setCanceled(true);
    }

    /** Moves the pose to the fist: handle along +Y, blade toward -Z, forearm back along +Z. A negative swing means carried. */
    public static void firstPerson(PoseStack pose,boolean right,float swing,float charge,float equip) {
        float[] p=swing<0?CARRY.clone():READY.clone();
        float c=smooth(charge);
        for(int i=0;i<6;i++)p[i]=Mth.lerp(c,p[i],THROW[i]);
        int side=right?1:-1;
        pose.translate(side*p[0],p[1]-.6f*equip,p[2]);
        pose.mulPose(Axis.YP.rotationDegrees(side*p[4]));
        pose.mulPose(Axis.XP.rotationDegrees(p[3]));
        pose.mulPose(Axis.ZP.rotationDegrees(side*p[5]));
    }

    /** Draws the forearm and the axe held in it at the current fist pose; root is the pose before {@link #firstPerson}. */
    public static void renderGrip(PoseStack pose,MultiBufferSource buffers,int light,ItemStack stack,@Nullable PlayerModel<?> model,ResourceLocation skin,
                                  boolean right,boolean slim,Matrix4f root,@Nullable LivingEntity holder,@Nullable Level level,int seed) {
        if(model!=null)renderForearm(pose,buffers,light,model,skin,right,slim,root);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(BLADE_YAW));
        pose.scale(SCALE,SCALE,SCALE);
        pose.translate(0,.5f-GRIP_Y,0);
        var items=Minecraft.getInstance().getItemRenderer();
        if(holder!=null)items.renderStatic(holder,stack,ItemDisplayContext.NONE,!right,pose,buffers,level,light,OverlayTexture.NO_OVERLAY,seed);
        else items.renderStatic(stack,ItemDisplayContext.NONE,light,OverlayTexture.NO_OVERLAY,pose,buffers,level,seed);
        pose.popPose();
    }

    public static void renderForearm(PoseStack pose,MultiBufferSource buffers,int light,PlayerModel<?> model,ResourceLocation skin,boolean right,boolean slim,Matrix4f root) {
        var arm=right?model.rightArm:model.leftArm;var sleeve=right?model.rightSleeve:model.leftSleeve;
        PartPose armPose=arm.storePose(),sleevePose=sleeve.storePose();
        arm.loadPose(PartPose.ZERO);sleeve.loadPose(PartPose.ZERO);
        // Aim the arm from a fixed elbow below the view to the fist, whatever the wrist is doing.
        var elbow=root.transformPosition(new Vector3f((right?1:-1)*ELBOW[0],ELBOW[1],ELBOW[2]));
        new Matrix4f(pose.last().pose()).invert().transformPosition(elbow);
        float reach=elbow.length();
        elbow.div(-reach);
        pose.pushPose();
        // The arm box runs shoulder (y -2) to hand (y 10); +Y points at the fist (y 8), stretched to reach the elbow.
        pose.mulPose(new Quaternionf().rotationTo(0,1,0,elbow.x,elbow.y,elbow.z));
        pose.scale(1,Mth.clamp(reach/(10/16f),.9f,1.15f),1);
        pose.translate(-(right?-1:1)*(slim?.5f:1f)/16f,-8/16f,0);
        arm.render(pose,buffers.getBuffer(RenderType.entitySolid(skin)),light,OverlayTexture.NO_OVERLAY);
        sleeve.render(pose,buffers.getBuffer(RenderType.entityTranslucent(skin)),light,OverlayTexture.NO_OVERLAY);
        pose.popPose();
        arm.loadPose(armPose);sleeve.loadPose(sleevePose);
    }

    public static void poseArm(HumanoidModel<?> model,LivingEntity entity) {
        var hand=entity.swingingArm==null?InteractionHand.MAIN_HAND:entity.swingingArm;
        boolean charging=entity.isUsingItem()&&HandAxeItem.is(entity.getUseItem());
        if(charging)hand=entity.getUsedItemHand();
        if(!charging)return;
        poseArm(model,(hand==InteractionHand.MAIN_HAND)==(entity.getMainArm()==HumanoidArm.RIGHT),model.attackTime,charging);
    }

    public static void poseArm(HumanoidModel<?> model,boolean right,float t,boolean charging) {
        if(!charging)return;
        int side=right?1:-1;
        var arm=right?model.rightArm:model.leftArm;
        float[] p=new float[]{-2.75f,.28f,-.22f};
        float turn=side*p[2]-model.body.yRot;
        model.body.yRot+=turn;
        // Keep the shoulders on the turned body, as vanilla's attack animation does.
        model.rightArm.z=Mth.sin(model.body.yRot)*5;model.rightArm.x=-Mth.cos(model.body.yRot)*5;
        model.leftArm.z=-Mth.sin(model.body.yRot)*5;model.leftArm.x=Mth.cos(model.body.yRot)*5;
        arm.xRot=p[0];
        arm.yRot=side*-.1f+model.body.yRot;
        arm.zRot=side*p[1];
        (right?model.leftArm:model.rightArm).yRot+=turn;
    }

    /** Extra wrist turn while charging a throw. */
    public static float wrist(HumanoidModel<?> model,LivingEntity entity,HumanoidArm side) {
        if(entity.isUsingItem()&&HandAxeItem.is(entity.getUseItem()))return entity.getUsedItemHand()==handOn(entity,side)?35:0;
        return 0;
    }
    public static float wrist(float t) {return 0;}
    private static InteractionHand handOn(LivingEntity entity,HumanoidArm side) {return side==entity.getMainArm()?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND;}

    private static float smooth(float t) {t=Mth.clamp(t,0,1);return t*t*(3-2*t);}
    private HandAxeAnimation() { }
}
