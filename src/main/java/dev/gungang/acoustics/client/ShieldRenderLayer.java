package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.gungang.acoustics.server.CombatGadgets;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

public final class ShieldRenderLayer extends RenderLayer<AbstractClientPlayer,PlayerModel<AbstractClientPlayer>> {
    public ShieldRenderLayer(RenderLayerParent<AbstractClientPlayer,PlayerModel<AbstractClientPlayer>> parent) {super(parent);}
    @Override public void render(PoseStack pose,MultiBufferSource buffers,int light,AbstractClientPlayer player,
            float swing,float amount,float partial,float age,float yaw,float pitch) {
        if(!CombatGadgets.holdingShield(player)||player.isInvisible())return;
        pose.pushPose();
        // Head transform follows gaze (including crouching), instead of the swinging arm.
        getParentModel().head.translateAndRotate(pose);
        pose.translate(0,-.25,0);
        pose.scale(1,-1,-1);
        ShieldRendering.atEye(pose,buffers,player.getMainHandItem(),light);
        pose.popPose();
    }
}
