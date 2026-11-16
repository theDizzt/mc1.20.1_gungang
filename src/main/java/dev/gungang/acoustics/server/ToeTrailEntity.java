package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.NetworkHooks;

/** One tracked entity per trail, rather than a separate entity for every spike. */
public final class ToeTrailEntity extends Entity {
    private static final EntityDataAccessor<CompoundTag> SHAPE=SynchedEntityData.defineId(ToeTrailEntity.class,EntityDataSerializers.COMPOUND_TAG);
    private LivingEntity owner;
    private final ArrayList<Vec3> path=new ArrayList<>(); // newest to oldest, quarter-block samples
    private Vec3 last;
    private long lastMoved;
    private int length;
    private record Spike(Vec3 position,int pathIndex,boolean critical){}
    private final ArrayList<Spike> spikes=new ArrayList<>();
    public boolean ownedBy(LivingEntity entity){return owner==entity;}
    public boolean validOwner(){return !isRemoved()&&ToeWeapon.allowed(owner)&&owner.level()==level();}
    public ToeTrailEntity(EntityType<? extends ToeTrailEntity> type,Level level){super(type,level);noPhysics=true;}
    @Override protected void defineSynchedData(){entityData.define(SHAPE,new CompoundTag());}
    public CompoundTag shape(){return entityData.get(SHAPE);}
    public void start(LivingEntity entity){owner=entity;lastMoved=level().getGameTime();updateLength(false);resetPath();}
    private void updateLength(boolean grow){
        var c=ExtraGadgetConfig.toe;var tag=ToeItem.carried(owner).getOrCreateTag();
        long interval=Math.max(1,Math.round(c.growthSeconds()*20));
        long ticks=tag.contains("ToeGrowthTicks")?tag.getLong("ToeGrowthTicks")
                :Math.max(0,tag.getInt("ToeLength")-c.initialLength())*interval;
        ticks=Math.min((long)(c.maximumLength()-c.initialLength())*interval,Math.max(0,ticks)+(grow?1:0));
        tag.putLong("ToeGrowthTicks",ticks);
        length=Math.min(c.maximumLength(),c.initialLength()+(int)(ticks/interval));
        tag.putInt("ToeLength",length);
    }
    private void resetPath(){
        path.clear();last=owner.position();path.add(last);
        var back=Vec3.directionFromRotation(0,owner.getYRot()).scale(-1);
        int initial=ExtraGadgetConfig.toe.initialLength();
        for(int i=1;i<=initial*4;i++){
            var p=last.add(back.scale(i*.25));
            var previous=path.get(path.size()-1);
            var wall=level().clip(new net.minecraft.world.level.ClipContext(previous.add(0,.1,0),p.add(0,.1,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,owner));
            if(wall.getType()!=HitResult.Type.MISS)break;
            path.add(p);
        }
        setPos(last);groundSpikes();sync();
    }
    @Override public void tick(){
        super.tick();if(level().isClientSide)return;
        if(!validOwner()){discard();return;}
        var c=ExtraGadgetConfig.toe;long now=level().getGameTime();
        updateLength(!ToeWeapon.sprinting(owner));
        var position=owner.position();double distance=last.distanceTo(position);
        // Never draw a damaging bridge across a teleport or respawn.
        if(distance>8){lastMoved=now;resetPath();}
        else if(distance>=.25){
            var direction=position.subtract(last).normalize();int steps=(int)(distance/.25);
            for(int i=1;i<=steps;i++)path.add(0,last.add(direction.scale(i*.25)));
            last=last.add(direction.scale(steps*.25));lastMoved=now;
        }
        // Retain older history for subsequent growth instead of replacing bends with straight guesses.
        while(path.size()>c.maximumLength()*4+1)path.remove(path.size()-1);
        while(path.size()<length*4+1){
            var tail=path.get(path.size()-1);
            var direction=path.size()>1?tail.subtract(path.get(path.size()-2)).normalize():Vec3.directionFromRotation(0,owner.getYRot()).scale(-1);
            var next=tail.add(direction.scale(.25));
            var wall=level().clip(new net.minecraft.world.level.ClipContext(tail.add(0,.1,0),next.add(0,.1,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,owner));
            if(wall.getType()!=HitResult.Type.MISS)break;
            path.add(next);
        }
        setPos(position);
        var stack=ToeItem.carried(owner);var tag=stack.getOrCreateTag();
        tag.putInt("ToeLength",length);tag.putInt("ToeMaximum",c.maximumLength());
        tag.putDouble("ToeJumpHeight",c.jumpHeight());
        groundSpikes();
        if(tickCount%2==0)sync();
        int interval=Math.max(1,(int)Math.round(c.damageIntervalSeconds()*20));
        if(tickCount%interval==0)damage(now,interval);
    }
    private void damage(long now,int interval){
        var c=ExtraGadgetConfig.toe;var hits=new HashMap<LivingEntity,Double>();
        if(spikes.isEmpty())return;
        AABB bounds=new AABB(spikes.get(0).position(),spikes.get(0).position());
        for(var spike:spikes)bounds=bounds.minmax(new AABB(spike.position(),spike.position()));
        var candidates=level().getEntitiesOfClass(LivingEntity.class,bounds.inflate(c.spikeWidth()/2,c.spikeHeight(),c.spikeWidth()/2),HiddenWeaponTargets::valid);
        // One world query per damage pulse; overlapping spikes choose the highest DPS, never stack.
        for(var spike:spikes){
            var point=spike.position();double radius=c.spikeWidth()/2;
            var box=new AABB(point.x-radius,point.y,point.z-radius,point.x+radius,point.y+c.spikeHeight(),point.z+radius);
            boolean critical=spike.critical();
            for(var target:candidates){
                if(!box.intersects(target.getBoundingBox()))continue;
                if(SafeZones.contains(target)||FreeForAllMatch.blocked(target)||LobbyManager.respawning(target.getUUID())||LobbyManager.inEitherSpawn(level(),target.getBoundingBox()))continue;
                if(target==owner&&spike.pathIndex()<4&&now-lastMoved<Math.round(c.stationaryGraceSeconds()*20))continue;
                double dps=c.damageAt(length);hits.merge(target,critical?dps*c.criticalMultiplier():dps,Math::max);
            }
        }
        for(var hit:hits.entrySet()){
            if(!validOwner())break;
            var target=hit.getKey();int immunity=target.invulnerableTime;target.invulnerableTime=0;
            // Indirect magic bypasses twin-blade guard; OrdnanceDamage includes allies and the owner.
            OrdnanceDamage.hurt(target,damageSources().indirectMagic(this,owner),(float)(hit.getValue()*interval/20.0));
            if(target.isAlive())target.invulnerableTime=Math.min(immunity,target.invulnerableTime);
        }
    }
    private void groundSpikes(){
        spikes.clear();var c=ExtraGadgetConfig.toe;
        int visible=Math.min(path.size(),length*4+1),samples=(visible+1)/2;
        for(int i=0;i<visible;i+=2){
            var sample=path.get(i);
            if(!level().hasChunkAt(net.minecraft.core.BlockPos.containing(sample)))continue;
            // Vertical collision ray lands on the actual slab/stair top. No ground within four blocks: no spike.
            var hit=level().clip(new net.minecraft.world.level.ClipContext(sample.add(0,.6,0),sample.add(0,-4,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,owner));
            if(hit.getType()!=HitResult.Type.BLOCK||hit.getDirection()!=net.minecraft.core.Direction.UP)continue;
            var p=hit.getLocation();double r=c.spikeWidth()/2;
            var box=new AABB(p.x-r,p.y+.001,p.z-r,p.x+r,p.y+c.spikeHeight(),p.z+r);
            if(LobbyManager.inEitherSpawn(level(),box))continue;
            boolean safe=false;
            for(double x:new double[]{p.x-r,p.x,p.x+r})for(double z:new double[]{p.z-r,p.z,p.z+r})
                if(SafeZones.contains(level(),x,p.y+.01,z)||SafeZones.contains(level(),x,p.y+c.spikeHeight(),z))safe=true;
            if(safe)continue;
            spikes.add(new Spike(p,i,i/2>=Math.max(0,samples-c.criticalLength()*2)));
        }
    }
    private void sync(){
        var data=new CompoundTag();if(owner==null)return;
        data.putUUID("Owner",owner.getUUID());
        data.putDouble("X",getX());data.putDouble("Y",getY());data.putDouble("Z",getZ());
        int count=spikes.size();int[] points=new int[count*3],criticalFlags=new int[count];
        for(int i=0;i<count;i++){var spike=spikes.get(i);var p=spike.position().subtract(position());points[i*3]=(int)Math.round(p.x*64);points[i*3+1]=(int)Math.round(p.y*64);points[i*3+2]=(int)Math.round(p.z*64);criticalFlags[i]=spike.critical()?1:0;}
        data.putIntArray("CriticalFlags",criticalFlags);
        data.putIntArray("Points",points);data.putInt("Critical",Math.min(count,(int)Math.ceil(ExtraGadgetConfig.toe.criticalLength()*2)));
        data.putFloat("Width",(float)ExtraGadgetConfig.toe.spikeWidth());data.putFloat("Height",(float)ExtraGadgetConfig.toe.spikeHeight());
        entityData.set(SHAPE,data);
    }
    @Override public AABB getBoundingBoxForCulling(){return getBoundingBox().inflate(105);}
    @Override public boolean shouldRenderAtSqrDistance(double distance){return distance<192*192;}
    @Override protected void readAdditionalSaveData(CompoundTag tag){discard();}
    @Override protected void addAdditionalSaveData(CompoundTag tag){}
    @Override public boolean shouldBeSaved(){return false;}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
}
