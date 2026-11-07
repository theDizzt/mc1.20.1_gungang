package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.gungang.acoustics.mixin.EntityWaterAccessor;
import dev.gungang.acoustics.server.ToyBlocks;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 224 aquarium: the water inside the glass (no faces toward a neighbouring tank, so tanks merge), the sand or gravel
 * floor, a rock, seagrass and the fish swimming its circle. A disguise's tank (no real block at its spot) stands alone.
 */
public final class AquariumRenderer implements BlockEntityRenderer<ToyBlocks.AquariumEntity> {
    private static final float INSET=.5f/16,SURFACE=14f/16,FLOOR=2f/16;
    /** Each tank's display fish, rebuilt when its kind changes. */
    private static final Map<ToyBlocks.AquariumEntity,Entity> FISH=new WeakHashMap<>();
    public AquariumRenderer(BlockEntityRendererProvider.Context context){}

    @Override public void render(ToyBlocks.AquariumEntity tank,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        var state=tank.getBlockState();if(!(state.getBlock() instanceof ToyBlocks.Aquarium))return;
        var level=tank.getLevel();var pos=tank.getBlockPos();
        // A real placed tank looks at its neighbours; a disguise's (not the block at its own spot) never merges.
        boolean placed=level!=null&&level.getBlockState(pos).getBlock()==state.getBlock();
        boolean[] open=new boolean[6];
        for(var dir:Direction.values())open[dir.ordinal()]=placed&&level.getBlockState(pos.relative(dir)).getBlock()==state.getBlock();
        float x0=open[Direction.WEST.ordinal()]?0:INSET,x1=open[Direction.EAST.ordinal()]?1:1-INSET;
        float z0=open[Direction.NORTH.ordinal()]?0:INSET,z1=open[Direction.SOUTH.ordinal()]?1:1-INSET;
        float y0=open[Direction.DOWN.ordinal()]?0:INSET,y1=open[Direction.UP.ordinal()]?1:SURFACE;
        // Fixed sheet buffers: the solid contents are flushed before the translucent water, so the water never hides them.
        water(pose,buffers.getBuffer(net.minecraft.client.renderer.Sheets.translucentCullBlockSheet()),light,open,x0,y0,z0,x1,y1,z1);
        // Floor, rock and plant sit on the tank's bottom (a stacked tank's upper blocks have none). Each part is drawn with
        // pixel-true UVs (a scaled block would squash its whole texture into the thin box).
        var solid=buffers.getBuffer(net.minecraft.client.renderer.Sheets.cutoutBlockSheet());
        float floor=y0;
        int substrate=state.getValue(ToyBlocks.Aquarium.SUBSTRATE);
        if(substrate>0&&!open[Direction.DOWN.ordinal()]){
            cube(pose,solid,sprite(substrate==1?"sand":"gravel"),light,x0,y0,z0,x1,y0+FLOOR,z1);floor=y0+FLOOR;
        }
        if(state.getValue(ToyBlocks.Aquarium.ROCK)){
            cube(pose,solid,sprite("cobblestone"),light,.56f,floor,.58f,.82f,floor+.16f,.82f);
            cube(pose,solid,sprite("mossy_cobblestone"),light,.62f,floor+.16f,.62f,.76f,floor+.24f,.76f);
            cube(pose,solid,sprite("andesite"),light,.7f,floor,.36f,.86f,floor+.1f,.52f);
            cube(pose,solid,sprite("stone"),light,.46f,floor,.5f,.54f,floor+.05f,.58f);
        }
        if(state.getValue(ToyBlocks.Aquarium.PLANT)){
            float time=level!=null?level.getGameTime()+partial:0,top=(open[Direction.UP.ordinal()]?1:SURFACE)-floor-.06f;
            plant(pose,solid,sprite("seagrass"),light,.24f,floor,.26f,Math.min(.62f,top),time);
            plant(pose,solid,sprite("seagrass"),light,.36f,floor,.7f,Math.min(.42f,top),time+20);
            plant(pose,solid,sprite("kelp_plant"),light,.16f,floor,.5f,Math.min(.72f,top),time+40);
        }
        int kind=state.getValue(ToyBlocks.Aquarium.FISH);
        if(kind>0)fish(tank,kind,partial,floor,pose,buffers,light);
    }
    private static net.minecraft.client.renderer.texture.TextureAtlasSprite sprite(String name){
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(new ResourceLocation("minecraft","block/"+name));
    }
    /** A box whose faces take the texture's pixels at their block coordinates, like a model element. */
    private static void cube(PoseStack pose,VertexConsumer buffer,net.minecraft.client.renderer.texture.TextureAtlasSprite sp,int light,float x0,float y0,float z0,float x1,float y1,float z1){
        for(var dir:Direction.values()){
            float[][] q=switch(dir){
                case UP->new float[][]{{x0,y1,z0},{x0,y1,z1},{x1,y1,z1},{x1,y1,z0}};
                case DOWN->new float[][]{{x0,y0,z1},{x0,y0,z0},{x1,y0,z0},{x1,y0,z1}};
                case NORTH->new float[][]{{x1,y1,z0},{x1,y0,z0},{x0,y0,z0},{x0,y1,z0}};
                case SOUTH->new float[][]{{x0,y1,z1},{x0,y0,z1},{x1,y0,z1},{x1,y1,z1}};
                case WEST->new float[][]{{x0,y1,z0},{x0,y0,z0},{x0,y0,z1},{x0,y1,z1}};
                default->new float[][]{{x1,y1,z1},{x1,y0,z1},{x1,y0,z0},{x1,y1,z0}};
            };
            for(var v:q){
                float u=switch(dir.getAxis()){case X->v[2];default->v[0];};
                float w=dir.getAxis()==Direction.Axis.Y?v[2]:1-v[1];
                vertex(pose,buffer,v[0],v[1],v[2],sp.getU(u*16),sp.getV(w*16),light,dir.getStepX(),dir.getStepY(),dir.getStepZ());
            }
        }
    }
    /** A swaying clump: two crossed quads of a plant texture rooted at (x, floor, z). */
    private static void plant(PoseStack pose,VertexConsumer buffer,net.minecraft.client.renderer.texture.TextureAtlasSprite sp,int light,float x,float floor,float z,float height,float time){
        if(height<=.05f)return;
        float r=.14f,sway=.025f*(float)Math.sin(time/18f);
        float[][] arms={{-r,-r,r,r},{-r,r,r,-r}};
        for(var a:arms){
            // Both windings: the cutout sheet culls back faces.
            vertex(pose,buffer,x+a[0]+sway,floor+height,z+a[1],sp.getU0(),sp.getV0(),light,0,1,0);
            vertex(pose,buffer,x+a[0],floor,z+a[1],sp.getU0(),sp.getV1(),light,0,1,0);
            vertex(pose,buffer,x+a[2],floor,z+a[3],sp.getU1(),sp.getV1(),light,0,1,0);
            vertex(pose,buffer,x+a[2]+sway,floor+height,z+a[3],sp.getU1(),sp.getV0(),light,0,1,0);
            vertex(pose,buffer,x+a[2]+sway,floor+height,z+a[3],sp.getU1(),sp.getV0(),light,0,1,0);
            vertex(pose,buffer,x+a[2],floor,z+a[3],sp.getU1(),sp.getV1(),light,0,1,0);
            vertex(pose,buffer,x+a[0],floor,z+a[1],sp.getU0(),sp.getV1(),light,0,1,0);
            vertex(pose,buffer,x+a[0]+sway,floor+height,z+a[1],sp.getU0(),sp.getV0(),light,0,1,0);
        }
    }
    private static void vertex(PoseStack pose,VertexConsumer buffer,float x,float y,float z,float u,float v,int light,float nx,float ny,float nz){
        buffer.vertex(pose.last().pose(),x,y,z).color(1f,1f,1f,1f).uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.last().normal(),nx,ny,nz).endVertex();
    }
    /** The water's outer faces: none toward a merged neighbour, a surface on top unless another tank is above. */
    private static void water(PoseStack pose,VertexConsumer buffer,int light,boolean[] open,float x0,float y0,float z0,float x1,float y1,float z1){
        var sprite=Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(new ResourceLocation("minecraft","block/water_still"));
        float u0=sprite.getU0(),u1=sprite.getU1(),v0=sprite.getV0(),v1=sprite.getV1();
        var m=pose.last().pose();var n=pose.last().normal();
        float r=.25f,g=.46f,b=.89f,a=.55f;
        for(var dir:Direction.values()){
            if(open[dir.ordinal()])continue;
            float[][] q=switch(dir){
                case UP->new float[][]{{x0,y1,z0},{x0,y1,z1},{x1,y1,z1},{x1,y1,z0}};
                case DOWN->new float[][]{{x0,y0,z1},{x0,y0,z0},{x1,y0,z0},{x1,y0,z1}};
                case NORTH->new float[][]{{x1,y1,z0},{x1,y0,z0},{x0,y0,z0},{x0,y1,z0}};
                case SOUTH->new float[][]{{x0,y1,z1},{x0,y0,z1},{x1,y0,z1},{x1,y1,z1}};
                case WEST->new float[][]{{x0,y1,z0},{x0,y0,z0},{x0,y0,z1},{x0,y1,z1}};
                default->new float[][]{{x1,y1,z1},{x1,y0,z1},{x1,y0,z0},{x1,y1,z0}};
            };
            float[][] uv={{u0,v0},{u0,v1},{u1,v1},{u1,v0}};
            for(int i=0;i<4;i++)buffer.vertex(m,q[i][0],q[i][1],q[i][2]).color(r,g,b,a).uv(uv[i][0],uv[i][1]).overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(light).normal(n,dir.getStepX(),dir.getStepY(),dir.getStepZ()).endVertex();
        }
    }
    /** The fish swimming a slow circle in the middle of the tank (a squid is a small one). */
    private static void fish(ToyBlocks.AquariumEntity tank,int kind,float partial,float floor,PoseStack pose,MultiBufferSource buffers,int light){
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        var type=switch(kind){case 1->EntityType.COD;case 2->EntityType.SALMON;case 3->EntityType.TROPICAL_FISH;case 4->EntityType.PUFFERFISH;default->EntityType.SQUID;};
        var entity=FISH.get(tank);
        if(entity==null||entity.getType()!=type){entity=type.create(mc.level);if(entity==null)return;FISH.put(tank,entity);}
        ((EntityWaterAccessor)entity).gungang$setInWater(true);
        float time=mc.level.getGameTime()+partial,seed=(tank.getBlockPos().hashCode()&255)/40f;
        float t=time/45f+seed;
        double x=.5+.2*Math.cos(t),z=.5+.2*Math.sin(t),y=floor+.38+.04*Math.sin(t*2.3);
        // Heading along the circle: yaw 0 faces +Z, and the fish moves toward (−sin t, cos t).
        float yaw=(float)Math.toDegrees(t)+180;
        entity.setPos(tank.getBlockPos().getX()+x,tank.getBlockPos().getY()+y,tank.getBlockPos().getZ()+z);
        entity.tickCount=(int)time;entity.setYRot(yaw);entity.yRotO=yaw;
        if(entity instanceof LivingEntity living){living.yBodyRot=yaw;living.yBodyRotO=yaw;living.yHeadRot=yaw;living.yHeadRotO=yaw;}
        float scale=switch(kind){case 2->.5f;case 3->.7f;case 4->.8f;case 5->.28f;default->.6f;};
        pose.pushPose();pose.translate(x,y,z);pose.scale(scale,scale,scale);
        mc.getEntityRenderDispatcher().render(entity,0,0,0,yaw,partial,pose,buffers,light);
        pose.popPose();
    }
    @Override public boolean shouldRenderOffScreen(ToyBlocks.AquariumEntity tank){return false;}
}
