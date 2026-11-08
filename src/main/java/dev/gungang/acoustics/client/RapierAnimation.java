package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/** Separate straight thrust and dash blade-beat poses, mirrored for either dominant hand. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID,value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class RapierAnimation implements IClientItemExtensions {
    private static LocalPlayer stancePlayer;
    private static float sprint,oldSprint,crouch,oldCrouch,yawLag,oldYawLag,pitchLag,oldPitchLag;
    private static float lastYaw,lastPitch;

    /** Tick-based inertia keeps stance transitions independent of render frame rate. */
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void stance(net.minecraftforge.event.TickEvent.ClientTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        var mc=net.minecraft.client.Minecraft.getInstance();var player=mc.player;
        boolean holding=player!=null&&(player.getMainHandItem().getItem() instanceof dev.gungang.acoustics.server.RapierItem
                ||player.getOffhandItem().getItem() instanceof dev.gungang.acoustics.server.RapierItem);
        if(!holding||player!=stancePlayer){
            stancePlayer=holding?player:null;
            sprint=oldSprint=crouch=oldCrouch=yawLag=oldYawLag=pitchLag=oldPitchLag=0;
            if(player!=null){lastYaw=player.getYRot();lastPitch=player.getXRot();}
            return;
        }
        if(mc.isPaused())return;
        oldSprint=sprint;oldCrouch=crouch;oldYawLag=yawLag;oldPitchLag=pitchLag;
        boolean running=player.isSprinting()&&player.onGround()&&!player.isSwimming()&&!player.isFallFlying();
        sprint+=(running?1-sprint:-sprint)*.3f;
        crouch+=(player.isCrouching()?1-crouch:-crouch)*.35f;
        float yaw=net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(player.getYRot()-lastYaw),-10,10);
        float pitch=net.minecraft.util.Mth.clamp(player.getXRot()-lastPitch,-8,8);
        lastYaw=player.getYRot();lastPitch=player.getXRot();
        yawLag+=(yaw-yawLag)*.35f;pitchLag+=(pitch-pitchLag)*.35f;
    }
    /** First person: the off-hand secondary is not drawn while the rapier is wielded (RapierOffhandLayerMixin does third person). */
    @net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST) public static void offhand(net.minecraftforge.client.event.RenderHandEvent event){
        var player=net.minecraft.client.Minecraft.getInstance().player;
        if(event.getHand()==net.minecraft.world.InteractionHand.OFF_HAND&&player!=null&&player.getMainHandItem().getItem() instanceof dev.gungang.acoustics.server.RapierItem)event.setCanceled(true);
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void hand(net.minecraftforge.client.event.RenderHandEvent event){
        if(!(event.getItemStack().getItem() instanceof dev.gungang.acoustics.server.RapierItem))return;
        var mc=net.minecraft.client.Minecraft.getInstance();var player=mc.player;if(player==null)return;
        boolean right=(event.getHand()==net.minecraft.world.InteractionHand.MAIN_HAND)==(player.getMainArm()==HumanoidArm.RIGHT);
        var pose=event.getPoseStack();pose.pushPose();var root=new org.joml.Matrix4f(pose.last().pose());
        float sweep=sweepProgress(event.getItemStack(),player,event.getPartialTick());
        stanceTransform(pose,player,right,event.getPartialTick(),event.getSwingProgress(),sweep);
        firstPerson(pose,right,event.getSwingProgress()>0||sweep>=0?0:event.getEquipProgress(),event.getSwingProgress(),sweep);
        pose.pushPose();pose.scale(.8f,.8f,.8f);
        if(mc.getEntityRenderDispatcher().getRenderer(player) instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer renderer)
            HandAxeAnimation.renderForearm(pose,event.getMultiBufferSource(),event.getPackedLight(),renderer.getModel(),player.getSkinTextureLocation(),right,"slim".equals(player.getModelName()),root);
        pose.popPose();
        pose.scale(1.3f,1.3f,1.3f);pose.translate(0,.65,0); // hilt centre at the fist; bypass item display transforms that offset the blade
        mc.getItemRenderer().renderStatic(player,event.getItemStack(),net.minecraft.world.item.ItemDisplayContext.NONE,!right,pose,event.getMultiBufferSource(),player.level(),event.getPackedLight(),net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,player.getId());
        pose.popPose();event.setCanceled(true);
    }
    /** Action-film camera kick on the strike (first person only): a nod on the thrust, a tilt along the sweep. */
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void camera(net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles event){
        var mc=net.minecraft.client.Minecraft.getInstance();var player=mc.player;
        if(player==null||!mc.options.getCameraType().isFirstPerson()||!(player.getMainHandItem().getItem() instanceof dev.gungang.acoustics.server.RapierItem))return;
        float partial=(float)event.getPartialTick();
        float sweep=sweepProgress(player.getMainHandItem(),player,partial);
        boolean swept=sweep>=0;float k=RapierMotion.kick(swept,swept?sweep:player.getAttackAnim(partial));
        if(k<=0)return;
        int side=player.getMainArm()==HumanoidArm.RIGHT?1:-1;
        event.setPitch(event.getPitch()+(swept?.4f:1.1f)*k);
        event.setRoll(event.getRoll()+side*(swept?-2.2f:1.4f)*k);
        if(swept)event.setYaw(event.getYaw()-side*.8f*k);
    }
    public static void poseArm(net.minecraft.client.model.HumanoidModel<?> model,net.minecraft.world.entity.LivingEntity entity){
        if(!(entity.getMainHandItem().getItem() instanceof dev.gungang.acoustics.server.RapierItem))return;
        boolean right=entity.getMainArm()==HumanoidArm.RIGHT;
        float sweep=sweepProgress(entity.getMainHandItem(),entity,net.minecraft.client.Minecraft.getInstance().getFrameTime());
        // The off-hand item is hidden while the rapier is wielded (RapierOffhandLayerMixin), so that hand always balances.
        poseArm(model,right,sweep>=0?sweep:model.attackTime,sweep>=0,true);
    }
    public static void poseArm(net.minecraft.client.model.HumanoidModel<?> model,boolean right,float progress,boolean sweep,boolean balanceFree){
        var arm=right?model.rightArm:model.leftArm;
        int side=right?1:-1;
        var p=RapierMotion.sample(sweep,progress);
        arm.xRot=p.armPitch()+model.head.xRot*.35f;
        arm.yRot=side*p.armYaw();arm.zRot=side*p.armRoll();
        model.body.yRot=side*p.bodyYaw();model.body.xRot+=p.lean();
        // Replace the vanilla slash's shoulder orbit as well as its rotations.
        float shoulderX=side*5*(float)Math.cos(model.body.yRot),shoulderZ=side*5*(float)Math.sin(model.body.yRot);
        arm.x=-shoulderX;arm.z=shoulderZ;
        if(balanceFree){
            var balance=right?model.leftArm:model.rightArm;
            float active=Math.max(0,-p.armPitch()-.82f);
            // Free hand balances behind the bladed torso, opening as the sword arm extends.
            balance.xRot=.20f-.18f*active;balance.zRot=-side*(.22f+.24f*active);
            balance.yRot=-side*.32f;
            balance.x=shoulderX;balance.z=-shoulderZ;
        }
    }
    @Override public boolean applyForgeHandTransform(PoseStack pose,LocalPlayer player,HumanoidArm arm,ItemStack stack,float partial,float equip,float swing){
        float sweep=sweepProgress(stack,player,partial);
        stanceTransform(pose,player,arm==HumanoidArm.RIGHT,partial,swing,sweep);
        firstPerson(pose,arm==HumanoidArm.RIGHT,swing>0||sweep>=0?0:equip,swing,sweep);return true;
    }
    public static void firstPerson(PoseStack pose,boolean right,float equip,float swing){
        firstPerson(pose,right,equip,swing,-1);
    }
    public static void firstPerson(PoseStack pose,boolean right,float equip,float swing,float sweep){
        int side=right?1:-1;
        var p=RapierMotion.sample(sweep>=0,sweep>=0?sweep:swing);
        pose.translate(side*p.x(),p.y()-equip*.5,p.z());
        pose.mulPose(Axis.YP.rotationDegrees(side*p.yaw()));
        pose.mulPose(Axis.XP.rotationDegrees(p.pitch()));
        pose.mulPose(Axis.ZP.rotationDegrees(side*p.roll()));
    }
    /** Small camera-space offsets keep the point readable without changing attack timing or aim. */
    private static void stanceTransform(PoseStack pose,LocalPlayer player,boolean right,float partial,float swing,float sweep){
        if(player!=stancePlayer)return;
        int side=right?1:-1;
        float t=sweep>=0?sweep:swing;
        // Fade ambient motion out during wind-up and bring it back only at the end of recovery.
        float leave=net.minecraft.util.Mth.clamp(t/.12f,0,1);
        float enter=net.minecraft.util.Mth.clamp((t-.80f)/.20f,0,1);
        float weight=1-leave*leave*(3-2*leave)+enter*enter*(3-2*enter);
        float run=net.minecraft.util.Mth.lerp(partial,oldSprint,sprint)*weight;
        float low=net.minecraft.util.Mth.lerp(partial,oldCrouch,crouch)*weight;
        float yaw=net.minecraft.util.Mth.lerp(partial,oldYawLag,yawLag)*weight;
        float pitch=net.minecraft.util.Mth.lerp(partial,oldPitchLag,pitchLag)*weight;
        float age=player.tickCount+partial;
        float breath=(float)Math.sin(age*.085f)*weight*(1-run*.7f);
        float stride=player.walkAnimation.position(partial);
        float movement=player.onGround()?Math.min(1,player.walkAnimation.speed(partial))*weight:0;
        pose.translate(side*(run*.075f+breath*.004f)-yaw*.0015f,
                -run*.10f+low*.035f+breath*.006f+Math.cos(stride*1.4f)*movement*.008f,
                run*.10f-low*.025f);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw*.22f));
        pose.mulPose(Axis.XP.rotationDegrees(run*14-low*3-pitch*.18f));
        pose.mulPose(Axis.ZP.rotationDegrees(side*(-run*12+breath*.45f)+yaw*.14f));
    }
    private static float sweepProgress(ItemStack stack,net.minecraft.world.entity.LivingEntity entity,float partial){
        var tag=stack.getTag();
        if(tag==null||!tag.contains(dev.gungang.acoustics.server.RapierItem.SWEEP_ANIMATION))return -1;
        float elapsed=entity.level().getGameTime()-tag.getLong(dev.gungang.acoustics.server.RapierItem.SWEEP_ANIMATION)+partial;
        return elapsed>=0&&elapsed<dev.gungang.acoustics.server.RapierItem.SWEEP_ANIMATION_TICKS
                ?elapsed/dev.gungang.acoustics.server.RapierItem.SWEEP_ANIMATION_TICKS:-1;
    }
    public static float wrist(net.minecraft.client.model.HumanoidModel<?> model,net.minecraft.world.entity.LivingEntity entity,HumanoidArm arm){
        if(arm!=entity.getMainArm()||!(entity.getMainHandItem().getItem() instanceof dev.gungang.acoustics.server.RapierItem))return 0;
        float sweep=sweepProgress(entity.getMainHandItem(),entity,net.minecraft.client.Minecraft.getInstance().getFrameTime());
        return RapierMotion.wrist(sweep>=0,sweep>=0?sweep:model.attackTime);
    }
}
