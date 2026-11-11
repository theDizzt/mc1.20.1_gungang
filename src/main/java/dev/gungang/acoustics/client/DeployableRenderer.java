package dev.gungang.acoustics.client;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.gungang.acoustics.server.DeployableEntity;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;

public final class DeployableRenderer extends EntityRenderer<DeployableEntity> {
    private final net.minecraft.world.item.ItemStack mine=new net.minecraft.world.item.ItemStack(dev.gungang.acoustics.server.GadgetItems.LANDMINE.get());
    private final net.minecraft.client.renderer.block.BlockRenderDispatcher blocks;
    public DeployableRenderer(EntityRendererProvider.Context context) {super(context);blocks=context.getBlockRenderDispatcher();}
    @Override public void render(DeployableEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        pose.pushPose();
        if(entity.wall()) {
            if(entity.east())pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90));
            wall(pose,buffers,light);
            TeamEmblems.decal(pose,buffers,light,entity.teamName(),0,2.12f,-.205f,.65f,false);
            TeamEmblems.decal(pose,buffers,light,entity.teamName(),0,2.12f,.205f,.65f,true);
        } else {
            if(entity.mineOutlineVisibleTo(net.minecraft.client.Minecraft.getInstance().player)){
                pose.pushPose();pose.translate(0,.155,0);pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));
                TeamEmblems.decal(pose,buffers,light,entity.teamName(),0,-.12f,0,.24f,false);pose.popPose();
            }
            // OBJ already matches the old installed plate's 0.6125-block footprint, with its bottom at Y=0.
            pose.translate(-.5,0,-.5);
            var model=net.minecraft.client.Minecraft.getInstance().getItemRenderer().getModel(mine,entity.level(),null,0);
            blocks.getModelRenderer().renderModel(pose.last(),buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS)),null,model,1,1,1,light,OverlayTexture.NO_OVERLAY);
        }
        pose.popPose();
        // Enemy mines must not reveal their team through the ordinary colored frame either.
        if(entity.wall()||entity.mineOutlineVisibleTo(net.minecraft.client.Minecraft.getInstance().player)) {
            int color=entity.teamColor();
            var box=entity.getBoundingBox().move(-entity.getX(),-entity.getY(),-entity.getZ()).inflate(.015);
            LevelRenderer.renderLineBox(pose,buffers.getBuffer(RenderType.lines()),box,
                    (color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,1);
        }
        super.render(entity,yaw,partial,pose,buffers,light);
    }
    private static final ResourceLocation PLANKS=new ResourceLocation("gungang","textures/entity/barrier_planks.png");
    private static final ResourceLocation SIGN=new ResourceLocation("gungang","textures/entity/barrier_sign.png");
    /** Half thickness of the wall (its hitbox) and how far the no-entry sign stands off each face. */
    private static final float HALF=.18f,SIGN_OFFSET=.02f;
    /**
     * The 3x3 wall along local X (rotated for east-facing walls): weathered boards on a 3-block texture, edges cut
     * from the same boards, and a slightly raised no-entry sign on both faces (scripts/make_barrier_resources.py).
     */
    private static void wall(PoseStack pose,MultiBufferSource buffers,int light) {
        var m=pose.last().pose();var n=pose.last().normal();
        var vc=buffers.getBuffer(RenderType.entityCutoutNoCull(PLANKS));
        float t=HALF*2/3; // board texture share matching the wall's thickness
        quad(vc,m,n,light,-1.5f,0,-HALF, 1.5f,0,-HALF, 1.5f,3,-HALF, -1.5f,3,-HALF, 1,1,0,0,0,0,-1);  // front
        quad(vc,m,n,light,1.5f,0,HALF, -1.5f,0,HALF, -1.5f,3,HALF, 1.5f,3,HALF, 1,1,0,0,0,0,1);      // back
        quad(vc,m,n,light,-1.5f,3,-HALF, 1.5f,3,-HALF, 1.5f,3,HALF, -1.5f,3,HALF, 0,t,1,0,0,1,0);    // top
        quad(vc,m,n,light,-1.5f,0,HALF, 1.5f,0,HALF, 1.5f,0,-HALF, -1.5f,0,-HALF, 0,1,1,1-t,0,-1,0); // bottom
        quad(vc,m,n,light,1.5f,0,-HALF, 1.5f,0,HALF, 1.5f,3,HALF, 1.5f,3,-HALF, 1-t,1,1,0,1,0,0);    // ends
        quad(vc,m,n,light,-1.5f,0,HALF, -1.5f,0,-HALF, -1.5f,3,-HALF, -1.5f,3,HALF, 0,1,t,0,-1,0,0);
        vc=buffers.getBuffer(RenderType.entityCutoutNoCull(SIGN));
        float f=-HALF-SIGN_OFFSET,b=HALF+SIGN_OFFSET;
        quad(vc,m,n,light,-1.5f,0,f, 1.5f,0,f, 1.5f,3,f, -1.5f,3,f, 1,1,0,0,0,0,-1);
        quad(vc,m,n,light,1.5f,0,b, -1.5f,0,b, -1.5f,3,b, 1.5f,3,b, 1,1,0,0,0,0,1);
    }
    /** Quad drawn without culling: u0 at corners 0 and 3, u1 at 1 and 2; v0 along the first edge (bottom), v1 along the last. */
    private static void quad(com.mojang.blaze3d.vertex.VertexConsumer vc,org.joml.Matrix4f m,org.joml.Matrix3f n,int light,
            float x0,float y0,float z0,float x1,float y1,float z1,float x2,float y2,float z2,float x3,float y3,float z3,
            float u0,float v0,float u1,float v1,float nx,float ny,float nz) {
        vertex(vc,m,n,light,x0,y0,z0,u0,v0,nx,ny,nz);vertex(vc,m,n,light,x1,y1,z1,u1,v0,nx,ny,nz);
        vertex(vc,m,n,light,x2,y2,z2,u1,v1,nx,ny,nz);vertex(vc,m,n,light,x3,y3,z3,u0,v1,nx,ny,nz);
    }
    private static void vertex(com.mojang.blaze3d.vertex.VertexConsumer vc,org.joml.Matrix4f m,org.joml.Matrix3f n,int light,
            float x,float y,float z,float u,float v,float nx,float ny,float nz) {
        vc.vertex(m,x,y,z).color(255,255,255,255).uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n,nx,ny,nz).endVertex();
    }
    @Override public ResourceLocation getTextureLocation(DeployableEntity entity) {return TextureAtlas.LOCATION_BLOCKS;}
}
