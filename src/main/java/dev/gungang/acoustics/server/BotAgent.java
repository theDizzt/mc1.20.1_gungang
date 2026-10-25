package dev.gungang.acoustics.server;

import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;

/**
 * One bot in a match: its body, loadout and mind. Each tick: perceive ({@link BotPerception}: only what this bot
 * could see, hear or feel) → decide ({@link BotTactics}, utility scores) → act ({@link BotActions}, behaviour trees)
 * → aim and fire ({@link BotAim}). Shared settings, profiles and events live in {@link BotManager}.
 */
final class BotAgent {
    final int slot;
    final UUID id;
    String team;
    BotEntity bot;
    BotGun gun;
    Loadouts.Weapon weapon,secondary;
    ItemStack primaryStack,secondaryStack;
    boolean usingSecondary,secondarySupplied;
    private int nextSwitch,switchedAt;
    final BotHealth health=new BotHealth();
    final BotMovement movement=new BotMovement();
    final BotGadgets gadgets=new BotGadgets();
    final BotPerception perception=new BotPerception();
    final BotTactics tactics=new BotTactics();
    final BotActions actions=new BotActions();
    final BotAim aim=new BotAim();
    private List<Loadouts.Weapon> primaryChoices=List.of(),secondaryChoices=List.of();
    private BotLoadoutChoice.Outcome lastLife;
    private final Map<String,List<BotLoadoutChoice.Outcome>> primaryHistory=new HashMap<>(),secondaryHistory=new HashMap<>();
    private boolean chooseOnSpawn=true;
    private int bornAt;
    private double lifeDamage,encounterDistance;
    String choiceReason="첫 출전";
    LivingEntity target;
    int respawnAt,nextThink,nextPath,nextShot;
    private long completedPoint,activePoint;

    BotAgent(int slot,String team){this.slot=slot;this.id=BotDatabase.id(slot);this.team=team;}

    void damageDealt(double amount){if(bot!=null&&Double.isFinite(amount)&&amount>0)lifeDamage+=amount;}
    BotEntity entity(){return bot;}
    String name(){return BotManager.name(slot);}

