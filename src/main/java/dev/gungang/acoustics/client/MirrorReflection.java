package dev.gungang.acoustics.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.logging.LogUtils;
import dev.gungang.acoustics.GungangAcoustics;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * 244 mirror: a real reflection. Each frame, right after the sky, the nearest few mirrors seen last frame are rendered
 * into their own off-screen texture from the camera reflected through the glass, with an off-axis frustum fitted to
 * the glass so the near plane is the glass itself (nothing behind the mirror shows). The reflection holds the blocks in
 * front of the mirror (a mesh cached per mirror and rebuilt every second), the entities there (the viewer included)
 * and their block entities. The glass then shows that texture. It is drawn with vanilla renderers, not the chunk
 * renderer, so it works with Embeddium; with a shader pack in use, or past the nearest few, a mirror stays plain silver.
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class MirrorReflection {
    private static final int SLOTS=3,WIDTH=200,HEIGHT=420,REBUILD_TICKS=20,FORGET_TICKS=200,MAX_ENTITIES=48;
    private static final double RANGE=40,FAR=64,WIDE=10,BELOW=4,ABOVE=10,DEPTH=18;
    /** A mirror's glass as last drawn: world corners in the order the renderer gave them, the front normal and centre. */
    private record Glass(Vec3[] corners,Vec3 normal,Vec3 center){}
    /** A reflection ready this frame: which glass, the texture, and the texture position of each corner. */
    record Ready(Vec3 center,Vec3 normal,ResourceLocation texture,float[][] uv){}
    private static final class Mesh{
        BlockPos origin;final Map<RenderType,VertexBuffer> layers=new LinkedHashMap<>();final List<BlockPos> blockEntities=new ArrayList<>();
        long built,used;
        void close(){layers.values().forEach(VertexBuffer::close);layers.clear();}
    }
    private static final List<Glass> seen=new ArrayList<>();
    private static final List<Ready> ready=new ArrayList<>();
    private static final TextureTarget[] targets=new TextureTarget[SLOTS];
    private static final ResourceLocation[] textures=new ResourceLocation[SLOTS];
    private static final Map<String,Mesh> meshes=new HashMap<>();
    private static Matrix4f inverseView=new Matrix4f();
    private static Vec3 camera=Vec3.ZERO;
    /** Inside a reflection pass: mirrors drawn there are plain and not registered (no mirror in a mirror). */
    static boolean drawing;
    private static boolean broken;

    // ---- the glass, from PartyRenderer ----

    /**
     * Registers the glass rectangle {@code local} (pixels, corners in drawing order, at the current pose) for the next
     * frame's reflection and returns this frame's reflection texture and corner positions, or null to draw plain glass.
     */
    static Ready glass(PoseStack pose,float[][] local){
        if(drawing||broken)return null;
        var m=pose.last().pose();var corners=new Vec3[4];
        for(int i=0;i<4;i++)corners[i]=world(m,local[i][0]/16,local[i][1]/16,local[i][2]/16);
        var center=corners[0].add(corners[2]).scale(.5);
        var front=world(m,0,0,-1).subtract(world(m,0,0,0)).normalize();
        seen.add(new Glass(corners,front,center));
        for(var r:ready)if(r.center.distanceToSqr(center)<.02&&r.normal.dot(front)>.95)return r;
        return null;
    }
    /** A point of the current pose in world space: the pose maps to camera-relative view space, undone here. */
    private static Vec3 world(Matrix4f pose,float x,float y,float z){
        var v=pose.transform(new Vector4f(x,y,z,1));inverseView.transform(v);
        return new Vec3(v.x+camera.x,v.y+camera.y,v.z+camera.z);
    }
    static ResourceLocation texture(Ready r){return r.texture;}
    static float[][] uv(Ready r){return r.uv;}

    // ---- the reflection pass ----

    @SubscribeEvent public static void stage(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_SKY)return;
        inverseView=new Matrix4f(e.getPoseStack().last().pose()).invert();camera=e.getCamera().getPosition();
        var last=List.copyOf(seen);seen.clear();ready.clear();
        var mc=Minecraft.getInstance();
        if(broken||mc.level==null||last.isEmpty()||shaderPack()){forget(mc.level==null?0:mc.level.getGameTime());return;}
        var eye=camera;var chosen=new ArrayList<Glass>();
        for(var g:last.stream().sorted(Comparator.comparingDouble(g->g.center.distanceToSqr(eye))).toList()){
            if(chosen.size()>=SLOTS)break;
            if(g.center.distanceToSqr(eye)>RANGE*RANGE||eye.subtract(g.center).dot(g.normal)<.05||Math.abs(g.normal.y)>.3)continue;
            if(chosen.stream().anyMatch(o->o.center.distanceToSqr(g.center)<.02))continue;
            chosen.add(g);
        }
        if(chosen.isEmpty()){forget(mc.level.getGameTime());return;}
        var projection=new Matrix4f(RenderSystem.getProjectionMatrix());var sorting=RenderSystem.getVertexSorting();
        var modelView=RenderSystem.getModelViewStack();modelView.pushPose();modelView.setIdentity();RenderSystem.applyModelViewMatrix();
        boolean built=false;
        try{
            for(int i=0;i<chosen.size();i++){
                var g=chosen.get(i);var mesh=mesh(mc,g,!built);
                if(mesh==null)continue;
                if(mc.level.getGameTime()-mesh.built==0)built=true;
                ready.add(reflect(mc,i,g,mesh,e.getPartialTick()));
            }
        }catch(Throwable error){
            broken=true;ready.clear();
            LogUtils.getLogger().error("[GunGang] Mirror reflections turned off after a rendering error",error);
        }finally{
            drawing=false;
            mc.renderBuffers().bufferSource().endBatch();
            modelView.popPose();RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection,sorting);
            mc.getMainRenderTarget().bindWrite(true);
        }
        forget(mc.level.getGameTime());
    }
    private static Ready reflect(Minecraft mc,int slot,Glass g,Mesh mesh,float partial){
        Vec3 n=g.normal,eye=camera;
        double distance=eye.subtract(g.center).dot(n);
        Vec3 mirrored=eye.subtract(n.scale(2*distance));
        // Looking out through the glass from behind it, upright.
        var view=new Matrix4f().setLookAlong((float)n.x,(float)n.y,(float)n.z,0,1,0);
        float[][] at=new float[4][];double near=Double.MAX_VALUE;
        for(int k=0;k<4;k++){
            var c=g.corners[k];var v=view.transform(new Vector4f((float)(c.x-mirrored.x),(float)(c.y-mirrored.y),(float)(c.z-mirrored.z),1));
            at[k]=new float[]{v.x,v.y,-v.z};near=Math.min(near,-v.z);
        }
        float l=Float.MAX_VALUE,r=-Float.MAX_VALUE,b=Float.MAX_VALUE,t=-Float.MAX_VALUE;
        for(var p:at){float x=(float)(p[0]*near/p[2]),y=(float)(p[1]*near/p[2]);p[0]=x;p[1]=y;l=Math.min(l,x);r=Math.max(r,x);b=Math.min(b,y);t=Math.max(t,y);}
        // The near plane a hair past the glass, so the mirror's own glass and frame never show in it.
        float clip=(float)(near+.01),scale=clip/(float)near;
        var projection=new Matrix4f().setFrustum(l*scale,r*scale,b*scale,t*scale,clip,(float)FAR);
        float[][] uv=new float[4][];
        for(int k=0;k<4;k++)uv[k]=new float[]{(at[k][0]-l)/(r-l),(at[k][1]-b)/(t-b)};

        var target=target(slot);
        var fog=RenderSystem.getShaderFogColor();
        target.setClearColor(fog[0],fog[1],fog[2],1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);
        RenderSystem.setProjectionMatrix(projection,VertexSorting.DISTANCE_TO_ORIGIN);
        FogRenderer.setupNoFog();
        Lighting.setupLevel(view);
        drawing=true;
        // Blocks: the cached mesh, placed relative to the mirrored eye.
        var origin=mesh.origin;
        var blocks=new Matrix4f(view).translate((float)(origin.getX()-mirrored.x),(float)(origin.getY()-mirrored.y),(float)(origin.getZ()-mirrored.z));
        for(var layer:mesh.layers.entrySet()){
            var type=layer.getKey();type.setupRenderState();
            var shader=RenderSystem.getShader();
            if(shader!=null){
                if(shader.CHUNK_OFFSET!=null)shader.CHUNK_OFFSET.set(0f,0f,0f);
                layer.getValue().bind();layer.getValue().drawWithShader(blocks,projection,shader);VertexBuffer.unbind();
            }
            type.clearRenderState();
        }
        // Entities and block entities in front of the mirror.
        var pose=new PoseStack();pose.mulPoseMatrix(view);
        MultiBufferSource.BufferSource buffers=mc.renderBuffers().bufferSource();
        var level=mc.level;var dispatcher=mc.getEntityRenderDispatcher();
        int count=0;
        for(var entity:level.getEntities((net.minecraft.world.entity.Entity)null,region(g))){
            if(count++>=MAX_ENTITIES)break;
            double x=Mth.lerp(partial,entity.xOld,entity.getX()),y=Mth.lerp(partial,entity.yOld,entity.getY()),z=Mth.lerp(partial,entity.zOld,entity.getZ());
            if(new Vec3(x,y,z).subtract(g.center).dot(n)<-.3)continue; // behind the glass
            try{dispatcher.render(entity,x-mirrored.x,y-mirrored.y,z-mirrored.z,Mth.lerp(partial,entity.yRotO,entity.getYRot()),partial,pose,buffers,dispatcher.getPackedLightCoords(entity,partial));}
            catch(RuntimeException skipped){}
        }
        var blockEntities=mc.getBlockEntityRenderDispatcher();
        for(var pos:mesh.blockEntities){
            var be=level.getBlockEntity(pos);if(be==null)continue;
            pose.pushPose();pose.translate(pos.getX()-mirrored.x,pos.getY()-mirrored.y,pos.getZ()-mirrored.z);
            try{blockEntities.render(be,partial,pose,buffers);}catch(RuntimeException skipped){}
            pose.popPose();
        }
        buffers.endBatch();
        drawing=false;
        return new Ready(g.center,g.normal,textures[slot],uv);
    }
    private static TextureTarget target(int slot){
        if(targets[slot]==null){
            var target=new TextureTarget(WIDTH,HEIGHT,true,Minecraft.ON_OSX);target.setFilterMode(9729);
            targets[slot]=target;textures[slot]=new ResourceLocation(GungangAcoustics.ID,"mirror_reflection_"+slot);
            int id=target.getColorTextureId();
            Minecraft.getInstance().getTextureManager().register(textures[slot],new AbstractTexture(){
                {this.id=id;}
                @Override public void load(ResourceManager manager){}
                @Override public void releaseId(){} // the render target owns the texture
            });
        }
        return targets[slot];
    }

    // ---- the cached block mesh ----

    /** What a mirror can show: up to WIDE blocks to each side, BELOW under and ABOVE over its middle, DEPTH out in front. */
    private static AABB region(Glass g){
        var front=Direction.getNearest(g.normal.x,0,g.normal.z);var side=front.getClockWise();
        Vec3 f=Vec3.atLowerCornerOf(front.getNormal()),s=Vec3.atLowerCornerOf(side.getNormal());
        var a=g.center.add(s.scale(WIDE)).add(0,-BELOW,0).subtract(f.scale(.5));
        var b=g.center.subtract(s.scale(WIDE)).add(0,ABOVE,0).add(f.scale(DEPTH));
        return new AABB(a,b);
    }
    private static Mesh mesh(Minecraft mc,Glass g,boolean mayBuild){
        var box=region(g);var key=BlockPos.containing(g.center).asLong()+"/"+Direction.getNearest(g.normal.x,0,g.normal.z);
        long now=mc.level.getGameTime();var mesh=meshes.get(key);
        if(mesh!=null&&(now-mesh.built<REBUILD_TICKS||!mayBuild)){mesh.used=now;return mesh;}
        if(!mayBuild)return null;
        if(mesh!=null)mesh.close();else mesh=new Mesh();
        build(mc,mesh,box);mesh.built=mesh.used=now;meshes.put(key,mesh);
        return mesh;
    }
    private static void build(Minecraft mc,Mesh mesh,AABB box){
        var level=mc.level;var blocks=mc.getBlockRenderer();var blockEntities=mc.getBlockEntityRenderDispatcher();
        var min=BlockPos.containing(box.minX,box.minY,box.minZ);var max=BlockPos.containing(box.maxX,box.maxY,box.maxZ);
        mesh.origin=min;mesh.blockEntities.clear();
        var builders=new LinkedHashMap<RenderType,BufferBuilder>();
        var pose=new PoseStack();var random=RandomSource.create();
        for(var pos:BlockPos.betweenClosed(min,max)){
            var state=level.getBlockState(pos);if(state.isAir())continue;
            var fluid=state.getFluidState();
            if(!fluid.isEmpty()){
                var type=ItemBlockRenderTypes.getRenderLayer(fluid);
                // The liquid renderer writes chunk-local positions (pos & 15); shift them to the mesh's origin.
                blocks.renderLiquid(pos,level,new Offset(builder(builders,type),(pos.getX()&~15)-min.getX(),(pos.getY()&~15)-min.getY(),(pos.getZ()&~15)-min.getZ()),state,fluid);
            }
            if(state.getRenderShape()==RenderShape.MODEL){
                var model=blocks.getBlockModel(state);var stored=level.getModelDataManager().getAt(pos);
                var data=model.getModelData(level,pos,state,stored==null?ModelData.EMPTY:stored);
                random.setSeed(state.getSeed(pos));
                for(var type:model.getRenderTypes(state,random,data)){
                    pose.pushPose();pose.translate(pos.getX()-min.getX(),pos.getY()-min.getY(),pos.getZ()-min.getZ());
                    blocks.renderBatched(state,pos,level,pose,builder(builders,type),true,random,data,type);
                    pose.popPose();
                }
            }
            if(state.hasBlockEntity()){
                var be=level.getBlockEntity(pos);
                if(be!=null&&!(be instanceof dev.gungang.acoustics.server.PartyBlocks.PartyEntity party&&party.kind()==dev.gungang.acoustics.server.PartyBlocks.Kind.MIRROR)
                        &&blockEntities.getRenderer(be)!=null)mesh.blockEntities.add(pos.immutable());
            }
        }
        for(var e:builders.entrySet()){
            var rendered=e.getValue().endOrDiscardIfEmpty();if(rendered==null)continue;
            var buffer=new VertexBuffer(VertexBuffer.Usage.STATIC);buffer.bind();buffer.upload(rendered);VertexBuffer.unbind();
            mesh.layers.put(e.getKey(),buffer);
        }
    }
    private static BufferBuilder builder(Map<RenderType,BufferBuilder> builders,RenderType type){
        return builders.computeIfAbsent(type,t->{var b=new BufferBuilder(t.bufferSize());b.begin(t.mode(),t.format());return b;});
    }
    private static void forget(long now){
        meshes.values().removeIf(m->{if(now-m.used>FORGET_TICKS||now<m.used){m.close();return true;}return false;});
    }
    /** Vertices shifted by a fixed offset (liquids). */
    private record Offset(VertexConsumer to,double dx,double dy,double dz) implements VertexConsumer {
        @Override public VertexConsumer vertex(double x,double y,double z){to.vertex(x+dx,y+dy,z+dz);return this;}
        @Override public VertexConsumer color(int r,int g,int b,int a){to.color(r,g,b,a);return this;}
        @Override public VertexConsumer uv(float u,float v){to.uv(u,v);return this;}
        @Override public VertexConsumer overlayCoords(int u,int v){to.overlayCoords(u,v);return this;}
        @Override public VertexConsumer uv2(int u,int v){to.uv2(u,v);return this;}
        @Override public VertexConsumer normal(float x,float y,float z){to.normal(x,y,z);return this;}
        @Override public void endVertex(){to.endVertex();}
        @Override public void defaultColor(int r,int g,int b,int a){to.defaultColor(r,g,b,a);}
        @Override public void unsetDefaultColor(){to.unsetDefaultColor();}
    }

    // ---- shader packs ----

    private static java.lang.invoke.MethodHandle shaderPackCheck;private static boolean checked;
    /** Oculus/Iris with a shader pack on: its pipeline owns the framebuffers, so mirrors stay plain. */
    private static boolean shaderPack(){
        if(!checked){
            checked=true;
            try{
                var api=Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                var instance=api.getMethod("getInstance").invoke(null);
                shaderPackCheck=java.lang.invoke.MethodHandles.publicLookup().findVirtual(api,"isShaderPackInUse",java.lang.invoke.MethodType.methodType(boolean.class)).bindTo(instance);
            }catch(ReflectiveOperationException|LinkageError absent){shaderPackCheck=null;}
        }
        if(shaderPackCheck==null)return false;
        try{return (boolean)shaderPackCheck.invokeExact();}catch(Throwable error){return true;}
    }
    private MirrorReflection(){}
}
