package dev.gungang.acoustics.server;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/**
 * Secondary items the bot does not hold as a weapon: GunGang gadgets used as abilities on their own cooldown while
 * the primary stays in hand, and other usable items (e.g. lrtactical throwables) thrown through a fake player that
 * shares the bot's UUID, so projectile owners and kill credit resolve to the bot.
 * Decisions run once per think; ongoing effects (heal, boost, jet, shield, life support) advance every tick.
 */
final class BotGadgets {
    private static final UUID SPEED=UUID.fromString("0f9c1a5e-3c43-4c27-9e1e-8a6c1f2b7d10");
    private static final String FAKE_NAME="GunGangBot";
    /** Cooldown deadlines per secondary id survive respawns inside one match, as they do for players. */
    private final Map<String,Long> deadlines=new HashMap<>();
    private String kind="",id="";
    // Assigned on equip: touching ItemStack.EMPTY here would load item registries in plain unit tests.
    private ItemStack stack;
    private int cooldown;
    private long lastSeen,healEnd,boostEnd,jetEnd,lifeEnd,lifeDrain,raisedUntil,shieldRepair,shieldCombat;
    private float healPerTick,shieldHealth,lastScore;
    private long lastHurt;
    private boolean lifeActive,disabled;
    private Vec3 jetDirection,jetPrevious;
    private DamageSource lifeSource;

    /** Gadget kind for an item, "use" for any other usable item, or "" for items the bot holds as weapons. */
    static String kind(ItemStack stack,boolean held) {
        if(held||stack.isEmpty())return "";
        return stack.getItem() instanceof GadgetItem gadget?gadget.kind:"use";
    }
    static boolean supported(ItemStack stack) {return !stack.isEmpty()&&!(stack.getItem() instanceof BombItem)&&!CombatGadgets.is(stack,"turret");}

