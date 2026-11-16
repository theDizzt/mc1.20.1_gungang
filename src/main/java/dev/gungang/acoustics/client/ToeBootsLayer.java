package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.gungang.acoustics.server.ToeItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;

public final class ToeBootsLayer<T extends LivingEntity,M extends HumanoidModel<T>> extends RenderLayer<T,M> {
    public ToeBootsLayer(RenderLayerParent<T,M> parent){super(parent);}
    @Override public void render(PoseStack pose,MultiBufferSource buffers,int light,T entity,float swing,float amount,float partial,float age,float yaw,float pitch){
        var stack=ToeItem.carried(entity);if(stack.isEmpty()||entity.isInvisible())return;
        for(var leg:java.util.List.of(getParentModel().leftLeg,getParentModel().rightLeg)){
            if(!leg.visible)continue;
            pose.pushPose();leg.translateAndRotate(pose);pose.translate(0,10/16.0,0);
            pose.mulPose(Axis.XP.rotationDegrees(180));
            Minecraft.getInstance().getItemRenderer().renderStatic(entity,stack,ItemDisplayContext.NONE,false,pose,buffers,entity.level(),light,OverlayTexture.NO_OVERLAY,entity.getId());
            pose.popPose();
        }
    }
}
