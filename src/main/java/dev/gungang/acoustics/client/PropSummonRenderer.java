package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.gungang.acoustics.server.PropSummonEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import java.util.WeakHashMap;

/** Vanilla models only: these client-side render proxies are never added to a world. */
public final class PropSummonRenderer extends EntityRenderer<PropSummonEntity> {
    private final WeakHashMap<PropSummonEntity,Mob> visuals=new WeakHashMap<>();
    public PropSummonRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=.3f;}
    private Mob visual(PropSummonEntity e){return visuals.computeIfAbsent(e,key->switch(e.kind()){
        case SPIDER->EntityType.CAVE_SPIDER.create(e.level());case BEE->EntityType.BEE.create(e.level());case SILVERFISH->EntityType.SILVERFISH.create(e.level());
    });}
    @Override public void render(PropSummonEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        var v=visual(e);if(v==null)return;
        v.setPos(e.position());v.tickCount=e.tickCount;v.setYRot(e.getYRot());v.yRotO=e.yRotO;
        v.yBodyRot=e.getYRot();v.yBodyRotO=e.yRotO;v.yHeadRot=e.getYRot();v.yHeadRotO=e.yRotO;
        v.setXRot(e.getXRot());v.xRotO=e.xRotO;v.hurtTime=e.hurtTime;v.deathTime=e.deathTime;
        v.setOnGround(e.onGround());v.setDeltaMovement(e.getDeltaMovement());
        v.walkAnimation.update((float)Math.min(1,e.getDeltaMovement().horizontalDistance()*4),.4f);
        if(v instanceof net.minecraft.world.entity.animal.Bee bee)bee.setRemainingPersistentAngerTime(200);
        entityRenderDispatcher.getRenderer(v).render(v,yaw,partial,pose,buffers,light);
        super.render(e,yaw,partial,pose,buffers,light);
    }
    @Override public ResourceLocation getTextureLocation(PropSummonEntity e){return entityRenderDispatcher.getRenderer(visual(e)).getTextureLocation(visual(e));}
}
