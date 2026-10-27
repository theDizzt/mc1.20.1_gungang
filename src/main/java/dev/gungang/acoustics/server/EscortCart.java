package dev.gungang.acoustics.server;

import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class EscortCart extends Entity {
    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> TRAVEL=net.minecraft.network.syncher.SynchedEntityData.defineId(EscortCart.class,net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    /** Deck height above the cart origin: the top of the gold load players stand on. */
    public static final double DECK=2.5;
    /** Model pieces in cart space (x across, y up, z along travel; -z is the rear), matching EscortRenderer. */
    private static final AABB[] PARTS={
            new AABB(-1.5,.55,-2.5,1.5,2.3,2.5),
            new AABB(-1.3,2.3,-2.3,1.3,DECK,2.3),
            new AABB(-1.44,.06,-2.17,-1.12,.55,-1.33),new AABB(-1.44,.06,1.33,-1.12,.55,2.17),
            new AABB(1.12,.06,-2.17,1.44,.55,-1.33),new AABB(1.12,.06,1.33,1.44,.55,2.17)};
    /** Strip width used to approximate a turned piece with axis-aligned collision boxes. */
    private static final double STRIP=1/16.0;
    private List<AABB> solids=List.of();
    private double solidX=Double.NaN,solidY,solidZ;
    private float solidYaw;
    public EscortCart(EntityType<? extends EscortCart> type,Level level){super(type,level);noPhysics=true;setInvulnerable(true);}
    @Override protected void defineSynchedData(){entityData.define(TRAVEL,0f);}
    public float travel(){return entityData.get(TRAVEL);}
    @Override protected void readAdditionalSaveData(CompoundTag tag){}
    @Override protected void addAdditionalSaveData(CompoundTag tag){}
    @Override public boolean shouldBeSaved(){return false;}
    /** Collision comes from the model pieces (collisions), not the turned bounding box. */
    @Override public boolean canBeCollidedWith(){return false;}
    /** Kept so projectile searches find the cart; their hits are narrowed to the model with clip. */
    @Override public boolean isPickable(){return true;}
    @Override public boolean isPushable(){return false;}
    /** Everyone sees the payload through walls, whatever their team. */
    @Override public boolean isCurrentlyGlowing(){return true;}
    @Override protected AABB makeBoundingBox(){
        double angle=Math.toRadians(getYRot()),x=Math.abs(Math.cos(angle))*1.5+Math.abs(Math.sin(angle))*2.5,z=Math.abs(Math.sin(angle))*1.5+Math.abs(Math.cos(angle))*2.5;
        return new AABB(getX()-x,getY(),getZ()-z,getX()+x,getY()+DECK,getZ()+z);
    }
    private Vec3 local(Vec3 world){
        double a=Math.toRadians(getYRot()),cos=Math.cos(a),sin=Math.sin(a),dx=world.x-getX(),dz=world.z-getZ();
        return new Vec3(dx*cos+dz*sin,world.y-getY(),-dx*sin+dz*cos);
    }
    private Vec3 world(double x,double y,double z){
        double a=Math.toRadians(getYRot()),cos=Math.cos(a),sin=Math.sin(a);
        return new Vec3(getX()+x*cos-z*sin,getY()+y,getZ()+x*sin+z*cos);
    }
    /** Axis-aligned world boxes covering exactly the model; a turned cart is sliced into thin strips. */
    public List<AABB> solids(){
        if(getX()==solidX&&getY()==solidY&&getZ()==solidZ&&getYRot()==solidYaw)return solids;
        var out=new ArrayList<AABB>();
        for(var part:PARTS){
            Vec3[] c={world(part.minX,0,part.minZ),world(part.maxX,0,part.minZ),world(part.maxX,0,part.maxZ),world(part.minX,0,part.maxZ)};
            double minX=Double.MAX_VALUE,maxX=-Double.MAX_VALUE;
            for(var v:c){minX=Math.min(minX,v.x);maxX=Math.max(maxX,v.x);}
            double y0=getY()+part.minY,y1=getY()+part.maxY;
            // Right angles map onto a single exact box.
            if(Math.abs(c[1].x-c[0].x)<1e-6||Math.abs(c[1].z-c[0].z)<1e-6){
                double minZ=Double.MAX_VALUE,maxZ=-Double.MAX_VALUE;for(var v:c){minZ=Math.min(minZ,v.z);maxZ=Math.max(maxZ,v.z);}
                out.add(new AABB(minX,y0,minZ,maxX,y1,maxZ));continue;
            }
            for(double a=minX;a<maxX;a+=STRIP){
                double b=Math.min(maxX,a+STRIP),lo=Double.MAX_VALUE,hi=-Double.MAX_VALUE;
                for(int i=0;i<4;i++){
                    var p=c[i];var q=c[(i+1)%4];
                    if(p.x>=a&&p.x<=b){lo=Math.min(lo,p.z);hi=Math.max(hi,p.z);}
                    for(double edge:new double[]{a,b})if((p.x-edge)*(q.x-edge)<0){double z=p.z+(q.z-p.z)*(edge-p.x)/(q.x-p.x);lo=Math.min(lo,z);hi=Math.max(hi,z);}
                }
                if(hi>lo)out.add(new AABB(a,y0,lo,b,y1,hi));
            }
        }
        solidX=getX();solidY=getY();solidZ=getZ();solidYaw=getYRot();
        return solids=List.copyOf(out);
    }
    /** Collision shapes from every cart near the moving entity's swept box. */
    public static List<VoxelShape> collisions(Level level,Entity entity,AABB box){
        if(entity instanceof EscortCart)return List.of();
        var out=new ArrayList<VoxelShape>();
        for(var cart:level.getEntitiesOfClass(EscortCart.class,box))for(var solid:cart.solids())if(solid.intersects(box))out.add(Shapes.create(solid));
        return out;
    }
    /** Nearest point where a segment enters the model, for bullets and projectiles. */
    public Optional<Vec3> clip(Vec3 from,Vec3 to){
        Vec3 a=local(from),b=local(to),best=null;
        for(var part:PARTS){var hit=part.clip(a,b);if(hit.isPresent()&&(best==null||hit.get().distanceToSqr(a)<best.distanceToSqr(a)))best=hit.get();}
        return best==null?Optional.empty():Optional.of(world(best.x,best.y,best.z));
    }
    /** Standing on the deck: shared by the server (fall reset) and the client (carrying), so both agree on who rides. */
    public boolean carries(net.minecraft.world.entity.player.Player p){
        var b=p.getBoundingBox();
        if(!p.isAlive()||p.isSpectator()||p.getY()<getY()+DECK-.2||p.getY()>getY()+DECK+.3||p.getDeltaMovement().y>.1)return false;
        var feet=new AABB(b.minX-.1,getY()+DECK-.25,b.minZ-.1,b.maxX+.1,getY()+DECK,b.maxZ+.1);
        return solids().stream().anyMatch(feet::intersects);
    }
    /** On the rear ladder: behind the back wall, within the ladder's width, between the ground and the deck. */
    public boolean ladder(Entity e){
        var l=local(e.position());
        return Math.abs(l.x)<=.6&&l.z>=-3.0&&l.z<=-2.45&&l.y>=-.2&&l.y<=DECK+.1;
    }
    public static boolean onLadder(LivingEntity e){
        if(!(e instanceof net.minecraft.world.entity.player.Player)||e.isSpectator())return false;
        for(var cart:e.level().getEntitiesOfClass(EscortCart.class,e.getBoundingBox().inflate(1)))if(cart.ladder(e))return true;
        return false;
    }
    /**
     * Shortest way out of the cart body for a player it moved into, in world space; null when not inside.
     * Stepping up onto the deck wins when that is short.
     */
    public Vec3 pushOut(AABB body){
        Vec3 l=local(body.getCenter());double half=body.getXsize()/2,hx=1.5+half,hz=2.5+half,feet=body.minY-getY();
        if(Math.abs(l.x)>=hx||Math.abs(l.z)>=hz||body.maxY-getY()<=.55||feet>=DECK)return null;
        double east=hx-l.x,west=hx+l.x,south=hz-l.z,north=hz+l.z,up=DECK-feet;
        double side=Math.min(Math.min(east,west),Math.min(south,north));
        if(up<=.6&&up<=side)return new Vec3(0,up+.01,0);
        double x=side==east?east+.01:side==west?-west-.01:0,z=x!=0?0:side==south?south+.01:-north-.01;
        return world(x,0,z).subtract(getX(),getY(),getZ());
    }
    /** Riders are carried by their own client (EscortRiding); teleporting them here would freeze their movement and aim while acks are pending. */
    public void advance(Vec3 next,Vec3 direction){
        var old=position();double oldYaw=getYRot(),yaw=oldYaw+net.minecraft.util.Mth.wrapDegrees(Math.toDegrees(Math.atan2(-direction.x,direction.z))-oldYaw);
        entityData.set(TRAVEL,travel()+(float)old.distanceTo(next));
        for(var p:level().getEntitiesOfClass(net.minecraft.server.level.ServerPlayer.class,getBoundingBox().inflate(.1,.3,.1),this::carries))p.fallDistance=0;
        setYRot((float)yaw);setPos(next);setBoundingBox(makeBoundingBox());
    }
    @Override public void tick(){super.tick();if(!level().isClientSide&&!MatchManager.isEscortCart(this))discard();}
    @Override public void lerpTo(double x,double y,double z,float yaw,float pitch,int steps,boolean teleport){setYRot(getYRot()+net.minecraft.util.Mth.wrapDegrees(yaw-getYRot()));setXRot(pitch);setPos(x,y,z);setBoundingBox(makeBoundingBox());}
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket(){return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);}
}