    /**
     * Mobs only tick inside a player's simulation distance, so a bot far from everyone would plan paths but never
     * walk them. A short-lived region ticket keeps its own chunks entity-ticking; it lapses on its own once the bot is gone.
     */
    private static final TicketType<net.minecraft.world.level.ChunkPos> CHUNKS=
            TicketType.create("gungang_bot",Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong),40);
    private void keepTicking(){
        var chunk=bot.chunkPosition();
        // Distance 3: the bot's chunk and its neighbours tick entities; three chunks around stay loaded for path searches.
        ((ServerLevel)bot.level()).getChunkSource().addRegionTicket(CHUNKS,chunk,3,chunk);
    }

    void activate(Loadouts loadouts)throws Exception{
        movement.routeMemory.clear();
        weapon=BotManager.selectWeapon(loadouts);secondary=BotManager.selectSecondary(loadouts);
        primaryChoices=loadouts.primary().stream().filter(w->{var stack=w.stack();return stack.is(Items.BOW)||BotGun.supports(stack);}).toList();
        secondaryChoices=loadouts.secondary().stream().filter(BotManager::supportedSecondary).toList();
        chooseOnSpawn=true;respawnAt=BotManager.server().getTickCount();
        MatchManager.botParticipant(id,name(),team);
        spawn();
    }
    private void spawn()throws Exception{
        var server=BotManager.server();var arena=BotManager.arena();var settings=BotManager.settings();
        var level=BotManager.level();if(level==null)throw new IllegalStateException("Bot dimension unavailable");
        if(chooseOnSpawn){
            String oldPrimary=lastLife==null?null:weapon.id(),oldSecondary=lastLife==null||secondary==null?null:secondary.id();
            weapon=choose(primaryChoices,settings.weapon(),oldPrimary,false);
            secondary=choose(secondaryChoices,settings.secondary(),oldSecondary,true);
            choiceReason=lastLife==null?"첫 출전: 체력·이동·사거리·화력·장비 역할 비교":
                    !lastLife.combat()?"비전투 사망: 장비 유지":lastLife.productive()?"직전 교전 성과 양호: 자동 장비 유지":
                    "직전 교전 성과 낮음: 교전 거리·무기 성능·최근 장비별 성과로 대안 비교";
            chooseOnSpawn=false;
            LogUtils.getLogger().info("[GunGang] Bot {} loadout primary={} secondary={} reason={}",slot+1,weapon.id(),secondary==null?"none":secondary.id(),choiceReason);
        }
        var spawn=team.equals("ALPHA")?arena.alphaSpawn():arena.bravoSpawn();
        level.getChunk(net.minecraft.core.BlockPos.containing(spawn.x(),spawn.y(),spawn.z()));
        bot=BotEntity.TYPE.get().create(level);if(bot==null)throw new IllegalStateException("Bot creation failed");
        bot.setUUID(id);bot.moveTo(spawn.x(),spawn.y(),spawn.z(),spawn.yaw(),spawn.pitch());
        bot.setCustomName(Component.literal(name()).withStyle(LobbyManager.options().formatting(team)));bot.setCustomNameVisible(true);
        bot.skin(BotManager.skin(slot));
        bot.getAttribute(Attributes.MAX_HEALTH).setBaseValue(weapon.maxHealth());
        usingSecondary=false;secondarySupplied=false;nextSwitch=switchedAt=0;health.reset();movement.reset();
        primaryStack=weapon.stack();secondaryStack=secondary==null?ItemStack.EMPTY:secondary.stack();
        updateSpeed();
        gadgets.equip(bot,secondary,secondaryStack,heldSecondary());
        perception.reset();tactics.reset(server.getTickCount());actions.reset();aim.reset(bot);
        bot.setHealth(bot.getMaxHealth());bot.setItemSlot(EquipmentSlot.MAINHAND,primaryStack);
        bot.setDropChance(EquipmentSlot.MAINHAND,0);
        var scoreboard=server.getScoreboard();String key=team.equals("ALPHA")?"Red":"Blue";
        var side=scoreboard.getPlayerTeam(key);if(side==null)side=scoreboard.addPlayerTeam(key);
        side.setColor(LobbyManager.options().formatting(team));scoreboard.addPlayerToTeam(bot.getScoreboardName(),side);
        if(!level.addFreshEntity(bot))throw new IllegalStateException("Bot entity was rejected");
        keepTicking();
        // Preparation gates are closed. Search only once running, after the gates have opened.
        movement.prepareSpawnExit();
        gun=bot.getMainHandItem().is(Items.BOW)?null:new BotGun(bot);
        bornAt=server.getTickCount();lifeDamage=0;encounterDistance=0;
        // Each bot thinks and paths on its own offset, so several bots never pile their work onto the same tick.
        int now=server.getTickCount();target=null;nextShot=now;
        nextThink=now+slot*settings.thinkTicks()/BotManager.MAX;nextPath=now+slot*settings.pathTicks()/BotManager.MAX;
    }
    void remove(){
        var server=BotManager.server();
        if(bot!=null){
            if(server!=null)server.getScoreboard().removePlayerFromTeam(bot.getScoreboardName());
            bot.discard();bot=null;
        }
        if(server!=null)for(var level:server.getAllLevels())for(var entity:level.getAllEntities())
            if(entity instanceof Projectile projectile&&projectile.getOwner()!=null&&id.equals(projectile.getOwner().getUUID()))projectile.discard();
        gadgets.reset(null);gun=null;target=null;perception.reset();actions.reset();health.reset();movement.reset();
    }
    /** Match over: also clear this bot's deployables and cooldowns. */
    void close(){
        movement.routeMemory.clear();
        var server=BotManager.server();
        if(server!=null){GadgetManager.clearOwned(server,id);BearTraps.clear(server,id);}
        gadgets.clearCooldowns();remove();
    }
    private Loadouts.Weapon choose(List<Loadouts.Weapon> choices,String fixed,String previous,boolean secondarySlot){
        var options=choices.stream().map(w->{
            var stack=w.stack();boolean melee=BotManager.melee(stack),held=BotManager.held(stack);
            var data=stack.is(Items.BOW)?BotWeaponData.BOW:BotWeaponData.read(stack);
            var profile=BotCombatProfile.of(data.type(),w.category());
            String role=BotGadgets.kind(stack,held);
            return new BotLoadoutChoice.Option(w.id(),w.maxHealth(),w.movementSpeed(),melee,
                    melee?0:role.isEmpty()?profile.near():0,melee?3:role.isEmpty()?profile.far():0,
                    data.damage(),data.rpm(),data.magazine(),data.reloadSeconds(),role);
        }).toList();
        String chosen=BotLoadoutChoice.choose(options,fixed,previous,lastLife,secondarySlot,secondarySlot?secondaryHistory:primaryHistory);
        return chosen==null?null:choices.stream().filter(w->w.id().equals(chosen)).findFirst().orElseThrow();
    }
    /** Sprinting player pace per unit of forward input: speed 0.1 × sprint 1.3. */
    private static final double PLAYER_SPRINT=.1*1.3;
    /**
     * Path-walking mobs move attribute² (MoveControl feeds the speed in as the forward input as well), players
     * input 1 × attribute; both then lose the same 0.98 input damping. So the attribute is the square root of the
     * target pace: a sprinting player at the weapon's speed, scaled by the bot multiplier (1.0 = exactly that player).
     */
    void updateSpeed(){
        if(bot!=null&&weapon!=null)bot.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(
                Math.sqrt(PLAYER_SPRINT*(usingSecondary?secondary:weapon).movementSpeed()/100*BotManager.settings().movementSpeed()));
    }
    private boolean heldSecondary(){return secondaryStack!=null&&!secondaryStack.isEmpty()&&BotManager.held(secondaryStack);}
    private void equip(boolean useSecondary,int now)throws ReflectiveOperationException{
        if(useSecondary==usingSecondary||useSecondary&&!heldSecondary()||now<nextSwitch)return;
        if(gun!=null)gun.unequip();
        usingSecondary=useSecondary;
        bot.setItemSlot(EquipmentSlot.MAINHAND,useSecondary?secondaryStack:primaryStack);
        gun=BotGun.supports(bot.getMainHandItem())?new BotGun(bot,useSecondary&&!secondarySupplied):null;
        if(useSecondary)secondarySupplied=true;
        updateSpeed();switchedAt=now;nextSwitch=now+60;nextShot=now+4;nextPath=0;
    }
    void died(UUID killer){
        var server=BotManager.server();
        lastLife=new BotLoadoutChoice.Outcome(killer!=null||lifeDamage>0||encounterDistance>0,lifeDamage,
                Math.max(0,server.getTickCount()-bornAt),encounterDistance>0?encounterDistance:12);
        remember(primaryHistory,weapon.id(),lastLife);
        if(secondary!=null)remember(secondaryHistory,secondary.id(),lastLife);
        chooseOnSpawn=true;
        remove();respawnAt=server.getTickCount()+LobbyManager.options().respawnSeconds()*20;
    }
    private static void remember(Map<String,List<BotLoadoutChoice.Outcome>> history,String id,BotLoadoutChoice.Outcome outcome) {
        var entries=history.computeIfAbsent(id,key->new ArrayList<>());entries.add(outcome);
        if(entries.size()>4)entries.remove(0);
    }

    void tick(boolean running)throws Exception{
        var server=BotManager.server();var arena=BotManager.arena();var settings=BotManager.settings();
        // Keep countdown spawns loaded too, and move the ticket immediately at chunk boundaries.
        if(bot!=null&&!bot.isRemoved())keepTicking();
        if(!running){if(bot!=null)bot.getNavigation().stop();return;}
        if(bot==null){if(server.getTickCount()>=respawnAt)spawn();return;}
        if(bot.isRemoved()){remove();respawnAt=server.getTickCount()+20;return;}
        var bounds=bot.getBoundingBox();
        if(bot.getY()<bot.level().getMinBuildHeight()||arena.deathZones().stream().anyMatch(z->z.intersects(bounds.minX,bounds.minY,bounds.minZ,bounds.maxX,bounds.maxY,bounds.maxZ))){
            MatchManager.botDeath(id,null);return;
        }
        int now=server.getTickCount();
        if(bot.parkour.active()){aim.idle(bot);return;}
        if(bot.isInWater())bot.getJumpControl().jump();
        boolean thought=now>=nextThink;
        if(thought){nextThink=now+settings.thinkTicks();think(now);}
        if(target!=null&&!eligible(target))target=null;
        if(usingSecondary&&(target==null||BotManager.melee(secondaryStack)&&bot.distanceToSqr(target)>25||!BotManager.melee(secondaryStack)&&now-switchedAt>200))equip(false,now);
        else if(!usingSecondary&&target!=null&&BotManager.melee(secondaryStack)&&bot.distanceToSqr(target)<9)equip(true,now);
        boolean seen=target!=null&&visible(target);
        var action=tactics.action();
        boolean traveling=Set.of(BotTactics.Action.PATROL,BotTactics.Action.INVESTIGATE,BotTactics.Action.REGROUP,BotTactics.Action.SUPPORT).contains(action);
        gadgets.tick(bot,arena,team,target,seen,movement,thought,traveling);
        if(bot==null||bot.isRemoved())return;
        long time=bot.level().getGameTime();
        if(gadgets.jetting()){aim.idle(bot);return;} // The jetpack steers the body.
        // A known spawn exit outranks wandering toward sounds or regrouping, but never an immediate threat.
        if(!seen&&!perception.underFire(now)&&perception.danger(bot.position())==null&&bot.getHealth()>=bot.getMaxHealth()*.6f) {
            boolean leaving=movement.leavingSpawn();
            if(movement.exitSpawn(bot,arena,team,now)) {aim.idle(bot);return;}
            if(leaving)nextPath=now; // Continue out into the map without waiting for the patrol interval.
        }
        boolean pathTurn=now>=nextPath;if(pathTurn)nextPath=now+settings.pathTicks();
        boolean melee=BotManager.melee(bot.getMainHandItem()),reloading=gun!=null&&gun.reloading();
        // Decide: everything the bot knows, scored. A raised shield keeps it fighting while backing off.
        var lead=perception.lead(now);var ally=perception.nearestAlly(bot);
        var point=TeamPoints.latest(team,bot.level().dimension().location().toString(),completedPoint);
        if(point!=null&&point.sequence()!=activePoint) {
            if(tactics.action()==BotTactics.Action.RALLY)actions.reset();
            activePoint=point.sequence();nextPath=now;pathTurn=true;
        }
        if(point==null&&tactics.action()==BotTactics.Action.RALLY)actions.reset();
        var situation=new BotTactics.Situation(bot.getHealth()/bot.getMaxHealth(),gun==null?1:gun.ammo(bot.getMainHandItem()),reloading,
                seen?Math.max(1,perception.visible()):0,perception.alliesNear(bot,16),perception.allies().size(),perception.engagedAlly(now)!=null,
                seen?bot.distanceTo(target):Double.POSITIVE_INFINITY,perception.underFire(now),melee,
                bot.getHealth()<bot.getMaxHealth()*.6f&&health.available(bot,now,settings.range()),
                lead==null?Long.MAX_VALUE:now-lead.updated,ally!=null&&ally.distanceToSqr(bot)>24*24
                        &&BotMovement.forwardAlly(arena,team,bot.position(),ally.position()),perception.danger(bot.position())!=null);
        // Finish the bounded hide/peek/return cycle unless an urgent close threat or low health interrupts it.
        action=tactics.choose(situation,now,
                actions.holdingCover()&&!melee&&bot.getHealth()>bot.getMaxHealth()*.2f&&(!seen||bot.distanceToSqr(target)>16),
                gadgets.shielding(time)&&seen,point!=null);
        // Act: the chosen behaviour's tree.
        var ctx=actions.ctx();
        ctx.bot=bot;ctx.arena=arena;ctx.team=team;ctx.settings=settings;ctx.now=now;ctx.pathTurn=pathTurn;ctx.melee=melee;
        ctx.shielding=gadgets.shielding(time);ctx.reloading=reloading;ctx.target=seen?target:null;
        ctx.ammo=gun==null?1:gun.ammo(bot.getMainHandItem());ctx.reloadRequested=false;
        ctx.profile=BotCombatProfile.of(gun==null?"":gun.type(),(usingSecondary?secondary:weapon).category());
        movement.profile(ctx.profile);
        ctx.perception=perception;ctx.movement=movement;ctx.health=health;ctx.aim=aim;
        ctx.teamPoint=point==null?null:new Vec3(point.x(),point.y(),point.z());
        var status=actions.run(action);
        if(action==BotTactics.Action.RALLY&&status!=BotTree.Status.RUNNING&&point!=null)completedPoint=point.sequence();
        if(ctx.reloadRequested&&gun!=null&&!gadgets.blocksAttack(time))gun.reload();
        if(status==BotTree.Status.FAILURE)tactics.failed(action,now);
        // Finished its job: rest the action a moment so the bot moves on (re-engage after cover, etc.).
        else if(status==BotTree.Status.SUCCESS)tactics.completed(action,now,action==BotTactics.Action.COVER?120:60);
        // Aim and fire: a visible target always gets the crosshair, whatever the legs are doing.
        if(seen){
            var contact=perception.contact(target.getUUID());
            boolean bow=bot.getMainHandItem().is(Items.BOW);
            var flight=bow?BotWeaponData.BOW:gun==null?BotWeaponData.UNKNOWN:gun.ballistics();
            aim.track(bot,target,contact==null?null:contact.velocity,settings,now,melee?0:flight.speed(),flight.gravity(),flight.drag());
            if(ctx.allowFire&&(melee||bot.distanceTo(target)<=ctx.profile.engage())&&now>=nextShot&&(gun==null||!gun.reloading())&&!gadgets.blocksAttack(time)&&aim.ready(bot,target,now,melee)){
                nextShot=now+1;
                if(melee||BotShot.clear(bot,aim.yaw(),aim.pitch(),Math.min(80,bot.distanceTo(target)+8),flight))shoot(target);
            }
        }
    }
    /** An enemy of this bot that it may fight: a player or another bot on the other team, within range. */
    boolean eligible(LivingEntity e){
        int range=BotManager.settings().visionRange();
        return enemy(e)&&bot.distanceToSqr(e)<=range*range;
    }
    /** Damage cues are valid beyond the visual acquisition radius. */
    private boolean enemy(LivingEntity e){
        if(e==null||e==bot||!e.isAlive()||e.isSpectator()||e.level()!=bot.level())return false;
        if(e instanceof ServerPlayer p&&(p.isCreative()||LobbyManager.respawning(p.getUUID())))return false;
        if(!(e instanceof ServerPlayer)&&!(e instanceof BotEntity))return false;
        String side=BotManager.side(e);
        return (side.equals("ALPHA")||side.equals("BRAVO"))&&!side.equals(team);
    }
    private boolean visible(LivingEntity e){return eligible(e)&&bot.hasLineOfSight(e)&&!SpawnGateProtection.separates(bot,e);}
    private void think(int now)throws ReflectiveOperationException{
        perception.update(bot,team,BotManager.settings(),now);
        movement.allies(perception.allies().stream().map(Entity::position).toList());
        LivingEntity previous=target;
        target=perception.target(bot,previous,now);
        if(target!=null){
            double distance=bot.distanceTo(target);encounterDistance=encounterDistance==0?distance:encounterDistance*.75+distance*.25;
            if(target!=previous)nextPath=0;
        }else if(gun!=null)gun.stop();
    }
    /** Fires along the bot's current aim (spread, tremor and lead included), not straight at the target. */
    private void shoot(LivingEntity enemy)throws ReflectiveOperationException{
        var server=BotManager.server();
        float yaw=aim.yaw(),pitch=aim.pitch();
        bot.setYRot(yaw);bot.setYHeadRot(yaw);bot.setXRot(pitch);
        if(gun!=null){
            String result=gun.fire(pitch,yaw);
            BotManager.heard(bot);
            if(result.equals("NO_AMMO")&&!usingSecondary&&heldSecondary()
                    &&(!BotManager.melee(secondaryStack)||bot.distanceToSqr(enemy)<9))equip(true,server.getTickCount());
            return;
        }
        if(BotManager.melee(bot.getMainHandItem())){
            if(bot.distanceToSqr(enemy)<=7.84){bot.doHurtTarget(enemy);bot.swing(net.minecraft.world.InteractionHand.MAIN_HAND);nextShot=server.getTickCount()+12;}
            return;
        }
        var arrow=new Arrow(bot.level(),bot);arrow.setBaseDamage(6);arrow.pickup=AbstractArrow.Pickup.DISALLOWED;
        Vec3 direction=Vec3.directionFromRotation(pitch,yaw);
        arrow.shoot(direction.x,direction.y,direction.z,1.8f,.5f);
        bot.level().addFreshEntity(arrow);bot.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        bot.playSound(net.minecraft.sounds.SoundEvents.SKELETON_SHOOT,1,1);nextShot=server.getTickCount()+30;
        BotManager.heard(bot);
    }
    /** Enemy gunfire within earshot sends an idle bot to investigate, the way players react to shots. */
    void heard(LivingEntity shooter){
        if(bot==null||shooter==bot||shooter.level()!=bot.level())return;
        String side=BotManager.side(shooter);
        if(side.equals(team)||!(side.equals("ALPHA")||side.equals("BRAVO")))return;
        perception.gunfire(bot,shooter,BotManager.server().getTickCount());
    }
    void hurt(net.minecraftforge.event.entity.living.LivingHurtEvent event){
        int now=BotManager.server().getTickCount();
        gadgets.hurt(bot,event);
        // Shot by someone: remember roughly where from and re-think now; unseen attackers get investigated (turned toward).
        if(event.getAmount()>0&&event.getSource().getEntity() instanceof LivingEntity attacker&&enemy(attacker)){
            movement.hit(bot,event.getAmount(),now);nextPath=now;
            perception.hit(bot,attacker,now,event.getAmount());nextThink=now;
        }
    }
    /** A teammate under fire: the bot learns about it as from a callout. */
    void allyHit(LivingEntity ally,LivingEntity attacker){
        if(bot!=null&&ally!=bot&&eligible(attacker))perception.allyHit(bot,ally,attacker,BotManager.server().getTickCount());
    }
    /** A score, or "쉼" while the action rests after failing or finishing (held at -∞ by tactics). */
    private String score(BotTactics.Action action){float v=tactics.lastScore(action);return Float.isInfinite(v)?"쉼":String.format(Locale.ROOT,"%.0f",v);}
    String status(){
        var server=BotManager.server();
        if(bot==null)return String.format(Locale.ROOT,"%s: %s팀 · 리스폰 대기",name(),team);
        return String.format(Locale.ROOT,"%s: %s팀 · 행동=%s %.1f초째 (교전 %s·엄폐 %s·후퇴 %s·합류 %s·지원 %s·추적 %s) · 인지 %d명(보임 %d) · 위험 지점 %d · 주무기=%s 보조=%s · %s",
                name(),team,tactics.action().label,(server.getTickCount()-tactics.since())/20.0,
                score(BotTactics.Action.ENGAGE),score(BotTactics.Action.COVER),score(BotTactics.Action.RETREAT),
                score(BotTactics.Action.REGROUP),score(BotTactics.Action.SUPPORT),score(BotTactics.Action.INVESTIGATE),
                perception.known(),perception.visible(),perception.hazardCount(),weapon==null?"대기":weapon.id(),secondary==null?"없음":secondary.id(),gadgets.describe(bot));
    }
}
