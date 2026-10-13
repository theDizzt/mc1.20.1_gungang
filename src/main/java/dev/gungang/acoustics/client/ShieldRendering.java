package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.CombatGadgets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Draw from the eye anchor, independent of hand swing, handedness and equip animation. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class ShieldRendering {
    // JSON glass center: (8, 24.73, 8.175); ItemRenderer centers the model on (8,8,8).
    public static final double WINDOW_Y=(24.73-8)/16;
    public static void poseArms(net.minecraft.client.model.HumanoidModel<?> model) {
        model.rightArm.xRot=model.leftArm.xRot=-1.15f+model.head.xRot*.5f;
        model.rightArm.yRot=model.head.yRot*.5f-.3f;
        model.leftArm.yRot=model.head.yRot*.5f+.3f;
        model.rightArm.zRot=model.leftArm.zRot=0;
    }
    public static void atEye(PoseStack pose,MultiBufferSource buffers,ItemStack stack,int light) {
        pose.pushPose();
        pose.translate(0,-WINDOW_Y,.65);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack,ItemDisplayContext.NONE,light,
                OverlayTexture.NO_OVERLAY,pose,buffers,Minecraft.getInstance().level,0);
        pose.popPose();
    }
    @SubscribeEvent public static void hand(RenderHandEvent event) {
        var player=Minecraft.getInstance().player;
        if(player==null)return;
        if(event.getHand()==InteractionHand.OFF_HAND&&(CombatGadgets.is(event.getItemStack(),"shield")||CombatGadgets.holdingShield(player))) {
            event.setCanceled(true);return; // Carried shield must not obscure the primary weapon.
        }
        if(event.getHand()!=InteractionHand.MAIN_HAND||!CombatGadgets.holdingShield(player))return;
        event.setCanceled(true);
        var pose=event.getPoseStack();pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180));
        atEye(pose,event.getMultiBufferSource(),player.getMainHandItem(),event.getPackedLight());
        pose.popPose();
    }
    private ShieldRendering() { }
}
