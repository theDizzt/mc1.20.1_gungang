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
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void hand(net.minecraftforge.client.event.RenderHandEvent event){
        if(!(event.getItemStack().getItem() instanceof dev.gungang.acoustics.server.RapierItem))return;
        var mc=net.minecraft.client.Minecraft.getInstance();var player=mc.player;if(player==null)return;
        boolean right=(event.getHand()==net.minecraft.world.InteractionHand.MAIN_HAND)==(player.getMainArm()==HumanoidArm.RIGHT);
        var pose=event.getPoseStack();pose.pushPose();var root=new org.joml.Matrix4f(pose.last().pose());
        float sweep=sweepProgress(event.getItemStack(),player,event.getPartialTick());
        firstPerson(pose,right,event.getSwingProgress()>0||sweep>=0?0:event.getEquipProgress(),event.getSwingProgress(),sweep);
        pose.pushPose();pose.scale(.8f,.8f,.8f);
        if(mc.getEntityRenderDispatcher().getRenderer(player) instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer renderer)
            HandAxeAnimation.renderForearm(pose,event.getMultiBufferSource(),event.getPackedLight(),renderer.getModel(),player.getSkinTextureLocation(),right,"slim".equals(player.getModelName()),root);
        pose.popPose();
        pose.scale(1.3f,1.3f,1.3f);pose.translate(0,.65,0); // hilt centre at the fist; bypass item display transforms that offset the blade
        mc.getItemRenderer().renderStatic(player,event.getItemStack(),net.minecraft.world.item.ItemDisplayContext.NONE,!right,pose,event.getMultiBufferSource(),player.level(),event.getPackedLight(),net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,player.getId());
        pose.popPose();event.setCanceled(true);
    }
    public static void poseArm(net.minecraft.client.model.HumanoidModel<?> model,net.minecraft.world.entity.LivingEntity entity){
        if(!(entity.getMainHandItem().getItem() instanceof dev.gungang.acoustics.server.RapierItem))return;
        boolean right=entity.getMainArm()==HumanoidArm.RIGHT;
        float sweep=sweepProgress(entity.getMainHandItem(),entity,net.minecraft.client.Minecraft.getInstance().getFrameTime());
        poseArm(model,right,sweep>=0?sweep:model.attackTime,sweep>=0,entity.getOffhandItem().isEmpty());
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
            float active=Math.max(0,-p.armPitch()-.35f);
            balance.xRot=-.15f-.25f*active;balance.zRot=-side*(.08f+.24f*active);
            balance.yRot=-side*.15f;
            balance.x=shoulderX;balance.z=-shoulderZ;
        }
    }
    @Override public boolean applyForgeHandTransform(PoseStack pose,LocalPlayer player,HumanoidArm arm,ItemStack stack,float partial,float equip,float swing){
        float sweep=sweepProgress(stack,player,partial);
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
