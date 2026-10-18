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
    private final java.util.Map<PropEntity,java.util.Map<net.minecraft.core.BlockPos,net.minecraft.world.level.block.entity.BlockEntity>> blockEntities=new java.util.WeakHashMap<>();
    public PropRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=0;}
    @Override public void render(PropEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        var mc=Minecraft.getInstance();var state=entity.state();pose.pushPose();
        if(entity.item().equals("gungang:landmine")||entity.item().equals("minecraft:barrier")){
            pose.translate(0,.075,0);pose.scale(.7f,.7f,.7f);
            var item=net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation(entity.item()));
            mc.getItemRenderer().renderStatic(new ItemStack(item),ItemDisplayContext.GROUND,light,OverlayTexture.NO_OVERLAY,pose,buffers,entity.level(),entity.getId());
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
            var cache=blockEntities.computeIfAbsent(entity,u->new java.util.HashMap<>());
            for(var part:dev.gungang.acoustics.server.PropGeometry.parts(state)){
                pose.pushPose();pose.translate(part.offset().getX(),part.offset().getY(),part.offset().getZ());var partState=part.state();
                // Emissive blocks (campfire, glowstone, lanterns) stay bright in the dark like the real block.
                int partLight=LightTexture.pack(Math.max(LightTexture.block(light),partState.getLightEmission()),LightTexture.sky(light));
                if(partState.getBlock() instanceof EntityBlock block){
                    var be=cache.get(part.offset());
                    if(be==null||be.getBlockState().getBlock()!=partState.getBlock()){be=block.newBlockEntity(entity.blockPosition().offset(part.offset()),partState);if(be!=null){be.setLevel(entity.level());cache.put(part.offset(),be);}}
                    if(be!=null){be.setBlockState(partState);if(be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest){chest.triggerEvent(1,entity.open()?1:0);net.minecraft.world.level.block.entity.ChestBlockEntity.lidAnimateTick(entity.level(),be.getBlockPos(),partState,chest);}mc.getBlockEntityRenderDispatcher().renderItem(be,pose,buffers,partLight,OverlayTexture.NO_OVERLAY);}
                }
                if(partState.getRenderShape()==RenderShape.MODEL)mc.getBlockRenderer().renderSingleBlock(partState,pose,buffers,partLight,OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
        }
        pose.popPose();
        if(entity.marker())renderNameTag(entity,entity.getDisplayName(),pose,buffers,light);
    }
    @Override public ResourceLocation getTextureLocation(PropEntity entity){return TextureAtlas.LOCATION_BLOCKS;}
    @Override public boolean shouldRender(PropEntity entity,net.minecraft.client.renderer.culling.Frustum frustum,double x,double y,double z){return entity.marker()||super.shouldRender(entity,frustum,x,y,z);}
}
