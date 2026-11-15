package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import java.util.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 35 classic blower ({@code bf1:wex}). Left click blows a cone of wind instead of bullets: TaCZ still fires (ammo,
 * sound, muzzle), but its rounds never fly. While the user blows, everything inside the cone (windRange blocks long,
 * windConeAngle degrees off the aim, not behind walls) accelerates by windStrength x 0.1 blocks/tick each tick along
 * the aim, until its speed along the aim reaches windMaxSpeed. Teammates and enemies alike.
 * <p>Right click: an air cannon. Spends cannonAmmoCost rounds from the magazine,
 * throws the user back by cannonSelfKnockback, and everyone in a cannonRadius-wide line of cannonRange blocks takes
 * cannonDamage and cannonKnockbackMultiplier × a normal hit's knockback ({@link ExtraGadgetConfig.WindBlower}).
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class WindBlower {
    public static final String GUN_ID="bf1:wex";
    private static final String AMMO="GunCurrentAmmoCount";
    private static final Map<UUID,Long> LAST=new HashMap<>();
    private record Push(double strength,Vec3 direction) { }
    /** Pushes applied after entity ticking, so damage handlers and AI cannot overwrite them. */
    private static final Map<net.minecraft.world.entity.Entity,Push> PUSHES=new WeakHashMap<>();
    /** Game tick each blowing user last fired a round. */
    private static final Map<LivingEntity,Long> BLOWING=new WeakHashMap<>();
    /** A user blows from the tick of a round until two ticks after it (666 RPM fires every one or two ticks). */
    private static final int BLOW_TICKS=2;

    public static boolean is(ItemStack stack){var tag=stack==null?null:stack.getTag();return tag!=null&&GUN_ID.equals(tag.getString("GunId"));}

    public static void fire(ServerPlayer owner){
        var stack=owner.getMainHandItem();
        if(!is(stack)||!owner.isAlive()||owner.isSpectator()||LobbyManager.respawning(owner.getUUID())||FreeForAllMatch.blocked(owner)||SafeZones.contains(owner))return;
        var config=ExtraGadgetConfig.windBlower;
        long now=owner.level().getGameTime();
        Long last=LAST.get(owner.getUUID());
        if(last!=null&&now>=last&&now-last<ExtraGadgetConfig.ticks(config.cannonCooldownSeconds()))return;
        var tag=stack.getOrCreateTag();
        int ammo=tag.getInt(AMMO),cost=config.cannonAmmoCost();
        if(!owner.getAbilities().instabuild){
            if(ammo<cost){owner.displayClientMessage(Component.literal("공기대포 탄약 부족 ("+ammo+"/"+cost+")"),true);return;}
            tag.putInt(AMMO,ammo-cost);
            owner.inventoryMenu.broadcastChanges();
        }
        LAST.put(owner.getUUID(),now);
        var level=(ServerLevel)owner.level();
        var look=owner.getLookAngle();var eye=owner.getEyePosition();
        // The user is thrown back along the shot.
        if(config.cannonSelfKnockback()>0)PlayerImpulse.apply(owner,owner.getDeltaMovement().add(look.scale(-config.cannonSelfKnockback())));
        var wall=level.clip(new ClipContext(eye,eye.add(look.scale(config.cannonRange())),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner));
        var end=wall.getType()==HitResult.Type.BLOCK?wall.getLocation():eye.add(look.scale(config.cannonRange()));
        double radius=config.cannonRadius();
        float damage=(float)(config.cannonDamage()*AdrenalineDamage.multiplier(owner));
        var direction=look.normalize();
        var area=new net.minecraft.world.phys.AABB(eye,end).inflate(radius+1);
        for(var victim:level.getEntities(owner,area,e->e.isAlive()&&!e.isSpectator())){
            var box=victim.getBoundingBox().inflate(radius);
            if(!box.contains(eye)&&box.clip(eye,end).isEmpty())continue;
            if(LobbyManager.respawning(victim.getUUID())||SafeZones.contains(victim))continue;
            if(damage>0&&victim instanceof LivingEntity living&&CombatRules.target(living)&&!(victim instanceof Player other&&!owner.canHarmPlayer(other))){
                boolean down=dead(victim);
                victim.invulnerableTime=0;
                // Our own push below replaces the hit's vanilla knockback.
                CombatRules.quietly(()->victim.hurt(owner.damageSources().playerAttack(owner),damage));
                if(!down&&dead(victim))GungangNetwork.send(owner,new GungangNetwork.KillSound("tacz:kill"));
            }
            if(dead(victim))continue;
            double strength=CombatRules.HIT_STRENGTH*config.cannonKnockbackMultiplier();
            if(strength>0){PUSHES.put(victim,new Push(strength,direction));if(victim instanceof LivingEntity living)DamageCredit.wind(living,owner);}
        }
        // A puff of air along the shot.
        var step=end.subtract(eye);int points=Math.max(2,(int)(step.length()*2));
        for(int i=1;i<=points;i++){
            var at=eye.add(step.scale(i/(double)points)).add(0,-.2,0);
            level.sendParticles(ParticleTypes.CLOUD,at.x,at.y,at.z,2,radius*.3,radius*.3,radius*.3,.02);
        }
        level.sendParticles(ParticleTypes.POOF,end.x,end.y,end.z,8,.3,.3,.3,.05);
        level.playSound(null,owner.getX(),owner.getY(),owner.getZ(),SoundEvents.GENERIC_EXPLODE,SoundSource.PLAYERS,.6f,1.6f);
    }
    static boolean round(net.minecraft.world.entity.Entity entity){return entity instanceof Las5Damage.BulletAccess bullet&&GUN_ID.equals(String.valueOf(bullet.getGunId()));}
    /** A fired round never flies: the user blows the wind cone instead. */
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void fired(EntityJoinLevelEvent event){
        if(event.getLevel().isClientSide()||!round(event.getEntity()))return;
        event.setCanceled(true);
        if(!(event.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile round)||!(round.getOwner() instanceof LivingEntity owner)
                ||!(event.getLevel() instanceof ServerLevel level))return;
        long now=level.getGameTime();
        if(Long.valueOf(now).equals(BLOWING.put(owner,now)))return;
        gust(level,owner);
    }
    /** Wind strength of the loaded blower (weapon setting windStrength, 1 by default). */
    static double strength(){
        var loaded=LobbyManager.loaded();
        if(loaded!=null)for(var weapon:loaded.primary())if(weapon.itemNbt().contains(GUN_ID))return weapon.windStrength();
        return 1;
    }
    /** The motion the target really has: a server player's own motion is stale, so it is read from its movement. */
    private static Vec3 motion(net.minecraft.world.entity.Entity target){
        return target instanceof ServerPlayer?new Vec3(target.getX()-target.xo,target.getY()-target.yo,target.getZ()-target.zo):target.getDeltaMovement();
    }
    private static double slope(){return Math.tan(Math.toRadians(Math.max(0,Math.min(89,ExtraGadgetConfig.windBlower.windConeAngle()))));}
    private static void blow(ServerLevel level,LivingEntity owner,double acceleration,double maxSpeed){
        double range=ExtraGadgetConfig.windBlower.windRange();
        if(range<=0)return;
        var eye=owner.getEyePosition();var look=owner.getLookAngle().normalize();
        double slope=slope();
        var end=eye.add(look.scale(range));
        var area=new AABB(eye,end).inflate(range*slope+1);
        for(var target:level.getEntities(owner,area,e->e.isAlive()&&!e.isSpectator()&&e.isPickable())){
            if(target.isPassengerOfSameVehicle(owner)||owner.getVehicle()==target)continue;
            if(LobbyManager.respawning(target.getUUID())||SafeZones.contains(target))continue;
            if(target instanceof ServerPlayer p&&CombatGadgets.shieldBlocked(p))continue;
            // The point of the body nearest the wind's axis.
            var box=target.getBoundingBox();var center=box.getCenter();
            double t=Math.max(0,Math.min(range,center.subtract(eye).dot(look)));
            var axis=eye.add(look.scale(t));
            var nearest=new Vec3(Math.max(box.minX,Math.min(box.maxX,axis.x)),Math.max(box.minY,Math.min(box.maxY,axis.y)),Math.max(box.minZ,Math.min(box.maxZ,axis.z)));
            var offset=nearest.subtract(eye);double along=offset.dot(look);
            if(along<0||along>range||offset.subtract(look.scale(along)).length()>along*slope+.3)continue;
            // Walls block the wind.
            if(level.clip(new ClipContext(eye,nearest,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner)).getType()!=HitResult.Type.MISS)continue;
            var old=motion(target);double speed=old.dot(look);
            if(speed>=maxSpeed)continue;
            // All three components of the aim, vertical included.
            var motion=old.add(look.scale(Math.min(acceleration,maxSpeed-speed)));
            if(target instanceof ServerPlayer player)PlayerImpulse.apply(player,motion);
            else{target.setDeltaMovement(motion);target.hasImpulse=true;target.hurtMarked=true;}
            if(target instanceof LivingEntity living)DamageCredit.wind(living,owner);
        }
    }
    /** A few gusts of air leaving the nozzle along the cone. */
    private static void gust(ServerLevel level,LivingEntity owner){
        var look=owner.getLookAngle().normalize();var start=owner.getEyePosition().add(look.scale(.9)).add(0,-.25,0);
        double spread=slope(),speed=Math.max(.05,ExtraGadgetConfig.windBlower.windRange()/22);
        var random=level.random;
        for(int i=0;i<3;i++){
            var d=look.add((random.nextDouble()*2-1)*spread,(random.nextDouble()*2-1)*spread,(random.nextDouble()*2-1)*spread).normalize();
            level.sendParticles(ParticleTypes.CLOUD,start.x,start.y,start.z,0,d.x,d.y,d.z,speed*(.7+random.nextDouble()*.5));
        }
    }
    private static boolean dead(net.minecraft.world.entity.Entity target){return !target.isAlive()||target instanceof Player&&LobbyManager.respawning(target.getUUID());}

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        if(!BLOWING.isEmpty()){
            double acceleration=strength()*.1,maxSpeed=ExtraGadgetConfig.windBlower.windMaxSpeed();
            for(var entry:new ArrayList<>(BLOWING.entrySet())){
                var owner=entry.getKey();
                if(owner.isRemoved()||!(owner.level() instanceof ServerLevel level)||level.getGameTime()-entry.getValue()>BLOW_TICKS){BLOWING.remove(owner);continue;}
                if(!owner.isAlive()||owner.isSpectator()||LobbyManager.respawning(owner.getUUID())||SafeZones.contains(owner))continue;
                if(acceleration>0&&maxSpeed>0)blow(level,owner,acceleration,maxSpeed);
            }
        }
        if(PUSHES.isEmpty())return;
        var due=new ArrayList<>(PUSHES.entrySet());PUSHES.clear();
        for(var entry:due){
            var victim=entry.getKey();var push=entry.getValue();
            if(!victim.isAlive()||victim.isRemoved()||victim.isSpectator()||LobbyManager.respawning(victim.getUUID())||SafeZones.contains(victim))continue;
            if(victim instanceof ServerPlayer p&&CombatGadgets.shieldBlocked(p))continue;
            double strength=push.strength()*(victim instanceof LivingEntity living?1-living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE):1);
            if(strength<=0)continue;
            // Preserve all three components of the wind, including vertical aim.
            var old=victim.getDeltaMovement();var d=push.direction().scale(strength);
            var motion=old.add(d);
            if(victim instanceof ServerPlayer player)PlayerImpulse.apply(player,motion);
            else{victim.setDeltaMovement(motion);victim.hasImpulse=true;victim.hurtMarked=true;}
        }
    }
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event){LAST.clear();PUSHES.clear();BLOWING.clear();}
    private WindBlower(){}
}