    void equip(BotEntity bot,Loadouts.Weapon weapon,ItemStack item,boolean held) {
        reset(bot);
        kind=weapon==null?"":kind(item,held);id=weapon==null?"":weapon.id();stack=item;disabled=false;
        if(kind.isEmpty())return;
        cooldown=switch(kind) {
            case "molotov"->ExtraGadgetConfig.ticks(ExtraGadgetConfig.molotov.cooldownSeconds());
            case "arrow_grenade"->ExtraGadgetConfig.ticks(ExtraGadgetConfig.arrowGrenade.cooldownSeconds());
            case "orbital_strike"->ExtraGadgetConfig.ticks(ExtraGadgetConfig.orbital.cooldownSeconds());
            case "life_support"->ExtraGadgetConfig.ticks(ExtraGadgetConfig.life.cooldownSeconds());
            case "jetpack"->ExtraGadgetConfig.ticks(ExtraGadgetConfig.jet.cooldownSeconds());
            case "infrared_scanner"->ExtraGadgetConfig.ticks(ExtraGadgetConfig.scanner.cooldownSeconds());
            default->(int)Math.ceil(weapon.cooldownSeconds()*20);
        };
        long now=bot.level().getGameTime();
        // Like players, an orbital strike waits a full cooldown after it is first equipped.
        if(kind.equals("orbital_strike"))deadlines.putIfAbsent(id,now+cooldown);
        shieldHealth=(float)ExtraGadgetConfig.shield.capacity();
        // Carried in the off hand so other players can see what the bot brought.
        bot.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND,item.copy());
        bot.setDropChance(net.minecraft.world.entity.EquipmentSlot.OFFHAND,0);
    }
    /** Ends effects on the current body; cooldowns stay. */
    void reset(BotEntity bot) {
        if(bot!=null) {
            var speed=bot.getAttribute(Attributes.MOVEMENT_SPEED);if(speed!=null)speed.removeModifier(SPEED);
            if(jetting())bot.setNoGravity(false);
            if(lifeActive)bot.setAbsorptionAmount(0);
        }
        healEnd=boostEnd=jetEnd=lifeEnd=raisedUntil=shieldRepair=0;lifeActive=false;jetDirection=null;lifeSource=null;
    }
    void clearCooldowns() {deadlines.clear();}
    /** Status line: equipped gadget, its latest utility score and remaining cooldown. */
    String describe(BotEntity bot) {
        if(kind.isEmpty())return "보조 판단 없음(손에 드는 무기)";
        if(kind.equals("life_support"))return "보조="+id+" 치명상 시 자동 발동";
        long left=bot==null?0:Math.max(0,deadlines.getOrDefault(id,0L)-bot.level().getGameTime());
        return String.format(java.util.Locale.ROOT,"보조=%s 점수 %.2f(기준 %.2f) 쿨타임 %.1f초%s",id,lastScore,BotGadgetScore.THRESHOLD,left/20.0,disabled?" · 사용 불가로 중지":"");
    }

    boolean jetting() {return jetDirection!=null;}
    boolean shielding(long now) {return kind.equals("shield")&&now<raisedUntil&&shieldHealth>0;}
    /** Players cannot fire while flying a jetpack or holding a raised shield; neither can the bot. */
    boolean blocksAttack(long now) {return jetting()||shielding(now);}
    boolean boosted(long now) {return now<boostEnd;}
    private boolean ready(long now) {return !disabled&&now>=deadlines.getOrDefault(id,0L);}
    private void commit(long now) {deadlines.put(id,now+cooldown);}


    /** @param traveling moving between places (patrol, investigate, regroup, support) rather than fighting */
    void tick(BotEntity bot,ArenaMap arena,String team,net.minecraft.world.entity.LivingEntity target,boolean seen,BotMovement movement,boolean think,boolean traveling) {
        var level=(ServerLevel)bot.level();long now=level.getGameTime();
        if(seen)lastSeen=now;
        effects(bot,level,now);
        if(!think||kind.isEmpty()||!ready(now)||!bot.isAlive()||jetting())return;
        if(SafeZones.contains(bot)&&!Set.of("recovery_potion","shield","life_support").contains(kind))return;
        double distance=target==null?Double.MAX_VALUE:bot.distanceTo(target);
        float cost=(float)(bot.getMaxHealth()*GadgetConfig.adrenaline.healthCostPercent()/100);
        var goal=movement.goal();double goalDistance=goal==null?0:Math.sqrt(Math.pow(goal.x-bot.getX(),2)+Math.pow(goal.z-bot.getZ(),2));
        var situation=new BotGadgetScore.Situation(seen,distance,bot.getHealth()/bot.getMaxHealth(),now-lastSeen,now-lastHurt,
                traveling,goalDistance,bot.getHealth()>cost+bot.getMaxHealth()*.3f);
        lastScore=BotGadgetScore.score(kind,situation);
        // A little noise so the same situation does not always fire on the same tick.
        if(lastScore+bot.getRandom().nextFloat()*.15f<BotGadgetScore.THRESHOLD)return;
        try {
            boolean used=switch(kind) {
                case "molotov","arrow_grenade"->throwGrenade(bot,target,kind.equals("arrow_grenade"));
                case "landmine","bear_trap"->bot.onGround()&&placeTrap(bot,level,kind.equals("bear_trap"));
                case "barrier"->placeBarrier(bot,level,target);
                case "orbital_strike"->strike(bot,level,team,target);
                case "recovery_potion"->{
                    if(now<healEnd)yield false;
                    int ticks=Math.max(1,(int)Math.round(GadgetConfig.recovery.durationSeconds()*20));
                    healPerTick=(float)GadgetConfig.recovery.heal()/ticks;healEnd=now+ticks;
                    level.playSound(null,bot.blockPosition(),SoundEvents.GENERIC_DRINK,SoundSource.PLAYERS,.8f,1);yield true;
                }
                case "adrenaline"->{
                    var config=GadgetConfig.adrenaline;bot.setHealth(bot.getHealth()-cost);
                    var speed=bot.getAttribute(Attributes.MOVEMENT_SPEED);speed.removeModifier(SPEED);
                    speed.addTransientModifier(new AttributeModifier(SPEED,"GunGang bot adrenaline",config.movementBonusPercent()/100,AttributeModifier.Operation.MULTIPLY_TOTAL));
                    boostEnd=now+Math.max(1,Math.round(config.durationSeconds()*20));yield true;
                }
                case "infrared_scanner"->{
                    if(bot.getTeam()==null)yield false;
                    CombatGadgets.scan(bot,now+ExtraGadgetConfig.ticks(ExtraGadgetConfig.scanner.durationSeconds()));yield true;
                }
                case "shield"->{
                    // Raising costs nothing; the shield's own durability and repair time are the limit, not a cooldown.
                    if(shieldHealth>0&&now>=raisedUntil){raisedUntil=now+60;level.playSound(null,bot.blockPosition(),SoundEvents.ARMOR_EQUIP_IRON,SoundSource.PLAYERS,.8f,1);}
                    yield false;
                }
                case "jetpack"->startJet(bot,level,goal);
                case "use"->use(bot,level,team,target);
                default->false;
            };
            if(used)commit(now);
        }catch(RuntimeException failure) {
            // An item the bot cannot drive must not stop the bot; drop it for this life and log once.
            disabled=true;com.mojang.logging.LogUtils.getLogger().warn("[GunGang] Bot cannot use secondary {}",id,failure);
        }
    }

    private void effects(BotEntity bot,ServerLevel level,long now) {
        if(now<healEnd)bot.heal(healPerTick);
        if(boostEnd!=0&&now>=boostEnd) {var speed=bot.getAttribute(Attributes.MOVEMENT_SPEED);if(speed!=null)speed.removeModifier(SPEED);boostEnd=0;}
        if(kind.equals("shield")) {
            var config=ExtraGadgetConfig.shield;
            if(shieldHealth<=0&&now>=shieldRepair) {shieldHealth=(float)config.capacity();}
            else if(config.outOfCombatRegen()&&shieldHealth>0&&now-shieldCombat>=ExtraGadgetConfig.ticks(config.regenDelaySeconds()))
                shieldHealth=(float)Math.min(config.capacity(),shieldHealth+config.regenPerSecond()/20);
        }
        if(lifeActive) {
            if(now>=lifeDrain) {bot.setAbsorptionAmount(SurvivalRules.drain(bot.getAbsorptionAmount()));lifeDrain=now+ExtraGadgetConfig.ticks(ExtraGadgetConfig.life.drainIntervalSeconds());}
            if(now>=lifeEnd||bot.getAbsorptionAmount()<=0) {
                bot.setAbsorptionAmount(0);
                // Stay active while die() runs so the same device cannot revive twice.
                if(!SurvivalRules.recovered(bot.getHealth())) {bot.setHealth(0);bot.die(lifeSource==null?bot.damageSources().generic():lifeSource);}
                lifeActive=false;
            }
        }
        if(jetting())jet(bot,level,now);
    }

    /** Lethal damage: the life support catches it if it is ready. Returns true to cancel the death. */
    boolean lifeSupport(BotEntity bot,DamageSource source) {
        long now=bot.level().getGameTime();
        if(!kind.equals("life_support")||lifeActive||!ready(now)||source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL))return false;
        bot.setHealth(SurvivalRules.LIFE_HEALTH);bot.setAbsorptionAmount((float)ExtraGadgetConfig.life.absorption());bot.clearFire();
        lifeActive=true;lifeSource=source;lifeEnd=now+ExtraGadgetConfig.ticks(ExtraGadgetConfig.life.durationSeconds());
        lifeDrain=now+ExtraGadgetConfig.ticks(ExtraGadgetConfig.life.drainIntervalSeconds());commit(now);
        bot.level().playSound(null,bot.blockPosition(),SoundEvents.TOTEM_USE,SoundSource.PLAYERS,.8f,1.3f);
        return true;
    }
    /** Raised shield: frontal hits drain the shield first, melee is softened from any side, like the player shield. */
    void hurt(BotEntity bot,LivingHurtEvent event) {
        long now=bot.level().getGameTime();
        if(event.getAmount()>0)lastHurt=now;
        if(kind.equals("shield")&&event.getAmount()>0)shieldCombat=now;
        if(!shielding(now)||event.getAmount()<=0)return;
        var type=event.getSource();
        if(type.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)||type.is(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK))
            event.setAmount((float)(event.getAmount()*(1-ExtraGadgetConfig.shield.meleeReductionPercent()/100)));
        Vec3 from=type.getSourcePosition();if(from==null||event.getAmount()<=0)return;
        Vec3 delta=from.subtract(bot.position()),look=bot.getLookAngle();
        if(!ShieldRules.inFront(look.x,look.z,delta.x,delta.z))return;
        var hit=ShieldRules.absorb(shieldHealth,event.getAmount());shieldHealth=hit.durability();
        if(shieldHealth==0) {shieldRepair=now+ExtraGadgetConfig.ticks(ExtraGadgetConfig.shield.repairSeconds());raisedUntil=0;}
        event.setAmount(hit.damage());if(hit.damage()==0)event.setCanceled(true);
        bot.level().playSound(null,bot.blockPosition(),shieldHealth==0?SoundEvents.SHIELD_BREAK:SoundEvents.SHIELD_BLOCK,SoundSource.PLAYERS,1,1);
    }

    private static boolean throwGrenade(BotEntity bot,net.minecraft.world.entity.LivingEntity target,boolean arrows) {
        net.minecraft.world.entity.projectile.ThrowableItemProjectile projectile=arrows?GadgetItems.ARROW_GRENADE_PROJECTILE.get().create(bot.level()):GadgetItems.MOLOTOV_PROJECTILE.get().create(bot.level());if(projectile==null)return false;
        projectile.setOwner(bot);projectile.setPos(bot.getX(),bot.getEyeY()-.1,bot.getZ());
        Vec3 d=target.position().subtract(projectile.position());double flat=d.horizontalDistance();
        // Lob: aim above the feet in proportion to range so the low-gravity bottle lands near the target.
        projectile.shoot(d.x,d.y+flat*.12,d.z,arrows?(float)ExtraGadgetConfig.arrowGrenade.throwSpeed():1.1f,2);
        if(!bot.level().addFreshEntity(projectile))return false;
        bot.level().playSound(null,bot.blockPosition(),SoundEvents.SPLASH_POTION_THROW,SoundSource.PLAYERS,.7f,1);
        bot.swing(InteractionHand.OFF_HAND);return true;
    }
    private static boolean placeTrap(BotEntity bot,ServerLevel level,boolean bear) {
        var pos=bot.blockPosition();
        if(!level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP))return false;
        if(SafeZones.contains(level,pos.getX()+.5,pos.getY(),pos.getZ()+.5))return false;
        // Spread traps out instead of stacking them on one spot of the route.
        if(!level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,new AABB(pos).inflate(6),
                e->e instanceof DeployableEntity d&&!d.wall()||e instanceof BearTrapEntity).isEmpty())return false;
        if(bear) {
            if(BearTraps.count(bot.getUUID())>=GadgetConfig.bearTrap.maxPlaced())return false;
            var trap=GadgetItems.BEAR_TRAP_ENTITY.get().create(level);if(trap==null)return false;
            trap.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);trap.setYRot(bot.getDirection().toYRot());trap.setup(bot);
            BearTraps.placed(trap);if(!level.addFreshEntity(trap)) {trap.discard();return false;}
            level.playSound(null,pos,SoundEvents.IRON_TRAPDOOR_OPEN,SoundSource.PLAYERS,.9f,.7f);return true;
        }
        if(DeployableLedger.get(level.getServer()).count(bot.getUUID(),false)>=GadgetConfig.mine.maxPlaced())return false;
        return deploy(bot,level,pos,false,false);
    }
    private static boolean placeBarrier(BotEntity bot,ServerLevel level,net.minecraft.world.entity.LivingEntity target) {
        if(DeployableLedger.get(level.getServer()).count(bot.getUUID(),true)>=GadgetConfig.barrier.maxPlaced())return false;
        Vec3 d=target.position().subtract(bot.position());double flat=Math.max(1e-4,d.horizontalDistance());
        boolean east=Math.abs(d.x)>Math.abs(d.z);
        var pos=BlockPos.containing(bot.getX()+d.x/flat*2,bot.getY(),bot.getZ()+d.z/flat*2);
        if(!level.noCollision(GadgetManager.bounds(pos,true,east))||SafeZones.contains(level,pos.getX()+.5,pos.getY(),pos.getZ()+.5))return false;
        for(int offset=-1;offset<=1;offset++) {
            var support=pos.offset(east?0:offset,-1,east?offset:0);
            if(!level.getBlockState(support).isFaceSturdy(level,support,Direction.UP))return false;
        }
        return deploy(bot,level,pos,true,east);
    }
    private static boolean deploy(BotEntity bot,ServerLevel level,BlockPos pos,boolean wall,boolean east) {
        var entity=GadgetItems.DEPLOYABLE.get().create(level);if(entity==null)return false;
        entity.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);entity.setup(bot,wall,east);
        if(!level.addFreshEntity(entity)) {entity.discard();return false;}
        GadgetManager.track(entity);return true;
    }
    private static boolean strike(BotEntity bot,ServerLevel level,String team,net.minecraft.world.entity.LivingEntity target) {
        var config=ExtraGadgetConfig.orbital;double radius=config.radius();
        // Never call the strike onto itself or onto teammates standing in the blast.
        if(bot.distanceTo(target)<=radius+2)return false;
        String side=team.equals("ALPHA")?"Red":"Blue";
        for(var p:level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,target.getBoundingBox().inflate(radius),BotEntity::participant))
            if(p.getTeam()!=null&&p.getTeam().getName().equals(side)&&p.distanceTo(target)<=radius)return false;
        var strike=GadgetItems.ORDNANCE.get().create(level);if(strike==null)return false;
        strike.setPos(target.getX(),target.getY(),target.getZ());strike.setup(bot,true);
        if(!level.addFreshEntity(strike))return false;
        level.playSound(null,target.blockPosition(),SoundEvents.BEACON_ACTIVATE,SoundSource.PLAYERS,3,1.2f);return true;
    }

    private boolean startJet(BotEntity bot,ServerLevel level,Vec3 goal) {
        if(goal==null||!bot.onGround()||bot.isPassenger()||SafeZones.contains(bot))return false;
        Vec3 d=goal.subtract(bot.position());double flat=d.horizontalDistance();
        if(flat<20)return false;
        Vec3 direction=new Vec3(d.x/flat,0,d.z/flat);
        // Only take off along a clear corridor; a jet that hits a wall after a block is wasted.
        var box=bot.getBoundingBox().move(0,.2,0);
        for(int step=1;step<=6;step++)if(!level.noCollision(bot,box.move(direction.scale(step))))return false;
        bot.getNavigation().stop();bot.setNoGravity(true);bot.fallDistance=0;
        jetDirection=direction;jetPrevious=bot.position();jetEnd=level.getGameTime()+ExtraGadgetConfig.ticks(ExtraGadgetConfig.jet.durationSeconds());
        float yaw=(float)(Mth.atan2(direction.z,direction.x)*180/Math.PI)-90;bot.setYRot(yaw);bot.yBodyRot=yaw;
        level.playSound(null,bot.blockPosition(),SoundEvents.FIRECHARGE_USE,SoundSource.PLAYERS,1.2f,.7f);
        level.playSound(null,bot.blockPosition(),SoundEvents.BLAZE_SHOOT,SoundSource.PLAYERS,.9f,.6f);
        return true;
    }
    private void jet(BotEntity bot,ServerLevel level,long now) {
        var config=ExtraGadgetConfig.jet;
        if(now>=jetEnd||!bot.isAlive()||bot.horizontalCollision&&now>jetEnd-ExtraGadgetConfig.ticks(config.durationSeconds())+2||SafeZones.contains(bot)) {stopJet(bot,level,now);return;}
        Vec3 motion=jetDirection.scale(config.speed());
        var swept=bot.getBoundingBox().expandTowards(jetPrevious.subtract(bot.position())).expandTowards(motion).inflate(.1);
        var hit=level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,swept,q->q!=bot&&BotEntity.participant(q)&&!SafeZones.contains(q)).stream()
                .min(Comparator.comparingDouble(q->q.position().distanceToSqr(jetPrevious))).orElse(null);
        if(hit!=null) {
            OrdnanceDamage.hurt(hit,bot.damageSources().mobAttack(bot),(float)config.collisionDamage());
            SurvivalGadgets.jetImpact(hit,jetDirection);stopJet(bot,level,now);return;
        }
        SurvivalGadgets.trail(level,bot.getUUID(),bot.position().subtract(jetDirection.scale(.4)),jetEnd+ExtraGadgetConfig.ticks(config.trailSeconds()));
        jetPrevious=bot.position();bot.setDeltaMovement(motion);bot.hasImpulse=true;bot.fallDistance=0;
        if(now%8==0)level.playSound(null,bot.blockPosition(),SoundEvents.BLAZE_BURN,SoundSource.PLAYERS,.9f,.8f);
    }
    private void stopJet(BotEntity bot,ServerLevel level,long now) {
        jetDirection=null;bot.setNoGravity(false);bot.fallDistance=0;bot.setDeltaMovement(Vec3.ZERO);
        SurvivalGadgets.endTrail(bot.getUUID(),now+ExtraGadgetConfig.ticks(ExtraGadgetConfig.jet.trailSeconds()));
        level.playSound(null,bot.blockPosition(),SoundEvents.FIRE_EXTINGUISH,SoundSource.PLAYERS,.6f,1.4f);
    }

    /** Uses the item as a player would, through a fake player that shares the bot's UUID and team. */
    private boolean use(BotEntity bot,ServerLevel level,String team,net.minecraft.world.entity.LivingEntity target) {
        var fake=net.minecraftforge.common.util.FakePlayerFactory.get(level,new GameProfile(bot.getUUID(),FAKE_NAME+(BotDatabase.slot(bot.getUUID())+1)));
        var scoreboard=level.getScoreboard();var side=scoreboard.getPlayerTeam(team.equals("ALPHA")?"Red":"Blue");
        if(side!=null&&fake.getTeam()!=side)scoreboard.addPlayerToTeam(fake.getScoreboardName(),side);
        Vec3 eye=bot.getEyePosition(),d=target.getEyePosition().subtract(eye);double flat=d.horizontalDistance();
        float yaw=(float)(Mth.atan2(d.z,d.x)*180/Math.PI)-90,pitch=(float)(-Mth.atan2(d.y+flat*.15,flat)*180/Math.PI);
        fake.moveTo(bot.getX(),bot.getY(),bot.getZ(),yaw,pitch);fake.setYHeadRot(yaw);
        var item=stack.copy();fake.setItemInHand(InteractionHand.MAIN_HAND,item);
        try {
            var result=item.use(level,fake,InteractionHand.MAIN_HAND);
            // Charged throwables release on letting go of the button; simulate a one-second charge.
            if(fake.isUsingItem()) {var using=fake.getUseItem();using.releaseUsing(level,fake,Math.max(0,using.getUseDuration()-20));fake.stopUsingItem();}
            if(!result.getResult().consumesAction()&&!result.getResult().shouldSwing())return false;
        } finally {fake.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);}
        bot.swing(InteractionHand.OFF_HAND);return true;
    }
}
