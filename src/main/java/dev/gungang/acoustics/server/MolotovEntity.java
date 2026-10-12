package dev.gungang.acoustics.server;

import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

public final class MolotovEntity extends ThrowableItemProjectile {
    public MolotovEntity(EntityType<? extends MolotovEntity> type,Level level) {super(type,level);}
    @Override protected Item getDefaultItem() {return GadgetItems.MOLOTOV.get();}
    @Override protected float getGravity() {return .05f;}
    @Override protected boolean canHitEntity(Entity entity) {return false;}
    @Override protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        if(level().isClientSide)return;
        var effect=GadgetItems.ORDNANCE.get().create(level());
        if(effect!=null) {
            var point=hit.getLocation().add(net.minecraft.world.phys.Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(.02));
            effect.setPos(point);effect.setup(getOwner(),false);level().addFreshEntity(effect);
        }
        level().playSound(null,blockPosition(),net.minecraft.sounds.SoundEvents.SPLASH_POTION_BREAK,net.minecraft.sounds.SoundSource.PLAYERS,1,1);
        ((net.minecraft.server.level.ServerLevel)level()).sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,getX(),getY(),getZ(),35,.6,.2,.6,.08);
        discard();
    }
    @Override public void tick() {super.tick();if(!level().isClientSide&&tickCount>200)discard();}
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {
        return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);
    }
}
