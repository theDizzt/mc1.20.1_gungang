package dev.gungang.acoustics.client;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.gungang.acoustics.server.PropEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EntityType;
final class PropObjectRenderer {
    static boolean render(PropEntity prop,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        var mc=Minecraft.getInstance();
        if(prop.item().equals("minecraft:minecart")||prop.item().equals("minecraft:end_crystal")){
            net.minecraft.world.entity.Entity object=prop.item().equals("minecraft:minecart")?EntityType.MINECART.create(prop.level()):EntityType.END_CRYSTAL.create(prop.level());
            object.setPos(prop.position());object.tickCount=prop.tickCount;
            if(object instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal crystal){crystal.time=prop.tickCount;crystal.setShowBottom(true);}
            mc.getEntityRenderDispatcher().render(object,0,0,0,prop.rotation()*90,partial,pose,buffers,prop.item().equals("minecraft:end_crystal")?LightTexture.pack(15,LightTexture.sky(light)):light);return true;
        }
        if(!prop.item().equals("gungang:mannequin"))return false;
        var info=mc.getConnection()==null?null:mc.getConnection().getPlayerInfo(prop.skin());boolean slim=info!=null&&info.getModelName().equals("slim");
        var model=new net.minecraft.client.model.PlayerModel<PropEntity>(mc.getEntityModels().bakeLayer(slim?net.minecraft.client.model.geom.ModelLayers.PLAYER_SLIM:net.minecraft.client.model.geom.ModelLayers.PLAYER),slim);
        var texture=MatchHud.skin(prop.skin().toString());
        pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees(180-prop.rotation()*90));pose.scale(-1,-1,1);pose.translate(0,-1.501,0);
        int mode=prop.mode();model.leftArm.zRot=mode==1?-1.57f:mode==2?-.8f:0;model.rightArm.zRot=mode==1?1.57f:mode==2?.8f:0;
        if(mode==3){model.leftArm.xRot=-2.6f;model.rightArm.xRot=-2.6f;}
        if(mode==4){model.leftLeg.xRot=-1.5f;model.rightLeg.xRot=-1.5f;model.leftArm.xRot=-.7f;model.rightArm.xRot=-.7f;}
        if(mode==5){model.rightArm.zRot=2.5f;model.head.zRot=.25f;}
        model.hat.copyFrom(model.head);model.jacket.copyFrom(model.body);model.leftSleeve.copyFrom(model.leftArm);model.rightSleeve.copyFrom(model.rightArm);model.leftPants.copyFrom(model.leftLeg);model.rightPants.copyFrom(model.rightLeg);
        model.renderToBuffer(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(texture)),light,OverlayTexture.NO_OVERLAY,1,1,1,1);pose.popPose();return true;
    }
    private PropObjectRenderer(){}
}
