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
            var model=Minecraft.getInstance().getBlockRenderer().getBlockModel(s);var random=net.minecraft.util.RandomSource.create(42);var found=new java.util.LinkedHashSet<TextureAtlasSprite>();
            var sides=net.minecraft.core.Direction.values();
            for(int i=0;i<=sides.length;i++)for(var quad:model.getQuads(s,i<sides.length?sides[i]:null,random))found.add(quad.getSprite());
            return java.util.List.copyOf(found);
        });
        try{for(var sprite:list)MARK_ACTIVE.invokeExact(sprite);}catch(Throwable ignored){}
    }
    public PropRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=0;}
    @Override public void render(PropEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        var mc=Minecraft.getInstance();var state=entity.state();pose.pushPose();
        if(PropObjectRenderer.render(entity,partial,pose,buffers,light)){
        }else if(entity.item().equals("minecraft:barrier")){
            // Invisible barrier body; only authorized viewers receive a cube in the outline mask.
            if(mc.shouldEntityAppearGlowing(entity)){
                pose.translate(-.5,0,-.5);
                MultiBufferSource outlineOnly=type->buffers.getBuffer(RenderType.outline(TextureAtlas.LOCATION_BLOCKS));
                mc.getBlockRenderer().renderSingleBlock(Blocks.WHITE_CONCRETE.defaultBlockState(),pose,outlineOnly,light,OverlayTexture.NO_OVERLAY);
            }
        }else if(entity.item().equals("minecraft:painting")){
            var variants=entity.level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.PAINTING_VARIANT);
            var list=variants.holders().toList();
            var variant=list.get(Math.floorMod(entity.painting(),list.size()));
            var painting=new net.minecraft.world.entity.decoration.Painting(entity.level(),entity.blockPosition(),net.minecraft.core.Direction.from2DDataValue((entity.rotation()+2)%4),variant);
            pose.translate(0,variant.value().getHeight()/32d,0);
            painting.setPos(entity.position());
            mc.getEntityRenderDispatcher().getRenderer(painting).render(painting,entity.rotation()*90,partial,pose,buffers,light);
        }else{
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
                if(partState.getBlock() instanceof EntityBlock block){
                    // Ticked by PropEffects (book pages, chest lid); the chest lid opens while the prop is open.
                    var be=PropEffects.blockEntity(entity,part.offset(),partState,block);
                    if(be!=null){if(be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest)chest.triggerEvent(1,entity.open()?1:0);mc.getBlockEntityRenderDispatcher().renderItem(be,pose,buffers,partLight,OverlayTexture.NO_OVERLAY);}
                }
                if(partState.getRenderShape()==RenderShape.MODEL){animate(partState);mc.getBlockRenderer().renderSingleBlock(partState,pose,buffers,partLight,OverlayTexture.NO_OVERLAY);}
                pose.popPose();
            }
            if(entity.item().equals("minecraft:beacon")&&entity.open())net.minecraft.client.renderer.blockentity.BeaconRenderer.renderBeaconBeam(pose,buffers,new ResourceLocation("minecraft","textures/entity/beacon_beam.png"),partial,1,entity.level().getGameTime(),0,128,new float[]{.45f,1,.85f},.2f,.25f);
        }
        pose.popPose();
        if(entity.marker())renderNameTag(entity,entity.getDisplayName(),pose,buffers,light);
    }
    @Override public ResourceLocation getTextureLocation(PropEntity entity){return TextureAtlas.LOCATION_BLOCKS;}
    @Override public boolean shouldRender(PropEntity entity,net.minecraft.client.renderer.culling.Frustum frustum,double x,double y,double z){return entity.marker()||super.shouldRender(entity,frustum,x,y,z);}
}
