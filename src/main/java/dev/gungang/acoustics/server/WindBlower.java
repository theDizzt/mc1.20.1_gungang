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
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 35 classic blower ({@code bf1:wex}) right click: an air cannon. Spends cannonAmmoCost rounds from the magazine,
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
    private static final Map<LivingEntity,Push> PUSHES=new WeakHashMap<>();

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
        var direction=new Vec3(look.x,0,look.z);
        if(direction.lengthSqr()<1e-6)direction=Vec3.directionFromRotation(0,owner.getYRot());
        direction=direction.normalize();
        var area=new net.minecraft.world.phys.AABB(eye,end).inflate(radius+1);
        for(var victim:level.getEntitiesOfClass(LivingEntity.class,area,e->e!=owner&&e.isAlive()&&!e.isSpectator()&&CombatRules.target(e))){
            var box=victim.getBoundingBox().inflate(radius);
            if(!box.contains(eye)&&box.clip(eye,end).isEmpty())continue;
            if(LobbyManager.respawning(victim.getUUID())||SafeZones.contains(victim))continue;
            if(damage>0&&!(victim instanceof Player other&&!owner.canHarmPlayer(other))){
                boolean down=dead(victim);
                victim.invulnerableTime=0;
                // Our own push below replaces the hit's vanilla knockback.
                CombatRules.quietly(()->victim.hurt(owner.damageSources().playerAttack(owner),damage));
                if(!down&&dead(victim))GungangNetwork.send(owner,new GungangNetwork.KillSound("tacz:kill"));
            }
            if(dead(victim))continue;
            double strength=CombatRules.HIT_STRENGTH*config.cannonKnockbackMultiplier();
            if(strength>0){PUSHES.put(victim,new Push(strength,direction));DamageCredit.wind(victim,owner);}
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
    private static boolean dead(LivingEntity target){return target.isDeadOrDying()||target instanceof Player&&LobbyManager.respawning(target.getUUID());}

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||PUSHES.isEmpty())return;
        var due=new ArrayList<>(PUSHES.entrySet());PUSHES.clear();
        for(var entry:due){
            var victim=entry.getKey();var push=entry.getValue();
            if(!victim.isAlive()||victim.isRemoved()||victim.isSpectator()||LobbyManager.respawning(victim.getUUID())||SafeZones.contains(victim))continue;
            if(victim instanceof ServerPlayer p&&CombatGadgets.shieldBlocked(p))continue;
            double strength=push.strength()*(1-victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            if(strength<=0)continue;
            // Vanilla's knockback shape, along the shot instead of away from the attacker.
            var old=victim.getDeltaMovement();var d=push.direction().scale(strength);
            var motion=new Vec3(old.x/2+d.x,victim.onGround()?Math.min(.4,old.y/2+strength):old.y,old.z/2+d.z);
            if(victim instanceof ServerPlayer player)PlayerImpulse.apply(player,motion);
            else{victim.setDeltaMovement(motion);victim.hasImpulse=true;victim.hurtMarked=true;}
        }
    }
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event){LAST.clear();PUSHES.clear();}
    private WindBlower(){}
}
