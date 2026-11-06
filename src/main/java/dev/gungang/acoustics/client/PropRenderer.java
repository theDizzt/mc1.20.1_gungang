package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.gungang.acoustics.server.PropEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;

public final class PropRenderer extends EntityRenderer<PropEntity> {
    /** Sprites of each drawn block state, marked visible every frame (campfire flames, stonecutter saw). */
    private final java.util.Map<net.minecraft.world.level.block.state.BlockState,java.util.List<TextureAtlasSprite>> sprites=new java.util.HashMap<>();
    /**
     * Embeddium's "animate only visible textures" only advances sprites it saw in chunk meshes, so a block model drawn
     * by an entity would freeze; this marks the prop's sprites as visible. No-op without Embeddium/Sodium.
     */
    private static final java.lang.invoke.MethodHandle MARK_ACTIVE=markActive();
    private static java.lang.invoke.MethodHandle markActive(){
        try{
            var type=Class.forName("me.jellysquid.mods.sodium.client.render.texture.SpriteUtil");
            return java.lang.invoke.MethodHandles.publicLookup().findStatic(type,"markSpriteActive",java.lang.invoke.MethodType.methodType(void.class,TextureAtlasSprite.class));
        }catch(ReflectiveOperationException|LinkageError absent){return null;}
    }
    private void animate(net.minecraft.world.level.block.state.BlockState state){
        if(MARK_ACTIVE==null)return;
        var list=sprites.computeIfAbsent(state,s->{
            var model=Minecraft.getInstance().getBlockRenderer().getBlockModel(s);var random=net.minecraft.util.RandomSource.create();var found=new java.util.LinkedHashSet<TextureAtlasSprite>();
            var sides=net.minecraft.core.Direction.values();
            // Weighted models (fire: fire_0 or fire_1) pick a variant from the random seed. renderSingleBlock reseeds 42 for
            // every side; one shared random drifted to the other variant, so the drawn one was never marked and froze.
            // Collect every variant over many seeds, the drawn one (seed 42) included.
            for(long seed=0;seed<=64;seed++)for(int i=0;i<=sides.length;i++){
                random.setSeed(seed==64?42:seed);
                for(var quad:model.getQuads(s,i<sides.length?sides[i]:null,random))found.add(quad.getSprite());
            }
            return java.util.List.copyOf(found);
        });
        try{for(var sprite:list)MARK_ACTIVE.invokeExact(sprite);}catch(Throwable ignored){}
    }
    public PropRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=0;}
    /**
     * A disguise under invisibility (159 brewing stand, 191 doll's cloak) is not drawn at all while the effect lasts, for
     * everyone, its own hider and teammates included; a found block's marker still shows.
     */
    static boolean concealed(PropEntity entity){return entity.isInvisible()&&!entity.marker();}
    @Override public void render(PropEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        if(concealed(entity))return;
        var mc=Minecraft.getInstance();var state=entity.state();pose.pushPose();
        var emote=EmoteClient.offset(entity,partial);pose.translate(emote.x,emote.y,emote.z);
        if(PropObjectRenderer.render(entity,partial,pose,buffers,light)){
        }else if(entity.item().equals("minecraft:barrier")){
            // Invisible barrier body; only authorized viewers receive a cube in the outline mask.
            if(mc.shouldEntityAppearGlowing(entity)){
                pose.translate(-.5,0,-.5);
                MultiBufferSource outlineOnly=type->buffers.getBuffer(RenderType.outline(TextureAtlas.LOCATION_BLOCKS));
                mc.getBlockRenderer().renderSingleBlock(Blocks.WHITE_CONCRETE.defaultBlockState(),pose,outlineOnly,light,OverlayTexture.NO_OVERLAY);
            }
        }else if(entity.item().equals("minecraft:structure_void")){
            // Invisible in the world; drawn as a structure block shows it, a small box outline that matches its hitbox.
            pose.translate(-.5,0,-.5);
            LevelRenderer.renderLineBox(pose,buffers.getBuffer(RenderType.lines()),5/16d,5/16d,5/16d,11/16d,11/16d,11/16d,1,.75f,.75f,1);
        }else if(entity.item().equals("minecraft:painting")){
            var variants=entity.level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.PAINTING_VARIANT);
            var list=variants.holders().toList();
            var variant=list.get(Math.floorMod(entity.painting(),list.size()));
            var painting=new net.minecraft.world.entity.decoration.Painting(entity.level(),entity.blockPosition(),net.minecraft.core.Direction.from2DDataValue((entity.rotation()+2)%4),variant);
            pose.translate(0,variant.value().getHeight()/32d,0);
            painting.setPos(entity.position());
            mc.getEntityRenderDispatcher().getRenderer(painting).render(painting,entity.rotation()*90,partial,pose,buffers,light);
        }else{
            if(entity.item().equals("gungang:dried_ghast"))pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180-net.minecraft.util.Mth.rotLerp(partial,entity.yRotO,entity.getYRot())));
            pose.translate(-.5,0,-.5);
            // Slightly larger than a block around its centre, so where a disguise overlaps world blocks
            // (a sunk health pack in the floor, a prop against a wall) the disguise's faces win the depth test.
            pose.translate(.5,.5,.5);pose.scale(1.004f,1.004f,1.004f);pose.translate(-.5,-.5,-.5);
            // A sunk health pack fills the floor cell exactly; its top would z-fight (flicker) with the floor's,
            // so it sits a hair higher and narrower: a flush lid in the floor instead.
            if(entity.sunk()){pose.translate(.5,0,.5);pose.scale(.996f,1,.996f);pose.translate(-.5,1/64f,-.5);}
            for(var part:dev.gungang.acoustics.server.PropGeometry.parts(entity)){
                pose.pushPose();pose.translate(part.offset().getX(),part.offset().getY(),part.offset().getZ());var partState=part.state();
                // Emissive blocks (campfire, glowstone, lanterns) stay bright in the dark like the real block.
                int partLight=LightTexture.pack(Math.max(LightTexture.block(light),partState.getLightEmission()),LightTexture.sky(light));
                if(partState.getBlock() instanceof dev.gungang.acoustics.server.ShutterBlock){
                    // A lone tile rolling up into its header; the world has no shutter group for its block entity to follow.
                    double lift=PropEffects.motion(entity,partial);
                    ShutterRenderer.draw(pose,buffers,partLight,OverlayTexture.NO_OVERLAY,partState.getValue(dev.gungang.acoustics.server.ShutterBlock.AXIS)==net.minecraft.core.Direction.Axis.X,true,true,true,lift,lift,lift);
                    pose.popPose();continue;
                }
                if(partState.getBlock() instanceof EntityBlock block){
                    // Ticked by PropEffects (book pages, chest lid); the chest lid opens while the prop is open.
                    var be=PropEffects.blockEntity(entity,part.offset(),partState,block);
                    // 241: the easel disguise's canvas lives in the prop's text.
                    if(be instanceof dev.gungang.acoustics.server.ToyBlocks.EaselEntity easel)easel.pixels=dev.gungang.acoustics.server.ToyBlocks.canvas(entity.text());
                    if(be!=null){if(be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest)chest.triggerEvent(1,entity.open()?1:0);mc.getBlockEntityRenderDispatcher().renderItem(be,pose,buffers,partLight,OverlayTexture.NO_OVERLAY);}
                }
                if(partState.getRenderShape()==RenderShape.MODEL){animate(partState);mc.getBlockRenderer().renderSingleBlock(partState,pose,buffers,partLight,OverlayTexture.NO_OVERLAY);}
                // A bubble column disguise is the bubbles alone (PropEffects), without the water block around them.
                else if(!partState.getFluidState().isEmpty()&&!(partState.getBlock() instanceof net.minecraft.world.level.block.BubbleColumnBlock))fluid(entity,partState.getFluidState(),pose,buffers,partLight);
                pose.popPose();
            }
            if(entity.item().equals("minecraft:beacon")&&entity.open())net.minecraft.client.renderer.blockentity.BeaconRenderer.renderBeaconBeam(pose,buffers,new ResourceLocation("minecraft","textures/entity/beacon_beam.png"),partial,1,entity.level().getGameTime(),0,128,new float[]{.45f,1,.85f},.2f,.25f);
        }
        pose.popPose();
        EmoteClient.popup(entity,partial,pose,buffers,light);
        if(entity.marker())renderNameTag(entity,entity.getDisplayName(),pose,buffers,light);
    }
    /**
     * Liquid blocks (water, lava, acid, hot spring water, bubble columns) have no block model; draw a still source block:
     * its animated still sprite and tint on every face, with the top at a source block's 8/9 height.
     */
    private static void fluid(PropEntity entity,net.minecraft.world.level.material.FluidState fluid,PoseStack pose,MultiBufferSource buffers,int light){
        fluid(fluid,entity.level(),entity.blockPosition(),pose,buffers,light);
    }
    /** The same still source block, also drawn as the HUD icon of liquid disguises (which have no item or block model). */
    static void fluid(net.minecraft.world.level.material.FluidState fluid,net.minecraft.world.level.BlockAndTintGetter level,net.minecraft.core.BlockPos pos,PoseStack pose,MultiBufferSource buffers,int light){
        var client=net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions.of(fluid);
        var sprite=Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(level==null?client.getStillTexture():client.getStillTexture(fluid,level,pos));
        if(MARK_ACTIVE!=null)try{MARK_ACTIVE.invokeExact(sprite);}catch(Throwable ignored){}
        int color=level==null?client.getTintColor():client.getTintColor(fluid,level,pos);
        if((color>>>24)==0)color|=0xFF000000;
        float r=(color>>16&255)/255f,g=(color>>8&255)/255f,b=(color&255)/255f,a=(color>>>24)/255f,h=8/9f;
        var c=buffers.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));var last=pose.last();
        float u0=sprite.getU0(),u1=sprite.getU1(),v0=sprite.getV0(),v1=sprite.getV1(),side=sprite.getV(16*h);
        float[][] faces={
            {0,h,0, 0,h,1, 1,h,1, 1,h,0, 0,1,0},{0,0,1, 0,0,0, 1,0,0, 1,0,1, 0,-1,0},
            {1,h,0, 1,0,0, 0,0,0, 0,h,0, 0,0,-1},{0,h,1, 0,0,1, 1,0,1, 1,h,1, 0,0,1},
            {0,h,0, 0,0,0, 0,0,1, 0,h,1, -1,0,0},{1,h,1, 1,0,1, 1,0,0, 1,h,0, 1,0,0}};
        for(int f=0;f<faces.length;f++){
            float[] q=faces[f];float bottom=f<2?v1:side;
            float[][] uv={{u0,v0},{u0,bottom},{u1,bottom},{u1,v0}};
            for(int i=0;i<4;i++)c.vertex(last.pose(),q[i*3],q[i*3+1],q[i*3+2]).color(r,g,b,a).uv(uv[i][0],uv[i][1]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(last.normal(),q[12],q[13],q[14]).endVertex();
        }
    }
    @Override public ResourceLocation getTextureLocation(PropEntity entity){return TextureAtlas.LOCATION_BLOCKS;}
    @Override public boolean shouldRender(PropEntity entity,net.minecraft.client.renderer.culling.Frustum frustum,double x,double y,double z){return entity.marker()||PropObjectRenderer.beaming(entity)||super.shouldRender(entity,frustum,x,y,z);}
}
