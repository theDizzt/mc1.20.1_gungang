package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.MagicBookItem.Element;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 37 magic book abilities, five elements (조정관) with a left and a right click each. The client reports which of the two
 * keys are held ({@code bookInput}); everything else runs here, on the server tick:
 * <ul>
 * <li>황 left: ground slam ahead, lifts every enemy in front. Right (held): earthquake around the caster.</li>
 * <li>청 left (held): holy water stream, heals the ally it touches. Right: a water drop that speeds up the ally it hits.</li>
 * <li>적 left: a fireball homing on the enemy nearest the crosshair. Right: a flamethrower for a few seconds.</li>
 * <li>백 left: a light orb of unlimited range that shocks. Right: a series of lightning bolts where the caster looks.</li>
 * <li>흑 left (held): a ruin beam of unlimited range, paid with health. Right (held): flight where the caster looks
 * (the client moves the body while the server allows it, {@link MagicBookItem#flying}).</li>
 * </ul>
 * Projectiles are simulated here, not entities. Numbers: {@link ExtraGadgetConfig.MagicBook}.
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class MagicBook {
    /** A held key the client stopped refreshing counts as released (lost packet, closed game). */
    private static final int HOLD_TIMEOUT=30;
    private static final class State {
        boolean left,right;long refreshed;
        Element channel;boolean channelRight; // the held ability running (quake, holy, ruin, flight)
        double manaDebt,healthDebt;long nextShot;
        long flameEnd;long lightningEnd,nextBolt;int boltsLeft;
        UUID beamTarget;int beamTicks;
    }
    private static final Map<UUID,State> STATES=new HashMap<>();
    private enum Kind {WATER,FIRE,LIGHT}
    private static final class Shot {
        final LivingEntity owner;final ServerLevel level;final Kind kind;Vec3 pos,dir;final double speed;double left;final LivingEntity homing;
        Shot(LivingEntity owner,Kind kind,Vec3 pos,Vec3 dir,double speed,double range,LivingEntity homing){
            this.owner=owner;level=((ServerLevel)owner.level());this.kind=kind;this.pos=pos;this.dir=dir.normalize();this.speed=speed;left=range;this.homing=homing;
        }
    }
    private static final List<Shot> SHOTS=new ArrayList<>();
    private static final DustParticleOptions RUIN=new DustParticleOptions(new org.joml.Vector3f(.35f,.1f,.5f),1.4f);
    private static final DustParticleOptions HOLY=new DustParticleOptions(new org.joml.Vector3f(.55f,.85f,1f),1.1f);

    private static ExtraGadgetConfig.MagicBook config(){return ExtraGadgetConfig.magicBook;}
    private static int ticks(double seconds){return Math.max(1,(int)Math.round(seconds*20));}

    private static void sync(LivingEntity entity){if(entity instanceof ServerPlayer player)player.inventoryMenu.broadcastChanges();}
    /** Bots share spell costs, cooldowns, projectiles and damage with players. */
    static void botCast(BotEntity bot,LivingEntity target){
        if(!usable(bot))return;
        var stack=bot.getMainHandItem();var c=config();long now=bot.level().getGameTime();
        var state=STATES.computeIfAbsent(bot.getUUID(),id->new State());
        double distance=bot.distanceTo(target);
        Element element=distance<=c.slamRange()&&ready(stack,Element.YELLOW,false,now)&&MagicBookItem.mana(stack)>=c.slamMana()
                ?Element.YELLOW:distance<=c.fireballRange()?Element.RED:Element.WHITE;
        stack.getOrCreateTag().putInt(MagicBookItem.ELEMENT,element.ordinal());
        press(bot,stack,state,element,false,now);
    }
    // ---- input ----
    /** The client's held keys: {@code value} "LR" digits, e.g. "10" for the left key alone. */
    public static void input(ServerPlayer p,String value){
        if(value==null||value.length()!=2)return;
        var state=STATES.computeIfAbsent(p.getUUID(),id->new State());
        state.left=value.charAt(0)=='1';state.right=value.charAt(1)=='1';state.refreshed=p.level().getGameTime();
    }
    /** TaCZ fire select key or the mouse wheel: the next (or previous) element. Held abilities stop. */
    public static void cycle(ServerPlayer p,boolean back){
        var stack=p.getMainHandItem();if(!MagicBookItem.is(stack)||p.isSpectator())return;
        var element=MagicBookItem.element(stack);
        stack.getOrCreateTag().putInt(MagicBookItem.ELEMENT,(back?element.previous():element.next()).ordinal());
        var state=STATES.get(p.getUUID());if(state!=null)stopChannel(p,stack,state);
        p.level().playSound(null,p.blockPosition(),SoundEvents.BOOK_PAGE_TURN,SoundSource.PLAYERS,.8f,1.2f);
        sync(p);
    }
    private static boolean usable(LivingEntity p){
        return MagicBookItem.is(p.getMainHandItem())&&p.isAlive()&&!p.isSpectator()&&!LobbyManager.respawning(p.getUUID())
                &&!FreeForAllMatch.blocked(p)&&!SafeZones.contains(p);
    }

    // ---- tick ----
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var server=event.getServer();
        for(var it=STATES.entrySet().iterator();it.hasNext();){
            var entry=it.next();LivingEntity p=server.getPlayerList().getPlayer(entry.getKey());
            if(p==null)for(var level:server.getAllLevels())if(level.getEntity(entry.getKey()) instanceof BotEntity bot&&!bot.isRemoved()&&bot.isAlive()){p=bot;break;}
            if(p==null){it.remove();continue;}
            run(p,entry.getValue());
        }
        if(!SHOTS.isEmpty())SHOTS.removeIf(MagicBook::fly);
    }
    private static void run(LivingEntity p,State state){
        long now=p.level().getGameTime();
        if(now-state.refreshed>HOLD_TIMEOUT)state.left=state.right=false;
        var stack=p.getMainHandItem();
        if(!usable(p)){
            state.left=state.right=false;state.flameEnd=state.lightningEnd=0;
            var carried=p instanceof Player player?MagicBookItem.carriedStack(player):p.getMainHandItem();if(!carried.isEmpty())stopChannel(p,carried,state);else state.channel=null;
            return;
        }
        var element=MagicBookItem.element(stack);var c=config();
        // Timed abilities keep running after the click.
        if(now<state.flameEnd)flame(p,now,state);
        if(now<state.lightningEnd&&now>=state.nextBolt&&state.boltsLeft>0){
            bolt(p);state.boltsLeft--;state.nextBolt=now+Math.max(1,ticks(c.lightningSeconds())/Math.max(1,c.lightningCount()));
        }
        // A held ability ends with its key, its element, or what pays for it.
        if(state.channel!=null&&(state.channel!=element||!(state.channelRight?state.right:state.left)))stopChannel(p,stack,state);
        if(state.channel!=null){channel(p,stack,state,now);return;}
        if(state.left)press(p,stack,state,element,false,now);
        else if(state.right)press(p,stack,state,element,true,now);
    }
    private static boolean ready(ItemStack stack,Element element,boolean right,long now){return now>=MagicBookItem.cooldown(stack,element,right);}
    private static boolean pay(LivingEntity p,ItemStack stack,int mana){
        int have=MagicBookItem.mana(stack);
        if(have<mana){if(p instanceof ServerPlayer player)player.displayClientMessage(net.minecraft.network.chat.Component.literal("마나가 부족합니다").withStyle(net.minecraft.ChatFormatting.AQUA),true);return false;}
        MagicBookItem.setMana(stack,have-mana);return true;
    }
    private static void cooldown(LivingEntity p,ItemStack stack,Element element,boolean right,double seconds){
        if(seconds>0)MagicBookItem.setCooldown(stack,element,right,p.level().getGameTime()+ticks(seconds));
        sync(p);
    }
    private static void press(LivingEntity p,ItemStack stack,State state,Element element,boolean right,long now){
        if(!ready(stack,element,right,now))return;
        var c=config();
        switch(element){
            case YELLOW->{
                if(!right){if(pay(p,stack,c.slamMana())){slam(p);cooldown(p,stack,element,false,c.slamCooldownSeconds());}}
                else if(MagicBookItem.mana(stack)>=1)startChannel(p,stack,state,element,true);
            }
            case BLUE->{
                if(!right){if(MagicBookItem.mana(stack)>=1)startChannel(p,stack,state,element,false);}
                else if(pay(p,stack,c.dropMana())){
                    SHOTS.add(new Shot(p,Kind.WATER,p.getEyePosition().add(0,-.15,0),p.getLookAngle(),1.6,c.dropRange(),null));
                    sound(p,SoundEvents.BUCKET_EMPTY,1,1.4f);cooldown(p,stack,element,true,c.dropCooldownSeconds());
                }
            }
            case RED->{
                if(!right){
                    if(now<state.nextShot||!pay(p,stack,c.fireballMana()))return;
                    state.nextShot=now+ticks(c.fireballIntervalSeconds());
                    SHOTS.add(new Shot(p,Kind.FIRE,p.getEyePosition().add(0,-.15,0),p.getLookAngle(),1.2,c.fireballRange()+8,aimed(p,c.fireballRange(),20)));
                    sound(p,SoundEvents.BLAZE_SHOOT,.7f,1.2f);sync(p);
                }else if(pay(p,stack,c.flameMana())){
                    state.flameEnd=now+ticks(c.flameSeconds());
                    // The cooldown runs from the end of the flames.
                    cooldown(p,stack,element,true,c.flameSeconds()+c.flameCooldownSeconds());
                }
            }
            case WHITE->{
                if(!right){
                    if(now<state.nextShot||!pay(p,stack,c.lightMana()))return;
                    state.nextShot=now+ticks(c.lightIntervalSeconds());
                    SHOTS.add(new Shot(p,Kind.LIGHT,p.getEyePosition().add(0,-.15,0),p.getLookAngle(),4,c.lightRange(),null));
                    sound(p,SoundEvents.AMETHYST_BLOCK_CHIME,1,1.6f);sync(p);
                }else if(pay(p,stack,c.lightningMana())){
                    state.lightningEnd=now+ticks(c.lightningSeconds());state.boltsLeft=c.lightningCount();state.nextBolt=now;
                    cooldown(p,stack,element,true,c.lightningCooldownSeconds());
                }
            }
            case BLACK->{
                if(!right){if(p.getHealth()>c.ruinHealthPerSecond()/20+1)startChannel(p,stack,state,element,false);}
                else if(MagicBookItem.mana(stack)>=1)startChannel(p,stack,state,element,true);
            }
        }
    }

    // ---- held abilities ----
    private static void startChannel(LivingEntity p,ItemStack stack,State state,Element element,boolean right){
        state.channel=element;state.channelRight=right;state.manaDebt=state.healthDebt=0;state.beamTarget=null;state.beamTicks=0;
        if(element==Element.BLACK&&right){stack.getOrCreateTag().putBoolean(MagicBookItem.FLYING,true);sync(p);sound(p,SoundEvents.PHANTOM_FLAP,.8f,.7f);}
        if(element==Element.YELLOW)sound(p,SoundEvents.RAVAGER_ROAR,.6f,.5f);
    }
    private static void stopChannel(LivingEntity p,ItemStack stack,State state){
        if(state.channel==null)return;
        var c=config();var element=state.channel;boolean right=state.channelRight;state.channel=null;
        if(MagicBookItem.flying(stack))stack.getOrCreateTag().putBoolean(MagicBookItem.FLYING,false);
        if(element==Element.YELLOW&&right)cooldown(p,stack,element,true,c.quakeCooldownSeconds());
        else if(element==Element.BLACK&&right)cooldown(p,stack,element,true,c.flightCooldownSeconds());
        else sync(p);
    }
    /** Spends {@code perSecond} mana over the tick; false when the book ran dry. */
    private static boolean drain(LivingEntity p,ItemStack stack,State state,double perSecond){
        state.manaDebt+=perSecond/20;
        int whole=(int)state.manaDebt;if(whole<=0)return true;
        int have=MagicBookItem.mana(stack);if(have<whole)return false;
        state.manaDebt-=whole;MagicBookItem.setMana(stack,have-whole);return true;
    }
    private static void channel(LivingEntity p,ItemStack stack,State state,long now){
        var c=config();boolean ok;
        switch(state.channel){
            case YELLOW->{ok=drain(p,stack,state,c.quakeManaPerSecond());if(ok)quake(p,now);}
            case BLUE->{ok=drain(p,stack,state,c.holyManaPerSecond());if(ok)holy(p,state,now);}
            case BLACK->{
                if(state.channelRight){ok=drain(p,stack,state,c.flightManaPerSecond());if(ok){p.fallDistance=0;if(now%4==0)((ServerLevel)p.level()).sendParticles(RUIN,p.getX(),p.getY()+.4,p.getZ(),3,.25,.25,.25,0);}}
                else ok=ruin(p,state,now);
            }
            default->ok=false;
        }
        if(!ok)stopChannel(p,stack,state);
    }
    /** 황 right: every second, damage to every enemy within the radius; the ground shakes. */
    private static void quake(LivingEntity p,long now){
        var c=config();var level=((ServerLevel)p.level());
        if(now%10==0){
            var ground=level.getBlockState(p.blockPosition().below());
            if(!ground.isAir())level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,ground),p.getX(),p.getY()+.1,p.getZ(),40,c.quakeRadius()*.35,.1,c.quakeRadius()*.35,.15);
        }
        if(now%20!=0)return;
        level.playSound(null,p.blockPosition(),SoundEvents.GENERIC_EXPLODE,SoundSource.PLAYERS,.5f,.45f);
        double r=c.quakeRadius();
        for(var target:level.getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().inflate(r,4,r),e->enemy(p,e)&&e.distanceToSqr(p)<=r*r))
            hurt(p,target,(float)c.quakeDamagePerSecond());
    }
    /** 청 left: a 32-block stream; the ally it touches heals one point a second. */
    private static void holy(LivingEntity p,State state,long now){
        var c=config();var hit=trace(p,c.holyRange(),HiddenWeaponTargets::valid);
        beam(p,hit.end,0,now);
        if(hit.entity!=null&&Defibrillator.ally(p,hit.entity)){
            if(!hit.entity.getUUID().equals(state.beamTarget)){state.beamTarget=hit.entity.getUUID();state.beamTicks=0;}
            int every=Math.max(1,(int)Math.round(20/Math.max(.05,c.holyHealPerSecond())));
            if(++state.beamTicks%every==0&&hit.entity.getHealth()<hit.entity.getMaxHealth()){
                hit.entity.heal(1);HealCue.mark(hit.entity,HealCue.GHOST_TICKS);
                ((ServerLevel)p.level()).sendParticles(ParticleTypes.HEART,hit.entity.getX(),hit.entity.getY()+hit.entity.getBbHeight()+.3,hit.entity.getZ(),1,.2,.1,.2,0);
            }
        }else state.beamTarget=null;
        if(now%6==0)sound(p,SoundEvents.WATER_AMBIENT,.35f,1.6f);
    }
    /** 흑 left: an unlimited beam that eats the caster's health; false once the caster could not pay. */
    private static boolean ruin(LivingEntity p,State state,long now){
        var c=config();
        state.healthDebt+=c.ruinHealthPerSecond()/20;
        if(state.healthDebt>=1){
            int whole=(int)state.healthDebt;if(p.getHealth()-whole<1)return false;
            state.healthDebt-=whole;p.setHealth(p.getHealth()-whole);
        }
        var hit=trace(p,c.ruinRange(),e->enemy(p,e));
        beam(p,hit.end,1,now);
        if(hit.entity!=null){
            if(!hit.entity.getUUID().equals(state.beamTarget)){state.beamTarget=hit.entity.getUUID();state.beamTicks=0;}
            // Half a second's damage every ten ticks.
            if(state.beamTicks++%10==0)hurt(p,hit.entity,(float)(c.ruinDamagePerSecond()/2));
        }else state.beamTarget=null;
        if(now%8==0)sound(p,SoundEvents.BEACON_AMBIENT,.6f,.5f);
        return true;
    }

    // ---- one-shot abilities ----
    /** 황 left: slams the ground; every enemy up to slamRange ahead (a 90° fan) is hurt and thrown up. */
    private static void slam(LivingEntity p){
        var c=config();var level=((ServerLevel)p.level());var look=p.getLookAngle().multiply(1,0,1);
        if(look.lengthSqr()<1e-4)look=net.minecraft.world.phys.Vec3.directionFromRotation(0,p.getYRot());look=look.normalize();
        double range=c.slamRange();
        for(var target:level.getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().inflate(range,3,range),e->enemy(p,e))){
            var to=target.position().subtract(p.position());var flat=to.multiply(1,0,1);
            if(flat.length()>range||Math.abs(to.y)>3||flat.lengthSqr()>1e-4&&flat.normalize().dot(look)<Math.cos(Math.toRadians(45)))continue;
            if(!p.hasLineOfSight(target))continue;
            hurt(p,target,(float)c.slamDamage());
            if(!target.isAlive())continue;
            var old=target.getDeltaMovement();var lifted=new Vec3(old.x,c.slamLift(),old.z);
            if(target instanceof ServerPlayer victim)PlayerImpulse.apply(victim,lifted);
            else{target.setDeltaMovement(lifted);target.hasImpulse=true;target.hurtMarked=true;}
        }
        for(int i=1;i<=(int)range;i++){
            var at=p.position().add(look.scale(i));var ground=level.getBlockState(BlockPos.containing(at).below());
            if(!ground.isAir())level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,ground),at.x,at.y+.1,at.z,12,.4,.1,.4,.2);
        }
        level.sendParticles(ParticleTypes.EXPLOSION,p.getX()+look.x*2,p.getY()+.2,p.getZ()+look.z*2,1,0,0,0,0);
        level.playSound(null,p.blockPosition(),SoundEvents.GENERIC_EXPLODE,SoundSource.PLAYERS,.8f,.6f);
        p.swing(net.minecraft.world.InteractionHand.MAIN_HAND,true);
    }
    /** 적 right, each tick: flames along the view, damage every half second to the enemies in the cone. */
    private static void flame(LivingEntity p,long now,State state){
        var c=config();var level=((ServerLevel)p.level());var eye=p.getEyePosition();var look=p.getLookAngle();var random=p.getRandom();
        for(int i=0;i<4;i++){
            var spread=look.add((random.nextDouble()-.5)*.25,(random.nextDouble()-.5)*.25,(random.nextDouble()-.5)*.25).normalize();
            var from=eye.add(look.scale(.8)).add(0,-.2,0);
            level.sendParticles(i==3?ParticleTypes.SMOKE:ParticleTypes.FLAME,from.x,from.y,from.z,0,spread.x,spread.y,spread.z,c.flameRange()/16*.9);
        }
        if(now%6==0)level.playSound(null,p.blockPosition(),SoundEvents.FIRECHARGE_USE,SoundSource.PLAYERS,.5f,.8f+random.nextFloat()*.3f);
        if((state.flameEnd-now)%10!=0)return;
        double range=c.flameRange();
        for(var target:level.getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().inflate(range),e->enemy(p,e))){
            var to=target.getBoundingBox().getCenter().subtract(eye);
            if(to.length()>range||to.normalize().dot(look)<Math.cos(Math.toRadians(14))||!p.hasLineOfSight(target))continue;
            hurt(p,target,(float)(c.flameDamagePerSecond()/2));
        }
    }
    /** 백 right: one bolt where the caster looks (up to 32 blocks), scattered a little, onto the ground. */
    private static void bolt(LivingEntity p){
        var c=config();var level=((ServerLevel)p.level());var eye=p.getEyePosition();var random=p.getRandom();
        var aim=level.clip(new ClipContext(eye,eye.add(p.getLookAngle().scale(32)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getLocation();
        var spot=aim.add((random.nextDouble()*2-1)*3,0,(random.nextDouble()*2-1)*3);
        var down=level.clip(new ClipContext(spot.add(0,4,0),spot.add(0,-24,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        if(down.getType()==HitResult.Type.BLOCK)spot=down.getLocation();
        var bolt=EntityType.LIGHTNING_BOLT.create(level);
        if(bolt!=null){bolt.setVisualOnly(true);bolt.moveTo(spot);level.addFreshEntity(bolt);}
        double r=c.lightningRadius();
        for(var target:level.getEntitiesOfClass(LivingEntity.class,new AABB(spot,spot).inflate(r,4,r),e->enemy(p,e))){
            var flat=target.position().subtract(spot).multiply(1,0,1);
            if(flat.length()>r)continue;
            if(hurt(p,target,(float)c.lightningDamage()))shock(target,spot);
        }
    }

    // ---- projectiles ----
    /** One tick of a shot; true once it is spent. */
    private static boolean fly(Shot shot){
        if(shot.owner.isRemoved()||shot.level!=shot.owner.level())return true;
        if(shot.homing!=null&&shot.homing.isAlive()&&!shot.homing.isRemoved()){
            // Turns at most 12° a tick toward the target.
            var want=shot.homing.getBoundingBox().getCenter().subtract(shot.pos).normalize();
            double dot=Math.max(-1,Math.min(1,shot.dir.dot(want))),angle=Math.acos(dot),max=Math.toRadians(12);
            if(angle>1e-4)shot.dir=angle<=max?want:shot.dir.scale(Math.sin(angle-max)/Math.sin(angle)).add(want.scale(Math.sin(max)/Math.sin(angle))).normalize();
        }
        double step=Math.min(shot.speed,shot.left);var to=shot.pos.add(shot.dir.scale(step));
        var block=shot.level.clip(new ClipContext(shot.pos,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,shot.owner));
        if(block.getType()!=HitResult.Type.MISS)to=block.getLocation();
        LivingEntity hit=null;double best=Double.MAX_VALUE;Vec3 impactPoint=null;
        for(var body:shot.level.getEntitiesOfClass(LivingEntity.class,new AABB(shot.pos,to).inflate(1),e->e!=shot.owner&&e.isAlive()&&!e.isSpectator()&&passable(shot,e))){
            var box=body.getBoundingBox().inflate(.3);
            var point=box.contains(shot.pos)?shot.pos:box.clip(shot.pos,to).orElse(null);
            if(point!=null){double d=point.distanceToSqr(shot.pos);if(d<best){best=d;hit=body;impactPoint=point;}}
        }
        trail(shot,hit==null?to:impactPoint);
        if(hit!=null){shot.pos=impactPoint;impact(shot,hit);return true;}
        shot.left-=step;shot.pos=to;
        if(block.getType()!=HitResult.Type.MISS||shot.left<=1e-6){burst(shot);return true;}
        return false;
    }
    /** What a shot stops on: the water drop on any player or bot (allies get the boost), the others on enemies only. */
    private static boolean passable(Shot shot,LivingEntity e){
        return shot.kind==Kind.WATER?HiddenWeaponTargets.valid(e):enemy(shot.owner,e);
    }
    private static void impact(Shot shot,LivingEntity target){
        var c=config();
        switch(shot.kind){
            case WATER->{if(Defibrillator.ally(shot.owner,target))target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,ticks(c.dropSpeedSeconds()),0,false,true,true));}
            case FIRE->hurt(shot.owner,target,(float)c.fireballDamage());
            case LIGHT->{if(hurt(shot.owner,target,(float)c.lightDamage()))shock(target,shot.pos);}
        }
        burst(shot);
    }
    private static void trail(Shot shot,Vec3 to){
        var mid=shot.pos.add(to).scale(.5);
        switch(shot.kind){
            case WATER->shot.level.sendParticles(ParticleTypes.FALLING_WATER,mid.x,mid.y,mid.z,4,.15,.15,.15,0);
            case FIRE->shot.level.sendParticles(ParticleTypes.FLAME,mid.x,mid.y,mid.z,3,.08,.08,.08,.01);
            case LIGHT->shot.level.sendParticles(ParticleTypes.END_ROD,mid.x,mid.y,mid.z,2,.05,.05,.05,0);
        }
    }
    private static void burst(Shot shot){
        var at=shot.pos;
        switch(shot.kind){
            case WATER->{shot.level.sendParticles(ParticleTypes.SPLASH,at.x,at.y,at.z,16,.3,.3,.3,.1);shot.level.playSound(null,BlockPos.containing(at),SoundEvents.GENERIC_SPLASH,SoundSource.PLAYERS,.6f,1.4f);}
            case FIRE->{shot.level.sendParticles(ParticleTypes.LAVA,at.x,at.y,at.z,4,.2,.2,.2,0);shot.level.playSound(null,BlockPos.containing(at),SoundEvents.FIRE_EXTINGUISH,SoundSource.PLAYERS,.5f,1.2f);}
            case LIGHT->{shot.level.sendParticles(ParticleTypes.ELECTRIC_SPARK,at.x,at.y,at.z,12,.25,.25,.25,.2);shot.level.playSound(null,BlockPos.containing(at),SoundEvents.AMETHYST_BLOCK_BREAK,SoundSource.PLAYERS,.6f,1.5f);}
        }
    }

    // ---- helpers ----
    private record Trace(Vec3 end,LivingEntity entity){}
    /** The first block or matching body along the view, up to {@code range}. */
    private static Trace trace(LivingEntity p,double range,java.util.function.Predicate<LivingEntity> filter){
        var level=((ServerLevel)p.level());var eye=p.getEyePosition();var end=eye.add(p.getLookAngle().scale(range));
        var block=level.clip(new ClipContext(eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        if(block.getType()!=HitResult.Type.MISS)end=block.getLocation();
        LivingEntity hit=null;double best=Double.MAX_VALUE;
        for(var body:level.getEntitiesOfClass(LivingEntity.class,new AABB(eye,end).inflate(1),e->e!=p&&e.isAlive()&&!e.isSpectator()&&filter.test(e))){
            var box=body.getBoundingBox().inflate(.3);
            var point=box.contains(eye)?eye:box.clip(eye,end).orElse(null);
            if(point!=null){double d=point.distanceToSqr(eye);if(d<best){best=d;hit=body;}}
        }
        return new Trace(hit==null?end:eye.add(p.getLookAngle().scale(Math.sqrt(best))),hit);
    }
    /** The enemy nearest the crosshair within {@code range} and {@code degrees} of the view, in sight. */
    private static LivingEntity aimed(LivingEntity p,double range,double degrees){
        var eye=p.getEyePosition();var look=p.getLookAngle();LivingEntity best=null;double bestAngle=Math.toRadians(degrees);
        for(var e:((ServerLevel)p.level()).getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().inflate(range),e->enemy(p,e))){
            var to=e.getBoundingBox().getCenter().subtract(eye);if(to.length()>range)continue;
            double angle=Math.acos(Math.max(-1,Math.min(1,look.dot(to.normalize()))));
            if(angle<bestAngle&&p.hasLineOfSight(e)){bestAngle=angle;best=e;}
        }
        return best;
    }
    /** Beam visuals for the caster and everyone tracking them, every other tick. */
    private static void beam(LivingEntity p,Vec3 end,int kind,long now){
        if(now%2!=0)return;
        var eye=p.getEyePosition();
        GungangNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->p),
                new GungangNetwork.BookBeam(p.getId(),kind,eye.x,eye.y,eye.z,end.x,end.y,end.z));
    }
    static boolean enemy(LivingEntity owner,LivingEntity target){
        if(target==owner||!HiddenWeaponTargets.valid(target))return false;
        return owner.getTeam()==null||target.getTeam()==null||!owner.getTeam().getName().equals(target.getTeam().getName());
    }
    /** Magic damage from the caster; repeated ticks are not swallowed by the hurt cooldown. */
    private static boolean hurt(LivingEntity p,LivingEntity target,float amount){
        target.invulnerableTime=0;
        return target.hurt(p.damageSources().indirectMagic(p,p),(float)(amount*AdrenalineDamage.multiplier(p)));
    }
    private static void shock(LivingEntity target,Vec3 from){ArcShock.apply(target,from);ArcShock.stun(target);}
    private static void sound(LivingEntity p,SoundEvent sound,float volume,float pitch){p.level().playSound(null,p.blockPosition(),sound,SoundSource.PLAYERS,volume,pitch);}

    /** Mana a health pack restores to a carried book, or 0 when there is none to fill. */
    static int packMana(LivingEntity entity,boolean small){
        if(!(entity instanceof Player p))return 0;
        var stack=MagicBookItem.carriedStack(p);if(stack.isEmpty())return 0;
        return Math.min(small?config().smallPackMana():config().largePackMana(),MagicBookItem.capacity(stack)-MagicBookItem.mana(stack));
    }
    static void addMana(Player p,int amount){
        var stack=MagicBookItem.carriedStack(p);if(stack.isEmpty()||amount<=0)return;
        MagicBookItem.setMana(stack,MagicBookItem.mana(stack)+amount);sync(p);
    }

    /** Vanilla melee with the book does nothing; the client sends its keys instead. */
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void swing(net.minecraftforge.event.entity.player.AttackEntityEvent event){
        if(MagicBookItem.is(event.getEntity().getMainHandItem()))event.setCanceled(true);
    }
    @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event){STATES.remove(event.getEntity().getUUID());SHOTS.removeIf(s->s.owner==event.getEntity());}
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event){STATES.clear();SHOTS.clear();}
    private MagicBook(){}
}
