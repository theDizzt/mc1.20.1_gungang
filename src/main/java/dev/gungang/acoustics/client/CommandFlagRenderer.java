package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.gungang.acoustics.server.CommandFlagEntity;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public final class CommandFlagRenderer extends EntityRenderer<CommandFlagEntity> {
    private static final ResourceLocation TEXTURE=new ResourceLocation("minecraft","textures/block/white_wool.png");
    private static final ModelPart pole,cloth,trim,crown,base;
    public CommandFlagRenderer(EntityRendererProvider.Context context){
        super(context);shadowRadius=.4f;
    }
    static {
        var mesh=new MeshDefinition();var root=mesh.getRoot();
        root.addOrReplaceChild("pole",CubeListBuilder.create().texOffs(0,0)
                .addBox(-.8f,0,0,1.6f,40,1.6f).addBox(-6,37,-.7f,12,1.5f,1.5f),PartPose.ZERO);
        // Long hanging pennant, tapering to a point below the crown.
        var fabric=CubeListBuilder.create().texOffs(0,0).addBox(-5,19,-1,10,18,.5f);
        for(int i=0;i<5;i++)fabric.addBox(-5+i,18-i,-1,10-i*2,1,.5f);
        root.addOrReplaceChild("cloth",fabric,PartPose.ZERO);
        root.addOrReplaceChild("trim",CubeListBuilder.create().texOffs(0,0)
                .addBox(-4.5f,37,-.85f,9,1.5f,1.8f)
                .addBox(-3.6f,35.6f,-1.1f,7.2f,1,.7f)
                .addBox(-2.4f,32.7f,-1.1f,1.2f,3,.7f)
                .addBox(2,34,-1.1f,1.2f,2,.7f)
                .addBox(-3.5f,24,-1.1f,7,1,.7f),PartPose.ZERO);
        root.addOrReplaceChild("crown",CubeListBuilder.create().texOffs(0,0)
                .addBox(-3.5f,25,-1.18f,7,1.6f,.86f)
                .addBox(-3.5f,26.6f,-1.18f,1.4f,2.5f,.86f)
                .addBox(-.7f,26.6f,-1.18f,1.4f,2.5f,.86f)
                .addBox(2.1f,26.6f,-1.18f,1.4f,2.5f,.86f),PartPose.ZERO);
        root.addOrReplaceChild("base",CubeListBuilder.create().texOffs(0,0)
                .addBox(-5,0,-3,3,1.6f,3).addBox(1,0,1,4,1.4f,2.5f)
                .addBox(-2,0,-4,4,1,2).addBox(-3,0,2,2,1.8f,2),PartPose.ZERO);
        var model=LayerDefinition.create(mesh,16,16).bakeRoot();
        pole=model.getChild("pole");cloth=model.getChild("cloth");trim=model.getChild("trim");
        crown=model.getChild("crown");base=model.getChild("base");
    }
    @Override public void render(CommandFlagEntity flag,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        pose.pushPose();
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-flag.getYRot()));
        renderModel(pose,buffers,light,flag.color(),flag.teamName());
        pose.popPose();super.render(flag,yaw,partial,pose,buffers,light);
    }
    static void renderModel(PoseStack pose,MultiBufferSource buffers,int light,int color,String team){
        var vertex=buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        pole.render(pose,vertex,light,OverlayTexture.NO_OVERLAY,.48f,.28f,.2f,1);
        base.render(pose,vertex,light,OverlayTexture.NO_OVERLAY,.72f,.81f,.83f,1);
        int c=color;
        cloth.render(pose,vertex,light,OverlayTexture.NO_OVERLAY,(c>>16&255)/255f,(c>>8&255)/255f,(c&255)/255f,1);
        trim.render(pose,vertex,light,OverlayTexture.NO_OVERLAY,.83f,.93f,.94f,1);
        if(TeamEmblems.texture(team)==null)crown.render(pose,vertex,light,OverlayTexture.NO_OVERLAY,.98f,.77f,.17f,1);
        else {
            TeamEmblems.decal(pose,buffers,light,team,0,1.52f,-.076f,.53f,false);
            TeamEmblems.decal(pose,buffers,light,team,0,1.52f,-.018f,.53f,true);
        }
    }
    @Override public ResourceLocation getTextureLocation(CommandFlagEntity flag){return TEXTURE;}
}
