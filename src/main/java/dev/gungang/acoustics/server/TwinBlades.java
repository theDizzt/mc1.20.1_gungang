package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 31 twin blades (TaCZ gun {@code katana:tss}). The gun pack swings by firing a fan of 27 invisible bullets per motion;
 * here those bullets never spawn (so no bullet holes in blocks and no headshots). Each volley instead strikes once as
 * a blunt melee blow in front of the wielder, with the damage of its motion in the six-motion combo
 * ({@link ExtraGadgetConfig.TwinBlades}). Motion 5 and 6 fire two volleys at once and still strike once.
 * While guarding (right click: aim) the blades cannot attack, hits from the front lose guardReductionPercent (see
 * {@link #guard}) and the wielder moves at guardMovementSpeed % instead of the weapon's own speed. A kill plays TaCZ's
 * default kill sound.
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class TwinBlades {
    public static final String GUN_ID="katana:tss";
    /** The gun script restarts its combo after the fire interval (0.4 s) plus 3 s without a swing. */
    static final int COMBO_RESET_TICKS=68;
    public static final UUID GUARD_SPEED=UUID.fromString("5d0e7a3c-2b8f-4c41-9e36-7f1a0c9b2d64");
    private record Swing(int motion,long tick) { }
    private static final Map<UUID,Swing> LAST=new HashMap<>();
    private static java.lang.reflect.Method operator,aiming;
    private static boolean aimUnavailable;
    private static Loadouts indexed;
    private static double weaponSpeed=100;

    public static boolean is(ItemStack stack){var tag=stack==null?null:stack.getTag();return tag!=null&&GUN_ID.equals(tag.getString("GunId"));}
    static boolean aiming(LivingEntity entity){
        if(aimUnavailable)return false;
        try{
            if(operator==null){
                var type=Class.forName("com.tacz.guns.api.entity.IGunOperator");
                operator=type.getMethod("fromLivingEntity",LivingEntity.class);aiming=type.getMethod("getSynIsAiming");
            }
            return Boolean.TRUE.equals(aiming.invoke(operator.invoke(null,entity)));
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){aimUnavailable=true;return false;}
    }
    /** Right-click guard with the twin blades: no attacks meanwhile. Both sides use this. */
    public static boolean guarding(LivingEntity entity){return entity!=null&&is(entity.getMainHandItem())&&aiming(entity);}

    /** The pack's slash bullets: cancelled, and the first of each volley strikes instead. */
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void bullet(EntityJoinLevelEvent event){
        if(event.getLevel().isClientSide()||!(event.getEntity() instanceof Las5Damage.BulletAccess bullet)||!GUN_ID.equals(String.valueOf(bullet.getGunId())))return;
        event.setCanceled(true);
        if(event.getEntity() instanceof Projectile projectile&&projectile.getOwner() instanceof LivingEntity owner)swing(owner);
    }
    private static void swing(LivingEntity owner){
        if(!owner.isAlive()||owner.isSpectator()||guarding(owner))return;
        long now=owner.level().getGameTime();var last=LAST.get(owner.getUUID());
        if(last!=null&&last.tick()==now)return;
        int motion=last==null||now-last.tick()>COMBO_RESET_TICKS?1:last.motion()%6+1;
        LAST.put(owner.getUUID(),new Swing(motion,now));
        strike(owner,motion);
    }
    private static void strike(LivingEntity owner,int motion){
        if(owner instanceof Player p&&(LobbyManager.respawning(p.getUUID())||FreeForAllMatch.blocked(p)||SafeZones.contains(p)))return;
        var config=ExtraGadgetConfig.twinBlades;var level=owner.level();
        float damage=(float)(config.damage(motion)*AdrenalineDamage.multiplier(owner));
        double range=config.range(),half=Math.toRadians((motion==6?config.thrustAngle():config.slashAngle())/2);
        var eye=owner.getEyePosition();var look=owner.getLookAngle();
        var wall=level.clip(new ClipContext(eye,eye.add(look.scale(range)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner));
        boolean struck=false;
        for(var target:level.getEntitiesOfClass(LivingEntity.class,owner.getBoundingBox().inflate(range+1),e->e!=owner&&e.isAlive()&&!e.isSpectator()&&e.isPickable())){
            var box=target.getBoundingBox();
            var near=new Vec3(Math.max(box.minX,Math.min(eye.x,box.maxX)),Math.max(box.minY,Math.min(eye.y,box.maxY)),Math.max(box.minZ,Math.min(eye.z,box.maxZ)));
            var to=near.subtract(eye);
            if(to.lengthSqr()>range*range)continue;
            if(to.lengthSqr()>1e-6&&angle(look,to)>half&&angle(look,box.getCenter().subtract(eye))>half)continue;
            if(!owner.hasLineOfSight(target)||SafeZones.contains(target))continue;
            if(owner instanceof Player p&&target instanceof Player victim&&!p.canHarmPlayer(victim))continue;
            boolean down=dead(target);
            DamageSource source=owner instanceof Player p?owner.damageSources().playerAttack(p):owner.damageSources().mobAttack(owner);
            target.invulnerableTime=0;
            if(!target.hurt(source,damage))continue;
            struck=true;
            if(!down&&dead(target)&&owner instanceof ServerPlayer player)GungangNetwork.send(player,new GungangNetwork.KillSound("tacz:kill"));
        }
        // Gimmicks in reach (gas barrels, UXOs, cracked walls) take the blow too.
        if(wall.getType()==HitResult.Type.BLOCK)GimmickEntity.hit(level,wall.getBlockPos(),damage);
        level.playSound(null,owner.blockPosition(),struck?net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_STRONG:net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP,
                net.minecraft.sounds.SoundSource.PLAYERS,.8f,motion==6?.9f:1.15f);
    }
    private static double angle(Vec3 look,Vec3 to){return Math.acos(Math.max(-1,Math.min(1,look.dot(to.normalize()))));}
    /** Dead, or taken out by the match (players respawn instead of dying). */
    private static boolean dead(LivingEntity target){return target.isDeadOrDying()||target instanceof Player&&LobbyManager.respawning(target.getUUID());}

    /**
     * The guard: a hit from within guardAngle in front loses guardReductionPercent (90 %: a tenth still lands).
     * Explosions, poison, fire, wither and the ARC-12's arc (and its pellets) are never blocked, nor anything without a
     * direction (falls ...).
     */
    @SubscribeEvent(priority=EventPriority.HIGH) public static void guard(net.minecraftforge.event.entity.living.LivingHurtEvent event){
        var victim=event.getEntity();var source=event.getSource();
        if(victim.level().isClientSide||event.getAmount()<=0||!guarding(victim))return;
        if(unblockable(source))return;
        var from=source.getSourcePosition();if(from==null)return;
        var config=ExtraGadgetConfig.twinBlades;
        var look=victim.getLookAngle();var to=from.subtract(victim.getEyePosition());
        double flat=Math.hypot(to.x,to.z),lookFlat=Math.hypot(look.x,look.z);
        if(flat>1e-4&&lookFlat>1e-4){
            double cos=(look.x*to.x+look.z*to.z)/(flat*lookFlat);
            if(Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,cos))))>config.guardAngle()/2)return;
        }
        event.setAmount((float)(event.getAmount()*(1-config.guardReductionPercent()/100)));
        victim.level().playSound(null,victim.blockPosition(),net.minecraft.sounds.SoundEvents.SHIELD_BLOCK,net.minecraft.sounds.SoundSource.PLAYERS,.8f,1.3f);
    }
    static boolean unblockable(DamageSource source){
        return source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)||source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
                ||source.is(net.minecraft.world.damagesource.DamageTypes.WITHER)||source.is(net.minecraft.world.damagesource.DamageTypes.MAGIC)
                ||source.is(net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC)||ArcThrower.pellet(source.getDirectEntity());
    }
    /** The twin blades' configured weapon speed (weapon JSON movementSpeed), for the guard's speed. */
    private static double weaponSpeed(){
        var current=LobbyManager.loaded();
        if(current!=indexed){
            indexed=current;weaponSpeed=100;
            if(current!=null)for(var w:current.primary())try{if(is(w.stack())&&w.movementSpeed()!=null){weaponSpeed=w.movementSpeed();break;}}catch(RuntimeException invalid){}
        }
        return weaponSpeed;
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||event.player.level().isClientSide)return;
        var p=event.player;var speed=p.getAttribute(Attributes.MOVEMENT_SPEED);if(speed==null)return;
        var existing=speed.getModifier(GUARD_SPEED);
        double amount=guarding(p)&&p.isAlive()?ExtraGadgetConfig.twinBlades.guardMovementSpeed()/Math.max(1,weaponSpeed())-1:0;
        if(existing!=null&&existing.getAmount()==amount)return;
        if(existing!=null)speed.removeModifier(GUARD_SPEED);
        if(amount!=0)speed.addTransientModifier(new AttributeModifier(GUARD_SPEED,"GunGang twin blades guard",amount,AttributeModifier.Operation.MULTIPLY_TOTAL));
    }
    @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event){LAST.remove(event.getEntity().getUUID());}
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event){LAST.clear();indexed=null;}
    private TwinBlades(){}
}
