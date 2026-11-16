package dev.gungang.acoustics.server;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

/** 37 glue gun round: a white glob. A direct hit hurts an enemy; wherever it bursts, a glue pool forms on the floor below. */
public final class GlueShotEntity extends ThrowableItemProjectile implements net.minecraftforge.entity.IEntityAdditionalSpawnData {
    public static final DustParticleOptions GLUE=new DustParticleOptions(new org.joml.Vector3f(1,1,1),1.1f);
    private double damage;
    private float gravity=.02f;
    private double range=24;
    private Vec3 origin;

    public GlueShotEntity(EntityType<? extends GlueShotEntity> type,Level level){super(type,level);}
    @Override protected Item getDefaultItem(){return net.minecraft.world.item.Items.SNOWBALL;}
    @Override protected float getGravity(){return gravity;}
    void configure(double damage,float gravity,double range){this.damage=damage;this.gravity=gravity;this.range=Math.max(1,range);origin=position();}
    /** The client simulates the arc between server updates, so it needs the same gravity. */
    @Override public void writeSpawnData(net.minecraft.network.FriendlyByteBuf buffer){buffer.writeFloat(gravity);}
    @Override public void readSpawnData(net.minecraft.network.FriendlyByteBuf buffer){gravity=buffer.readFloat();}

    private boolean ally(Entity entity){
        if(!(getOwner() instanceof LivingEntity owner))return false;
        if(entity==owner)return true;
        if(owner instanceof Player shooter&&entity instanceof Player victim&&!shooter.canHarmPlayer(victim))return true;
        return owner.isAlliedTo(entity);
    }
    /** Direct hits only strike enemies (the pool is what hurts everyone): rounds pass through the owner and allies. */
    @Override protected boolean canHitEntity(Entity entity){
        return super.canHitEntity(entity)&&entity instanceof LivingEntity&&!entity.isSpectator()&&!ally(entity);
    }
    @Override public void tick(){
        super.tick();
        if(level().isClientSide){
            var v=getDeltaMovement();
            for(int i=0;i<2;i++)level().addParticle(GLUE,getX()-v.x*i*.5,getY()-v.y*i*.5,getZ()-v.z*i*.5,0,0,0);
            return;
        }
        // Past its range without hitting anything, the glob is simply gone: no pool.
        if(isAlive()&&origin!=null&&position().distanceToSqr(origin)>=range*range)discard();
        else if(tickCount>200)discard();
    }
    @Override protected void onHitEntity(EntityHitResult result){
        super.onHitEntity(result);
        if(level().isClientSide)return;
        var target=(LivingEntity)result.getEntity();
        if(!SafeZones.contains(target)&&!LobbyManager.respawning(target.getUUID())){
            target.invulnerableTime=0;
            CombatRules.quietly(()->target.hurt(damageSources().thrown(this,getOwner()),(float)damage));
        }
        burst(result.getLocation());
    }
    @Override protected void onHitBlock(BlockHitResult result){
        super.onHitBlock(result);
        if(level().isClientSide)return;
        GimmickEntity.hit(level(),result.getBlockPos(),(float)damage);
        // A floor hit lands on its top; a wall or ceiling hit drips to the floor below.
        if(result.getDirection()==net.minecraft.core.Direction.UP)pool(result.getLocation());
        else burst(result.getLocation().add(Vec3.atLowerCornerOf(result.getDirection().getNormal()).scale(.3)));
    }
    /** The pool goes on the floor under the burst point, if there is one within poolDropDistance. */
    private void burst(Vec3 at){
        var c=ExtraGadgetConfig.glueGun;
        var hit=level().clip(new ClipContext(at.add(0,.05,0),at.subtract(0,c.poolDropDistance(),0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
        if(hit.getType()==HitResult.Type.BLOCK&&hit.getDirection()==net.minecraft.core.Direction.UP)pool(hit.getLocation());
        else splash(at);
        discard();
    }
    private void pool(Vec3 floor){
        GluePoolEntity.spawn(level(),floor,getOwner());
        splash(floor);
        discard();
    }
    private void splash(Vec3 at){
        if(!(level() instanceof net.minecraft.server.level.ServerLevel server))return;
        server.sendParticles(GLUE,at.x,at.y+.1,at.z,14,.35,.1,.35,0);
        server.sendParticles(net.minecraft.core.particles.ParticleTypes.ITEM_SNOWBALL,at.x,at.y+.1,at.z,6,.2,.05,.2,.05);
        server.playSound(null,at.x,at.y,at.z,net.minecraft.sounds.SoundEvents.SLIME_BLOCK_PLACE,net.minecraft.sounds.SoundSource.PLAYERS,.8f,1.2f);
    }
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket(){
        return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);
    }
}
