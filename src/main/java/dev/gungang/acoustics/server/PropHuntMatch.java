package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.*;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID)
public final class PropHuntMatch {
    private static final class Member {
        final UUID id;final String name,team;final boolean invisible;final double health;
        PropEntity prop;boolean fixed;Vec3 anchor;float yaw,pitch;int waiting,charge=-1,hazard,horn,blast,shot;
        UUID watching;
        final Map<String,Integer> actions=new HashMap<>();
        Member(ServerPlayer p,String team){id=p.getUUID();name=p.getGameProfile().getName();this.team=team;invisible=p.isInvisible();health=p.getAttribute(Attributes.MAX_HEALTH).getBaseValue();}
    }
    private final MinecraftServer server;
    private final ArenaMap map;
    private Loadouts.Weapon shotgun;
    /** /gungang debug prop session: always hunting, no timers or winner, players join and leave one by one. */
    private final boolean test;
    private final Map<UUID,String> items=new HashMap<>();
    private record Saved(net.minecraft.nbt.ListTag inventory,GameType mode,double health,boolean invisible){}
    private final Map<UUID,Saved> saved=new HashMap<>();
    private final Map<UUID,Member> members=new LinkedHashMap<>();
    private final Random random=new Random();
    /** Dispenser arrows still in the world; all removed when the match closes. */
    private final List<net.minecraft.world.entity.projectile.Arrow> arrows=new ArrayList<>();
    public final PropHuntGame game;
    private boolean reviewing,rushing;
    private static final UUID RUSH=UUID.fromString("5a0b8f0e-6c1d-4c5e-9a7b-2f4e1d3c6b70");
    /** Final minute: seekers move 35% faster (transient, re-applied every tick, removed on review and restore). */
    private static void rush(ServerPlayer p,boolean on){
        var speed=p.getAttribute(Attributes.MOVEMENT_SPEED);if(speed==null)return;boolean has=speed.getModifier(RUSH)!=null;
        if(on&&!has)speed.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(RUSH,"gungang_prop_rush",.35,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
        else if(!on&&has)speed.removeModifier(RUSH);
    }
    public static Loadouts.Weapon validate(MinecraftServer server,ArenaMap map,Loadouts loadouts){
        var gun=shotgun(loadouts,map.propHunt().shotgun());
        var hiders=server.getPlayerList().getPlayers().stream().filter(p->LobbyManager.team(p.getUUID()).equals("ALPHA")).map(ServerPlayer::getUUID).toList();
        if(server.getPlayerList().getPlayers().stream().noneMatch(p->LobbyManager.team(p.getUUID()).equals("BRAVO")))throw new IllegalArgumentException("술래가 필요합니다.");
        new PropHuntGame(PropCatalog.available(),hiders,new Random(),PropHuntGame.huntTicks(map));return gun;
    }
    /** Weapon ID, or the M1014 when empty; must be a real TaCZ shotgun. */
    static Loadouts.Weapon shotgun(Loadouts loadouts,String id){
        var gun=loadouts.primary().stream().filter(w->id.isEmpty()?w.itemNbt().contains("tacz:m1014"):w.id().equals(id)).findFirst()
                .orElseThrow(()->new IllegalArgumentException("숨바꼭질 산탄총을 찾을 수 없습니다. propHunt.shotgun에 산탄총 무기 ID를 지정하세요 (기본 M1014)."));
        var stack=gun.stack();
        if(!net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).toString().equals("tacz:modern_kinetic_gun"))throw new IllegalArgumentException("숨바꼭질은 TaCZ 산탄총이 필요합니다.");
        try{
            var api=Class.forName("com.tacz.guns.api.TimelessAPI");
            var index=(Optional<?>)api.getMethod("getCommonGunIndex",net.minecraft.resources.ResourceLocation.class).invoke(null,PropCatalog.id(stack.getOrCreateTag().getString("GunId")));
            if(index.isEmpty()||!"shotgun".equalsIgnoreCase(String.valueOf(index.get().getClass().getMethod("getType").invoke(index.get()))))throw new IllegalArgumentException("propHunt.shotgun은 실제 shotgun(산탄총) 종류여야 합니다.");
        }catch(ReflectiveOperationException e){throw new IllegalArgumentException("TaCZ 산탄총 데이터를 확인할 수 없습니다.",e);}
        return gun;
    }
    public PropHuntMatch(MinecraftServer server,ArenaMap map,Loadouts.Weapon shotgun){
        this.server=server;this.map=map;this.shotgun=shotgun;test=false;
        for(var p:server.getPlayerList().getPlayers())if(Set.of("ALPHA","BRAVO").contains(LobbyManager.team(p.getUUID())))members.put(p.getUUID(),new Member(p,LobbyManager.team(p.getUUID())));
        game=new PropHuntGame(PropCatalog.available(),members.values().stream().filter(m->m.team.equals("ALPHA")).map(m->m.id).toList(),random,PropHuntGame.huntTicks(map));
    }
    private PropHuntMatch(MinecraftServer server){this.server=server;map=null;test=true;game=new PropHuntGame();}
    private PropHuntGame.Phase phase(){return test?PropHuntGame.Phase.HUNTING:game.phase();}
    private static PropHuntMatch testMatch;
    /** The debug prop session, or null. */
    public static PropHuntMatch testMatch(){return testMatch;}
    /**
     * /gungang debug prop: ALPHA disguises as item, BRAVO becomes a tagger, null team restores the player.
     * The first join saves inventory, game mode and health; the last leave ends the session.
     */
    public static void test(ServerPlayer p,String team,String item){
        if(team==null){if(testMatch!=null){testMatch.stop(p);if(testMatch.members.isEmpty())testMatch=null;}return;}
        if(testMatch==null)testMatch=new PropHuntMatch(p.server);
        testMatch.become(p,team,item);
    }
    public static boolean testing(UUID id){return testMatch!=null&&testMatch.participant(id);}
    public static void endTest(){if(testMatch!=null){var session=testMatch;testMatch=null;session.close();}}
    private void become(ServerPlayer p,String team,String item){
        if(team.equals("BRAVO")&&shotgun==null)try{shotgun=shotgun(Loadouts.load(),"");}catch(java.io.IOException e){throw new IllegalStateException(e);}
        var old=members.remove(p.getUUID());
        if(old!=null){home(p,old);if(old.prop!=null){PropInteraction.remove(old.prop);old.prop.discard();}game.remove(old.id);}
        else saved.put(p.getUUID(),new Saved(p.getInventory().save(new net.minecraft.nbt.ListTag()),p.gameMode.getGameModeForPlayer(),p.getAttribute(Attributes.MAX_HEALTH).getBaseValue(),p.isInvisible()));
        var m=new Member(p,team);members.put(m.id,m);
        if(team.equals("ALPHA")){items.put(m.id,item);game.join(m.id);}else{items.remove(m.id);p.setInvisible(saved.get(m.id).invisible());}
        p.setCamera(p);p.setGameMode(GameType.ADVENTURE);equip(p);p.refreshDimensions();sync();
    }
    private void stop(ServerPlayer p){
        var m=members.remove(p.getUUID());if(m==null)return;
        home(p,m);if(m.prop!=null){PropInteraction.remove(m.prop);m.prop.discard();}
        game.remove(m.id);items.remove(m.id);var s=saved.remove(m.id);
        p.setCamera(p);LobbyManager.clearPlayerInventory(p);LoadoutAttributes.clear(p);SecondaryRuntime.clear(p);
        p.getInventory().load(s.inventory());p.inventoryMenu.broadcastChanges();
        p.setGameMode(s.mode());p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(s.health());p.setHealth(p.getMaxHealth());
        p.setInvisible(s.invisible());p.removeEffect(MobEffects.BLINDNESS);p.refreshDimensions();
        GungangNetwork.send(p,new GungangNetwork.PropHunt(ServerFiles.GSON.toJson(new PropHuntSnapshot(false,"",0,0,List.of()))));
        sync();
    }
    @SubscribeEvent public static void testTick(net.minecraftforge.event.TickEvent.ServerTickEvent e){if(e.phase==net.minecraftforge.event.TickEvent.Phase.END&&testMatch!=null)testMatch.tick();}
    @SubscribeEvent public static void stopping(net.minecraftforge.event.server.ServerStoppingEvent e){endTest();}
    public boolean participant(UUID id){return members.containsKey(id);}
    public boolean hider(UUID id){var m=members.get(id);return m!=null&&m.team.equals("ALPHA");}
    public boolean frozen(UUID id){var m=members.get(id);return m!=null&&!reviewing&&(m.fixed||m.waiting>server.getTickCount()||m.team.equals("BRAVO")&&phase()==PropHuntGame.Phase.HIDING);}
    public void equip(ServerPlayer p){
        var m=members.get(p.getUUID());if(m==null)return;
        LobbyManager.clearPlayerInventory(p);LoadoutAttributes.clear(p);SecondaryRuntime.clear(p);
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(m.team.equals("ALPHA")?50:100);p.setHealth(p.getMaxHealth());
        if(m.team.equals("BRAVO")){
            var stack=shotgun.stack();stack.getOrCreateTag().putBoolean("GunGangPropShotgun",true);stack.getOrCreateTag().putInt("DummyAmmo",9999);stack.getOrCreateTag().putInt("MaxDummyAmmo",9999);
            LoadoutSlots.mark(stack,"GunGangIssued",0);p.getInventory().setItem(0,stack);p.getInventory().selected=0;
        }else{p.setInvisible(true);if(m.prop==null)change(p,m);}
        p.inventoryMenu.broadcastChanges();
    }
    private void change(ServerPlayer p,Member m){
        // A health pack sunk into the floor comes back up first, so the new block never starts inside the ground.
        boolean sunk=m.prop!=null&&m.prop.sunk();
        if(m.prop!=null){PropInteraction.remove(m.prop);m.prop.discard();}p.closeContainer();
        String item=test?items.get(m.id):PropCatalog.variant(game.draw(),random);
        m.prop=PropEntity.TYPE.get().create(p.serverLevel());m.prop.setup(m.id,item,random.nextInt(26));m.prop.setPos(m.fixed?m.anchor:p.position());
        p.serverLevel().addFreshEntity(m.prop);
        p.refreshDimensions();
        if(m.watching==null)unstick(p);
        if(m.fixed&&m.watching==null)m.anchor=p.position();
        if(sunk)p.sendSystemMessage(Component.literal("블록이 바뀌어 생명력 팩 매설이 풀렸습니다."));
        p.displayClientMessage(Component.literal("변신: ").append(new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(PropCatalog.id(item))).getHoverName()).append(" ["+PropCatalog.number(item)+"]"),true);
        m.hazard=server.getTickCount()+80;
    }
    public void tick(){
        if(phase()==PropHuntGame.Phase.FINISHED){LobbyManager.finish(game.winner());return;}
        // Keep the real hiding place loaded even while its owner watches a distant player.
        for(var m:members.values())if(m.prop!=null)((ServerLevel)m.prop.level()).getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.POST_TELEPORT,new net.minecraft.world.level.ChunkPos(m.prop.blockPosition()),2,m.prop.getId());
        boolean change=!test&&game.tick();
        if(phase()==PropHuntGame.Phase.REVIEW&&!reviewing){
            reviewing=true;
            for(var m:members.values()){if(m.prop!=null)mark(m);var p=server.getPlayerList().getPlayer(m.id);if(p!=null){rush(p,false);p.removeEffect(MobEffects.BLINDNESS);p.setCamera(p);p.setGameMode(GameType.SPECTATOR);p.sendSystemMessage(Component.literal(""+game.winner()+" 승리! 30초 동안 숨었던 위치를 둘러보세요."));}}
        }
        boolean rush=!test&&game.finalMinute();
        if(rush&&!rushing)for(var m:members.values()){var p=server.getPlayerList().getPlayer(m.id);if(p!=null)p.sendSystemMessage(Component.literal("마지막 1분 · 블록 교체 없음 · 술래 이동속도 +35%"));}
        rushing=rush;
        if(!reviewing)for(var m:members.values()){
            var p=server.getPlayerList().getPlayer(m.id);if(p==null)continue;
            if(m.team.equals("ALPHA")){
                if(!game.alive(m.id))continue;
                p.setInvisible(true);
                if(change)change(p,m);
                if(m.watching!=null){var camera=camera(m.watching);if(camera==null)home(p,m);else if(p.getCamera()!=camera)p.setCamera(camera);}
                if(m.fixed&&m.watching==null){hold(p,m.anchor,m.yaw,m.pitch);}
                if(m.prop!=null){p.setAirSupply(p.getMaxAirSupply());m.prop.setPos(spot(m,p));if(m.fixed)look(m);submerge(m.prop);hazards(p,m);}
            }else{
                rush(p,rush);
                if(phase()==PropHuntGame.Phase.HIDING||m.waiting>server.getTickCount()){
                    var s=map.bravoSpawn();hold(p,new Vec3(s.x(),s.y(),s.z()),s.yaw(),s.pitch());
                    if(phase()==PropHuntGame.Phase.HIDING)p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,30,0,false,false,false));
                    p.stopUsingItem();
                }else p.removeEffect(MobEffects.BLINDNESS);
                // The mode permits only its issued primary; no pickups or secondary gadgets.
                if(!p.getInventory().getItem(0).getOrCreateTag().getBoolean("GunGangPropShotgun")){
                    ItemStack gun=ItemStack.EMPTY;
                    for(int i=1;i<p.getInventory().getContainerSize();i++)if(p.getInventory().getItem(i).getOrCreateTag().getBoolean("GunGangPropShotgun")){gun=p.getInventory().getItem(i);p.getInventory().setItem(i,ItemStack.EMPTY);break;}
                    if(!gun.isEmpty())p.getInventory().setItem(0,gun);
                }
                for(int i=1;i<p.getInventory().getContainerSize();i++)p.getInventory().setItem(i,ItemStack.EMPTY);
                p.getInventory().selected=0;
            }
        }
        if(server.getTickCount()%4==0)sync();
    }
    /** A bigger new disguise must not start inside walls or the floor: step aside, or failing that, up onto free space. */
    private static void unstick(ServerPlayer p){
        if(p.level().noCollision(p))return;
        for(double up=0;up<=2;up+=.25)for(double x=-1;x<=1;x+=.5)for(double z=-1;z<=1;z+=.5){
            var to=p.position().add(x,up,z);
            if(p.level().noCollision(p,p.getBoundingBox().move(to.subtract(p.position())))){p.connection.teleport(to.x,to.y,to.z,p.getYRot(),p.getXRot());return;}
        }
    }
    private void hold(ServerPlayer p,Vec3 pos,float yaw,float pitch){
        p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;
        if(p.position().distanceToSqr(pos)>.0001||p.getYRot()!=yaw||p.getXRot()!=pitch)p.connection.teleport(pos.x,pos.y,pos.z,yaw,pitch);
    }
    private void hazards(ServerPlayer owner,Member m){
        if(phase()!=PropHuntGame.Phase.HUNTING)return;
        var prop=m.prop;String id=prop.item();
        if(prop.state().getBlock() instanceof NozzleBlock nozzle){
            // Spray particles keep running; only seekers in the column are affected (acid: 50 per second, water: puts out fire).
            nozzle.particles(owner.serverLevel(),prop.blockPosition());
            var column=nozzle.column(prop.blockPosition());boolean hit=false;
            if(nozzle.acid()&&server.getTickCount()<m.hazard)return;
            for(var target:server.getPlayerList().getPlayers()){
                var other=members.get(target.getUUID());if(other==null||!other.team.equals("BRAVO")||frozen(other.id)||target.level()!=owner.level()||!target.getBoundingBox().intersects(column))continue;
                if(nozzle.acid()){target.hurt(owner.damageSources().magic(),CONTACT_DAMAGE);AcidBlock.poison(owner.level(),target);hit=true;}else target.clearFire();
            }
            if(hit)m.hazard=server.getTickCount()+20;
            return;
        }
        if(server.getTickCount()<m.hazard)return;
        for(var target:server.getPlayerList().getPlayers()){
            var other=members.get(target.getUUID());if(other==null||!other.team.equals("BRAVO")||frozen(other.id)||target.level()!=owner.level())continue;
            if(!target.getBoundingBox().inflate(.15).intersects(prop.getBoundingBox()))continue;
            if(id.equals("gungang:uxo")){
                m.hazard=server.getTickCount()+100;
                explode(prop,3);
            }else if(id.endsWith("health_pack")){
                var config=id.contains("large")?GadgetConfig.largeHealthPack:GadgetConfig.smallHealthPack;
                if(target.getHealth()<target.getMaxHealth()){target.heal((float)config.heal());m.hazard=server.getTickCount()+(int)Math.ceil(config.cooldownSeconds()*20);}
            }else if(contactDamage(prop.state())){
                // Damaging blocks deal a fixed hit to seekers only (never another hider), at most once a second per disguise.
                target.hurt(contactSource(owner,prop.state()),CONTACT_DAMAGE);m.hazard=server.getTickCount()+20;
                // Status effects still apply on top (acid puddle poison).
                if(prop.state().getBlock() instanceof PuddleBlock)prop.state().entityInside(owner.level(),prop.blockPosition(),target);
            }else{
                // Other blocks keep their real contact behavior (webs, acid, water, etc.).
                prop.state().entityInside(owner.level(),prop.blockPosition(),target);
                prop.state().getBlock().stepOn(owner.level(),prop.blockPosition(),prop.state(),target);
            }
        }
    }
    public boolean hit(PropEntity prop,DamageSource source,float amount){
        if(reviewing||phase()!=PropHuntGame.Phase.HUNTING||!game.alive(prop.owner())||!Float.isFinite(amount)||amount<=0||SpawnGateProtection.blocked(prop,source)||SafeZones.blocked(prop,source))return false;
        if(!(source.getEntity() instanceof ServerPlayer shooter)||hider(shooter.getUUID())||!participant(shooter.getUUID())||frozen(shooter.getUUID()))return false;
        var direct=source.getDirectEntity();
        if(direct==null||!direct.getClass().getName().equals("com.tacz.guns.entity.EntityKineticBullet")||!shooter.getMainHandItem().getOrCreateTag().getBoolean("GunGangPropShotgun"))return false;
        var p=server.getPlayerList().getPlayer(prop.owner());if(p==null)return false;
        if(p.getHealth()<=amount)eliminate(p,shooter);else p.setHealth(p.getHealth()-amount);
        if(prop.item().equals("gungang:gas_barrel")){
            var m=members.get(prop.owner());if(server.getTickCount()>=m.hazard){m.hazard=server.getTickCount()+100;explode(prop,GimmickConfig.gas.radius());}
        }
        return true;
    }
    /** Contact damage of cactus, lit campfires, magma, pointed dripstone and the stonecutter blade; explosions of landmines and gas barrels.
     *  Blocks that only apply a status effect (cobweb slowness, acid poison, ...) keep their real behavior. */
    static final float CONTACT_DAMAGE=50,EXPLOSION_DAMAGE=120;
    static final double TNT_RADIUS=4;
    static boolean contactDamage(net.minecraft.world.level.block.state.BlockState state){
        var block=state.getBlock();
        return block instanceof CactusBlock||block instanceof MagmaBlock||block instanceof StonecutterBlock||block instanceof PointedDripstoneBlock
                ||block instanceof PuddleBlock puddle&&puddle.acid
                ||block instanceof CampfireBlock&&state.getValue(CampfireBlock.LIT);
    }
    private static DamageSource contactSource(ServerPlayer owner,net.minecraft.world.level.block.state.BlockState state){
        var sources=owner.damageSources();var block=state.getBlock();
        return block instanceof CactusBlock?sources.cactus():block instanceof MagmaBlock?sources.hotFloor():block instanceof CampfireBlock?sources.inFire():block instanceof PointedDripstoneBlock?sources.stalagmite():block instanceof PuddleBlock?sources.magic():sources.generic();
    }
    /** Explosion effect at the disguise; hurts seekers in range only, never hiders. */
    private void explode(PropEntity prop,double radius){
        var level=(ServerLevel)prop.level();
        for(var target:server.getPlayerList().getPlayers())if(target.level()==level&&participant(target.getUUID())&&!hider(target.getUUID())&&target.distanceTo(prop)<=radius)
            target.hurt(level.damageSources().explosion(null,null),EXPLOSION_DAMAGE);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,prop.getX(),prop.getY()+.5,prop.getZ(),1,0,0,0,0);
        level.playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,net.minecraft.sounds.SoundSource.BLOCKS,1,1);
    }
    public void eliminate(ServerPlayer p,ServerPlayer killer){
        var m=members.get(p.getUUID());if(m==null||reviewing)return;
        p.setHealth(p.getMaxHealth());p.clearFire();p.fallDistance=0;
        if(test){p.sendSystemMessage(Component.literal("[테스트] "+(m.team.equals("ALPHA")?"발견됨":"쓰러짐")+" · 체력을 회복하고 계속합니다."));return;}
        p.setCamera(p);
        if(m.team.equals("ALPHA")){
            if(!game.eliminate(m.id,killer==null?null:killer.getUUID()))return;
            // A found gas barrel or TNT goes off where it stood.
            if(m.prop!=null&&(m.prop.item().equals("gungang:gas_barrel")||m.prop.item().equals("minecraft:tnt"))){
                m.prop.setPos(spot(m,p));explode(m.prop,radius(m.prop));
            }
            if(m.prop!=null){m.prop.setPos(spot(m,p));mark(m);}home(p,m);m.fixed=false;p.setInvisible(false);p.setGameMode(GameType.SPECTATOR);p.refreshDimensions();
        }else{
            if(m.waiting>server.getTickCount())return;
            var s=map.bravoSpawn();p.teleportTo(p.serverLevel(),s.x(),s.y(),s.z(),s.yaw(),s.pitch());p.setGameMode(GameType.ADVENTURE);m.waiting=server.getTickCount()+100;equip(p);
        }
        sync();
    }
    private void mark(Member m){m.prop.setGlowingTag(true);m.prop.setCustomName(Component.literal(m.name));m.prop.setCustomNameVisible(true);}
    public void leave(ServerPlayer p){if(test){test(p,null,"");return;}var m=members.get(p.getUUID());if(m==null)return;if(hider(m.id)){game.eliminate(m.id,null);if(m.prop!=null)mark(m);}restore(p,m);}
    private void restore(ServerPlayer p,Member m){rush(p,false);p.setInvisible(m.invisible);p.removeEffect(MobEffects.BLINDNESS);p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(m.health);p.setHealth(Math.min(p.getHealth(),p.getMaxHealth()));}
    public void close(){arrows.forEach(net.minecraft.world.entity.Entity::discard);arrows.clear();if(test){for(var id:List.copyOf(members.keySet())){var p=server.getPlayerList().getPlayer(id);if(p!=null)stop(p);}return;}reviewing=true;PropInteraction.clear();for(var m:members.values()){if(m.prop!=null)m.prop.discard();var p=server.getPlayerList().getPlayer(m.id);if(p!=null){restore(p,m);p.refreshDimensions();}}for(var p:server.getPlayerList().getPlayers())GungangNetwork.send(p,new GungangNetwork.PropHunt(ServerFiles.GSON.toJson(new PropHuntSnapshot(false,"",0,0,List.of()))));}
    public List<MatchSnapshot.Row> rows(List<MatchSnapshot.Row> original){return original.stream().map(r->new MatchSnapshot.Row(r.id(),r.name(),r.team(),game.found(UUID.fromString(r.id())),r.deaths(),r.assists(),r.damage(),game.hidden(UUID.fromString(r.id())))).toList();}
    public void action(ServerPlayer p,String action){
        var m=members.get(p.getUUID());if(m==null||!hider(m.id)||!game.alive(m.id)||reviewing)return;
        int now=server.getTickCount();if(now-m.actions.getOrDefault(action,-100)<2)return;m.actions.put(action,now);
        switch(action){
            case "lock"->{home(p,m);m.fixed=!m.fixed;m.anchor=p.position();m.yaw=p.getYRot();m.pitch=p.getXRot();m.charge=-1;}
            case "next"->cycle(p,m,1);
            case "previous"->cycle(p,m,-1);
            case "home"->home(p,m);
            case "rotate"->{if(!m.fixed&&m.prop!=null){m.prop.rotate();p.refreshDimensions();}}
            case "snap"->{if(!m.fixed)snap(p);}
            case "interact"->{if(m.prop!=null&&m.watching==null&&m.prop.item().equals("minecraft:dispenser"))fire(p,m);else if(m.prop!=null&&m.watching==null&&explosive(m.prop.item()))blast(p,m);else if(m.prop!=null&&m.watching==null){PropInteraction.use(p,m.prop);if(m.prop.healthPack()){m.prop.setPos(spot(m,p));p.displayClientMessage(Component.literal(m.prop.sunk()?"생명력 팩: 바닥 아래로 들어갔습니다 · E로 나오기":"생명력 팩: 바닥 위로 나왔습니다 · E로 들어가기"),true);}p.refreshDimensions();}}
            case "charge"->{if(!m.fixed&&p.onGround())m.charge=server.getTickCount();}
            case "cancelCharge"->m.charge=-1;
            case "horn"->horn(m);
            case "jump"->{if(!m.fixed&&p.onGround()&&m.charge>=0){p.setDeltaMovement(p.getDeltaMovement().x,PropHuntGame.jumpVelocity(server.getTickCount()-m.charge),p.getDeltaMovement().z);p.hurtMarked=true;}m.charge=-1;}
        }
        sync();
    }
    /** G: a goat horn blast from the disguise itself, never the watched player's spot; at most once every 3 seconds. */
    private void horn(Member m){
        int now=server.getTickCount();if(m.prop==null||now<m.horn)return;m.horn=now+60;
        var variants=net.minecraft.sounds.SoundEvents.GOAT_HORN_SOUND_VARIANTS;
        m.prop.level().playSound(null,m.prop.getX(),m.prop.getY()+.5,m.prop.getZ(),variants.get(random.nextInt(variants.size())).value(),net.minecraft.sounds.SoundSource.PLAYERS,4,1);
    }
    static final String ARROW_TAG="gungang_prop_arrow";
    static final float ARROW_DAMAGE=25;
    /** E on a dispenser disguise: an arrow out of its facing side against seekers, once a second while hunting. */
    private void fire(ServerPlayer p,Member m){
        int now=server.getTickCount();
        if(phase()!=PropHuntGame.Phase.HUNTING){p.displayClientMessage(Component.literal("화살은 찾는 시간에만 쏠 수 있습니다."),true);return;}
        if(now<m.shot)return;m.shot=now+20;
        var prop=m.prop;var level=prop.level();var facing=prop.state().getValue(DispenserBlock.FACING);
        var dir=Vec3.atLowerCornerOf(facing.getNormal());var from=new Vec3(prop.getX(),prop.getY()+.5,prop.getZ()).add(dir.scale(.8));
        var arrow=new net.minecraft.world.entity.projectile.Arrow(level,from.x,from.y-.25,from.z);
        arrow.pickup=net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;arrow.addTag(ARROW_TAG);
        arrow.shoot(dir.x,dir.y,dir.z,2.5f,1);level.addFreshEntity(arrow);arrows.add(arrow);arrows.removeIf(net.minecraft.world.entity.Entity::isRemoved);
        level.levelEvent(1002,prop.blockPosition(),0);
    }
    static boolean propArrow(DamageSource source){return source.getDirectEntity()!=null&&source.getDirectEntity().getTags().contains(ARROW_TAG);}
    /** Another player right-clicking a disguise; the owner keeps using E. */
    public boolean useProp(ServerPlayer p,PropEntity prop){
        if(reviewing||!participant(p.getUUID())||frozen(p.getUUID())||hider(p.getUUID())&&!game.alive(p.getUUID())||!game.alive(prop.owner())||p.getUUID().equals(prop.owner()))return false;
        PropInteraction.use(p,prop);
        var owner=server.getPlayerList().getPlayer(prop.owner());if(owner!=null)owner.refreshDimensions();
        return true;
    }
    private void home(ServerPlayer p,Member m){
        if(m.watching==null)return;m.watching=null;p.setCamera(p);
        var level=test?p.serverLevel():server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,PropCatalog.id(map.dimension())));
        p.teleportTo(level,m.anchor.x,m.anchor.y,m.anchor.z,m.yaw,m.pitch);p.setGameMode(GameType.ADVENTURE);
    }
    private void cycle(ServerPlayer p,Member m,int direction){
        if(!m.fixed)return;
        var targets=members.values().stream().map(t->t.id).filter(id->!id.equals(m.id)&&camera(id)!=null).toList();
        if(targets.isEmpty()){home(p,m);return;}
        int current=targets.indexOf(m.watching);
        m.watching=targets.get(Math.floorMod(current+direction,targets.size()));
        p.closeContainer();p.stopUsingItem();p.setGameMode(GameType.SPECTATOR);p.setCamera(camera(m.watching));
    }
    /**
     * What to follow when watching this member: the player, or for a hider who is watching someone else
     * (a spectator then) their disguise, looking where they locked it. Null when they cannot be watched.
     */
    private net.minecraft.world.entity.Entity camera(UUID id){
        var t=members.get(id);var player=server.getPlayerList().getPlayer(id);if(t==null||player==null)return null;
        if(hider(id))return !game.alive(id)?null:t.watching!=null&&t.prop!=null?t.prop:player;
        return player.isSpectator()||!player.isAlive()?null:player;
    }
    /**
     * Nozzles may overlap water, acid and puddles like the real fluid-logged block: the disguise takes the fluid of
     * its cell (a puddle counts as water / acid), so it looks and behaves like a submerged nozzle.
     */
    private static void submerge(PropEntity prop){
        var state=prop.state();if(!(state.getBlock() instanceof NozzleBlock))return;
        var level=prop.level();var cell=BlockPos.containing(prop.getX(),prop.getY()+.5,prop.getZ());var here=level.getBlockState(cell);
        int fluid=here.getBlock() instanceof PuddleBlock puddle?(puddle.acid?3:1):NozzleBlock.fluidId(level.getFluidState(cell).getType());
        if(state.getValue(NozzleBlock.FLUID)!=fluid)prop.state(state.setValue(NozzleBlock.FLUID,fluid));
    }
    static final int BLAST_COOLDOWN=30*20;
    static boolean explosive(String item){return item.equals("minecraft:tnt")||item.equals("gungang:gas_barrel");}
    private double radius(PropEntity prop){return prop.item().equals("minecraft:tnt")?TNT_RADIUS:GimmickConfig.gas.radius();}
    /** E on a TNT or gas barrel disguise: a 120-damage blast against seekers, then 30 s cooldown. The hider stays hidden. */
    private void blast(ServerPlayer p,Member m){
        int now=server.getTickCount();
        if(phase()!=PropHuntGame.Phase.HUNTING){p.displayClientMessage(Component.literal("폭발은 찾는 시간에만 쓸 수 있습니다."),true);return;}
        if(now<m.blast){p.displayClientMessage(Component.literal("폭발 재사용 대기 "+((m.blast-now+19)/20)+"초"),true);return;}
        m.blast=now+BLAST_COOLDOWN;explode(m.prop,radius(m.prop));
        p.displayClientMessage(Component.literal("폭발! 30초 후 다시 사용할 수 있습니다."),true);
    }
    /** Where the disguise stands: the owner (or the locked anchor), one block lower for a sunk health pack. */
    private static Vec3 spot(Member m,ServerPlayer p){var base=m.fixed?m.anchor:p.position();return m.prop!=null&&m.prop.sunk()?base.add(0,-1,0):base;}
    /** A locked disguise faces the owner's locked view, so a camera on it sees what the owner saw. */
    private static void look(Member m){m.prop.setYRot(m.yaw);m.prop.setYHeadRot(m.yaw);m.prop.setYBodyRot(m.yaw);m.prop.setXRot(m.pitch);}
    private void snap(ServerPlayer p){
        Vec3 best=null;double distance=Double.MAX_VALUE;
        for(var pos:BlockPos.betweenClosed(p.blockPosition().offset(-2,-1,-2),p.blockPosition().offset(2,1,2))){
            if(p.level().getBlockState(pos).isAir())continue;
            for(var direction:Direction.values()){
                var cell=pos.relative(direction);var v=Vec3.atBottomCenterOf(cell);double d=v.distanceToSqr(p.position());
                if(d<distance&&d<=4&&p.level().noCollision(p,p.getBoundingBox().move(v.subtract(p.position())))){best=v;distance=d;}
            }
        }
        if(best!=null)p.connection.teleport(best.x,best.y,best.z,p.getYRot(),p.getXRot());
    }
    /** Safe zones are suspended while the match runs for seekers and the disguises they shoot. */
    public boolean armed(net.minecraft.world.entity.Entity entity){
        if(reviewing)return false;
        return entity instanceof PropEntity||entity instanceof ServerPlayer p&&participant(p.getUUID())&&!hider(p.getUUID());
    }
    static boolean npc(net.minecraft.world.entity.Entity entity){var key=net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());return key!=null&&key.getNamespace().equals("customnpcs");}
    /** Custom NPCs on the map stay inert for everyone in the match: no dialogs, trades or scripts from clicks. */
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void npcInteract(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract e){if(npcBlocked(e.getEntity(),e.getTarget())){e.setCanceled(true);e.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);}}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void npcInteractAt(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific e){if(npcBlocked(e.getEntity(),e.getTarget())){e.setCanceled(true);e.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);}}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void npcAttack(net.minecraftforge.event.entity.player.AttackEntityEvent e){if(npcBlocked(e.getEntity(),e.getTarget()))e.setCanceled(true);}
    /** Bullets pass through custom NPCs during prop hunt, on the server and in the client's own bullet simulation. */
    public static boolean bulletPasses(net.minecraft.world.entity.Entity bullet,net.minecraft.world.entity.Entity target){
        if(target==null||!npc(target))return false;
        return bullet.level().isClientSide?SafeZones.clientInMatch.getAsBoolean():MatchManager.propHunt()!=null;
    }
    private static boolean npcBlocked(net.minecraft.world.entity.player.Player player,net.minecraft.world.entity.Entity target){
        if(!npc(target))return false;
        if(player.level().isClientSide)return SafeZones.clientInMatch.getAsBoolean();
        var m=MatchManager.propHunt();return m!=null&&m.participant(player.getUUID());
    }
    public PropEntity prop(UUID id){var m=members.get(id);return m==null||!game.alive(id)||reviewing?null:m.prop;}
    private void sync(){
        // A test session only talks to its own players; everyone else keeps their normal HUD.
        for(var viewer:server.getPlayerList().getPlayers()){
            if(test&&!participant(viewer.getUUID()))continue;
            boolean see=hider(viewer.getUUID())||reviewing||!participant(viewer.getUUID());var rows=new ArrayList<PropHuntSnapshot.Row>();
            for(var m:members.values()){
                boolean found=hider(m.id)&&!game.alive(m.id),visible=see||found;
                rows.add(new PropHuntSnapshot.Row(m.id.toString(),m.name,m.team,visible&&m.prop!=null?m.prop.item():"",visible&&m.prop!=null?Block.getId(m.prop.state()):0,m.prop==null?-1:m.prop.getId(),found,m.fixed,game.hidden(m.id),game.found(m.id)));
            }
            GungangNetwork.send(viewer,new GungangNetwork.PropHunt(ServerFiles.GSON.toJson(new PropHuntSnapshot(true,phase().name(),test?0:(game.remaining()+19)/20,test?0:(game.nextChange()+19)/20,rows,test?0:(game.huntRemaining()+19)/20,!test&&game.finalMinute()))));
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void death(net.minecraftforge.event.entity.living.LivingDeathEvent e){var m=MatchManager.propHunt();if(m!=null&&e.getEntity() instanceof ServerPlayer p&&m.participant(p.getUUID())){e.setCanceled(true);m.eliminate(p,null);}}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void attack(net.minecraftforge.event.entity.living.LivingAttackEvent e){
        var m=MatchManager.propHunt();if(m==null)return;
        if(e.getEntity() instanceof ServerPlayer p&&m.participant(p.getUUID())){
            // A disguise never drowns or takes acid/poison damage, in water, acid or a nozzle spray,
            // nor suffocates: low props keep a raised eye (camera) that may poke into a block above.
            var source=e.getSource();boolean liquid=source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)||source.is(net.minecraft.world.damagesource.DamageTypes.DROWN)||source.is(net.minecraft.world.damagesource.DamageTypes.MAGIC)||source.is(net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC);
            if(liquid&&m.prop(p.getUUID())!=null){e.setCanceled(true);p.removeEffect(MobEffects.POISON);return;}
            if(m.reviewing||m.frozen(p.getUUID())&&!m.hider(p.getUUID())||m.hider(p.getUUID())&&(propArrow(e.getSource())||e.getSource().getEntity()!=null&&e.getSource().getEntity()!=p))e.setCanceled(true);
        }
        if(e.getSource().getEntity() instanceof ServerPlayer p&&m.participant(p.getUUID())&&(m.frozen(p.getUUID())||m.hider(p.getUUID())))e.setCanceled(true);
    }
    @SubscribeEvent public static void arrow(net.minecraftforge.event.entity.living.LivingHurtEvent e){if(MatchManager.propHunt()!=null&&propArrow(e.getSource()))e.setAmount(ARROW_DAMAGE);}
    @SubscribeEvent public static void pickup(net.minecraftforge.event.entity.player.EntityItemPickupEvent e){var m=MatchManager.propHunt();if(m!=null&&m.participant(e.getEntity().getUUID()))e.setCanceled(true);}
}
