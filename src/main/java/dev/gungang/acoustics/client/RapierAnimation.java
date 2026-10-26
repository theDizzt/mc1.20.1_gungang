package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/** Replace the broad vanilla slash with a short draw-back, straight lunge and recovery. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID,value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class RapierAnimation implements IClientItemExtensions {
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void hand(net.minecraftforge.client.event.RenderHandEvent event){
        if(!(event.getItemStack().getItem() instanceof dev.gungang.acoustics.server.RapierItem))return;
        var mc=net.minecraft.client.Minecraft.getInstance();var player=mc.player;if(player==null)return;
        boolean right=(event.getHand()==net.minecraft.world.InteractionHand.MAIN_HAND)==(player.getMainArm()==HumanoidArm.RIGHT);
        var pose=event.getPoseStack();pose.pushPose();var root=new org.joml.Matrix4f(pose.last().pose());
        firstPerson(pose,right,event.getSwingProgress()>0?0:event.getEquipProgress(),event.getSwingProgress());
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
        var arm=right?model.rightArm:model.leftArm;
        float thrust=thrust(model.attackTime);
        arm.xRot=-.35f-.8f*thrust+model.head.xRot*.35f;
        arm.yRot=(right?-.12f:.12f)*(1-thrust);arm.zRot=0;
        model.body.yRot=0;
    }
    @Override public boolean applyForgeHandTransform(PoseStack pose,LocalPlayer player,HumanoidArm arm,ItemStack stack,float partial,float equip,float swing){
        firstPerson(pose,arm==HumanoidArm.RIGHT,equip,swing);return true;
    }
    public static void firstPerson(PoseStack pose,boolean right,float equip,float swing){
        int side=right?1:-1;
        float thrust=thrust(swing);
        // Keep the fist below/right (mirrored for left hand), so it cannot hide the thrusting tip.
        pose.translate(side*(.48-.32*thrust),-.38+.26*thrust-equip*.5,-.95-.55*thrust);
        pose.mulPose(Axis.XP.rotationDegrees(-35-51*thrust));
        pose.mulPose(Axis.ZP.rotationDegrees(side*(-8+13*thrust)));
    }
    private static float thrust(float swing){return swing<.15f?smooth(swing/.15f):swing<=.25f?1:1-smooth((swing-.25f)/.75f);}
    private static float smooth(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
}
