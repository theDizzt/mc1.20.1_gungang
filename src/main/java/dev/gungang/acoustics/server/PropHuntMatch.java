package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
    final PropAbilities abilities=new PropAbilities(this);
    int now(){return server.getTickCount();}
    boolean hunting(){return !reviewing&&phase()==PropHuntGame.Phase.HUNTING;}
    boolean hiding(){return !reviewing&&phase()==PropHuntGame.Phase.HIDING;}
    List<ServerPlayer> players(){return members.keySet().stream().map(id->server.getPlayerList().getPlayer(id)).filter(Objects::nonNull).toList();}
    /** The body of a member: the player, or a bot's live entity (null while it has none). */
    LivingEntity body(UUID id){
        var p=server.getPlayerList().getPlayer(id);if(p!=null)return p;
        var m=members.get(id);if(m==null||!m.bot)return null;
        var a=BotManager.agent(id);return a==null||a.bot==null||a.bot.isRemoved()?null:a.bot;
    }
    /** Every member's body: players and bots alike. */
    List<LivingEntity> bodies(){return members.keySet().stream().map(this::body).filter(Objects::nonNull).toList();}
    /** Active seekers, players and bots: every hider gimmick acts on both. */
    List<LivingEntity> seekers(){return bodies().stream().filter(p->!hider(p.getUUID())&&!frozen(p.getUUID())&&p.isAlive()&&!p.isSpectator()).toList();}
    boolean bot(UUID id){var m=members.get(id);return m!=null&&m.bot;}
    String name(UUID id){var m=members.get(id);return m==null?"":m.name;}
    boolean locked(UUID id){var m=members.get(id);return m!=null&&m.fixed;}
    void unlock(LivingEntity p){var m=members.get(p.getUUID());if(m!=null){if(p instanceof ServerPlayer sp)home(sp,m);m.fixed=false;m.charge=-1;}}
    void relocate(LivingEntity p,Vec3 to){unlock(p);teleport(p,to);var prop=prop(p.getUUID());if(prop!=null)prop.setPos(to);}
    /** Moves a body at once: players through their connection, bots directly. */
    static void teleport(LivingEntity p,Vec3 to){
        if(p instanceof ServerPlayer sp){sp.teleportTo(sp.serverLevel(),to.x,to.y,to.z,sp.getYRot(),sp.getXRot());return;}
        if(p instanceof Mob mob)mob.getNavigation().stop();
        p.teleportTo(to.x,to.y,to.z);p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;
    }
    /** An action-bar note for a player; bots read nothing. */
    static void tell(LivingEntity p,String text){say(p,Component.literal(text),true);}
    static void say(LivingEntity p,Component text,boolean overlay){if(p instanceof ServerPlayer sp)sp.displayClientMessage(text,overlay);}
    /** A push that reaches a player's client at once; a bot just takes the motion. */
    static void impulse(LivingEntity p,Vec3 motion){if(p instanceof ServerPlayer sp)PlayerImpulse.apply(sp,motion);else{p.setDeltaMovement(motion);p.hurtMarked=true;p.hasImpulse=true;}}
    Vec3 randomMapPosition(LivingEntity p){
        if(map!=null&&map.previewArea()!=null){var a=map.previewArea();return new Vec3(a.minX()+random.nextDouble()*(a.maxX()-a.minX()+1),a.minY()+random.nextDouble()*(a.maxY()-a.minY()+1),a.minZ()+random.nextDouble()*(a.maxZ()-a.minZ()+1));}
        var a=map==null?null:map.alphaSpawn();var b=map==null?null:map.bravoSpawn();
        double x=a==null?p.getX():a.x(),z=a==null?p.getZ():a.z(),bx=b==null?x:b.x(),bz=b==null?z:b.z();
        return new Vec3(Math.min(x,bx)-24+random.nextDouble()*(Math.abs(x-bx)+48),p.getY()-8+random.nextDouble()*24,Math.min(z,bz)-24+random.nextDouble()*(Math.abs(z-bz)+48));
    }
    private static final class Member {
        final UUID id;final String name,team;final boolean invisible,bot;final double health;
        PropEntity prop;boolean fixed;Vec3 anchor;float yaw,pitch;int waiting,charge=-1,hazard,horn,blast,shot;
        /** Q wall cling: the wall it hangs on, or null. */
        Direction wall;
        UUID watching;
        /** Block choice: server tick the offer closes, the picked offer index (-1: none yet), first disguise or a change. */
        int choiceUntil,picked=-1;boolean initial;
        final Map<String,Integer> actions=new HashMap<>();
        Member(ServerPlayer p,String team){id=p.getUUID();name=p.getGameProfile().getName();this.team=team;invisible=p.isInvisible();health=p.getAttribute(Attributes.MAX_HEALTH).getBaseValue();bot=false;}
        /** An AI bot reserved in the lobby; its body comes and goes with {@link BotAgent}. */
        Member(UUID id,String name,String team){this.id=id;this.name=name;this.team=team;invisible=false;health=100;bot=true;}
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
    private static void rush(LivingEntity p,boolean on){
        var speed=p.getAttribute(Attributes.MOVEMENT_SPEED);if(speed==null)return;boolean has=speed.getModifier(RUSH)!=null;
        // A path-walking bot moves attribute² (see BotAgent.updateSpeed), so its share is √1.35 for the same +35% pace.
        if(on&&!has)speed.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(RUSH,"gungang_prop_rush",p instanceof Mob?Math.sqrt(1.35)-1:.35,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
        else if(!on&&has)speed.removeModifier(RUSH);
    }
    public static Loadouts.Weapon validate(MinecraftServer server,ArenaMap map,Loadouts loadouts){
        var gun=shotgun(loadouts,map.propHunt().shotgun());
        var hiders=new ArrayList<>(server.getPlayerList().getPlayers().stream().filter(p->LobbyManager.team(p.getUUID()).equals("ALPHA")).map(ServerPlayer::getUUID).toList());
        // Lobby bots take a side like players: hiders draw from the same deck, seekers count as seekers.
        hiders.addAll(BotManager.lobbyIds().stream().filter(id->BotManager.team(id).equals("ALPHA")).toList());
        if(server.getPlayerList().getPlayers().stream().noneMatch(p->LobbyManager.team(p.getUUID()).equals("BRAVO"))
                &&BotManager.lobbyIds().stream().noneMatch(id->BotManager.team(id).equals("BRAVO")))throw new IllegalArgumentException("술래가 필요합니다.");
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
        for(var id:BotManager.lobbyIds()){String team=BotManager.team(id);if(Set.of("ALPHA","BRAVO").contains(team))members.put(id,new Member(id,BotManager.name(id),team));}
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
        PropAwn.clear(p.getUUID());
        abilities.remove(p);
        var m=members.remove(p.getUUID());if(m==null)return;
        release(p,m);home(p,m);if(m.prop!=null){PropInteraction.remove(m.prop);m.prop.discard();}
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
    public boolean frozen(UUID id){var m=members.get(id);return m!=null&&!reviewing&&(m.fixed||abilities.stunned(id)||m.waiting>server.getTickCount()||m.team.equals("BRAVO")&&phase()==PropHuntGame.Phase.HIDING);}
    public void equip(ServerPlayer p){
        var m=members.get(p.getUUID());if(m==null)return;
        LobbyManager.clearPlayerInventory(p);LoadoutAttributes.clear(p);SecondaryRuntime.clear(p);
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(m.team.equals("ALPHA")?50:100);p.setHealth(p.getMaxHealth());
        if(m.team.equals("BRAVO")){
            p.getInventory().setItem(0,seekerShotgun());p.getInventory().selected=0;
            p.getInventory().setItem(40,PropAwn.createJetpack(p));
        }else{
            p.setInvisible(true);
            // The first disguise is picked from three blocks (10 s); a test session or an empty deck takes it at once.
            if(m.prop==null&&game.offers(m.id).isEmpty()){
                if(test||!offer(m,true))change(p,m);
            }
        }
        p.inventoryMenu.broadcastChanges();
    }
    /** How long a hider may pick its next block, and how many it sees. */
    static final int CHOICE=10*20,OFFER=3;
    /**
     * Puts blocks on offer to a player hider: three, or fewer when the deck must still last every other hider still
     * waiting for a block (bots draw at random and never get an offer). False when nothing could be offered.
     */
    private boolean offer(Member m,boolean initial){
        if(test||m.bot)return false;
        long waiting=members.values().stream().filter(o->hider(o.id)&&game.alive(o.id)&&game.offers(o.id).isEmpty()&&(!initial||o.prop==null)).count();
        int count=Math.min(OFFER,Math.max(1,game.free()/(int)Math.max(1,waiting)));
        if(game.offer(m.id,count,random).isEmpty())return false;
        m.choiceUntil=server.getTickCount()+CHOICE;m.picked=-1;m.initial=initial;
        var p=server.getPlayerList().getPlayer(m.id);
        if(p!=null)p.sendSystemMessage(Component.literal(initial?"변신할 블록을 숫자 키로 고르세요 (10초, 고르지 않으면 무작위)":"10초 뒤 블록이 바뀝니다 · 숫자 키로 다음 블록을 고르세요 (고르지 않으면 무작위)").withStyle(net.minecraft.ChatFormatting.AQUA));
        return true;
    }
    /** 10 s before a change: every living player hider gets its choice (bots keep drawing at random). */
    private void openChoices(){
        var hiders=members.values().stream().filter(o->hider(o.id)&&game.alive(o.id)).toList();
        int left=hiders.size();
        for(var o:hiders){
            if(!o.bot){
                int count=Math.min(OFFER,Math.max(1,game.free()/Math.max(1,left)));
                if(!game.offer(o.id,count,random).isEmpty()){
                    o.choiceUntil=server.getTickCount()+CHOICE;o.picked=-1;o.initial=false;
                    var p=server.getPlayerList().getPlayer(o.id);
                    if(p!=null)p.sendSystemMessage(Component.literal("10초 뒤 블록이 바뀝니다 · 숫자 키로 다음 블록을 고르세요 (고르지 않으면 무작위)").withStyle(net.minecraft.ChatFormatting.AQUA));
                }
            }
            left--;
        }
    }
    /** A bot body entering the match: a hider becomes its block (50 health, invisible), a seeker keeps 100 health. */
    void botSpawned(BotEntity bot){
        var m=members.get(bot.getUUID());if(m==null)return;
        bot.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND,ItemStack.EMPTY);
        if(m.team.equals("ALPHA")){
            bot.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,ItemStack.EMPTY);
            bot.getAttribute(Attributes.MAX_HEALTH).setBaseValue(50);bot.setHealth(50);
            bot.setInvisible(true);bot.setCustomNameVisible(false);
            if(m.prop==null||m.prop.isRemoved())change(bot,m);else{bot.disguise(m.prop);bot.refreshDimensions();}
        }else{bot.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);bot.setHealth(100);}
    }
    /** The seekers' issued shotgun: infinite dummy ammunition, marked so its pellets count against disguises. */
    ItemStack seekerShotgun(){
        var stack=shotgun.stack();stack.getOrCreateTag().putBoolean("GunGangPropShotgun",true);stack.getOrCreateTag().putInt("DummyAmmo",9999);stack.getOrCreateTag().putInt("MaxDummyAmmo",9999);
        LoadoutSlots.mark(stack,"GunGangIssued",0);return stack;
    }
    /** Loud disguise abilities (music, bells, shrieks, launches): seekers in earshot hear where they came from. */
    private static final Set<String> NOISY=Set.of("minecraft:note_block","minecraft:bell","minecraft:jukebox","minecraft:sculk_sensor","minecraft:sculk_shrieker","minecraft:dispenser",
            "minecraft:end_crystal","minecraft:spawner","minecraft:bubble_column","gungang:shutter","minecraft:brewing_stand");
    private void noise(Member m){if(m.prop!=null&&NOISY.contains(m.prop.item())&&hunting())BotManager.propNoise(m.prop.position(),m.id);}
    private void change(LivingEntity p,Member m){
        // A health pack sunk into the floor comes back up first, so the new block never starts inside the ground.
        boolean sunk=m.prop!=null&&m.prop.sunk();
        if(m.prop!=null){PropInteraction.remove(m.prop);m.prop.discard();}if(p instanceof ServerPlayer sp)sp.closeContainer();
        // The picked offer, a random one of the offer when time ran out, or (bots) the next free block.
        String item=test?items.get(m.id):PropCatalog.variant(game.take(m.id,m.picked,random),random);
        m.picked=-1;m.initial=false;m.choiceUntil=0;
        var level=(ServerLevel)p.level();
        m.prop=PropEntity.TYPE.get().create(level);m.prop.setup(m.id,item,random.nextInt(26));m.prop.setPos(m.fixed?m.anchor:p.position());
        level.addFreshEntity(m.prop);
        if(p instanceof BotEntity bot)bot.disguise(m.prop);
        abilities.initialize(p,m.prop);
        p.refreshDimensions();
        if(m.watching==null)unstick(p);
        if(m.fixed&&m.watching==null)m.anchor=p.position();
        if(sunk&&p instanceof ServerPlayer sp)sp.sendSystemMessage(Component.literal("블록이 바뀌어 생명력 팩 매설이 풀렸습니다."));
        // The new disguise is announced in the client's control guide (right side), not the action bar.
        m.hazard=server.getTickCount()+80;
    }
    public void tick(){
        if(phase()==PropHuntGame.Phase.FINISHED){LobbyManager.finish(game.winner());return;}
        // Keep the real hiding place loaded even while its owner watches a distant player.
        for(var m:members.values())if(m.prop!=null)((ServerLevel)m.prop.level()).getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.POST_TELEPORT,new net.minecraft.world.level.ChunkPos(m.prop.blockPosition()),2,m.prop.getId());
        boolean change=!test&&game.tick();
        if(!test&&!reviewing&&phase()!=PropHuntGame.Phase.REVIEW&&game.nextChange()==CHOICE)openChoices();
        if(phase()==PropHuntGame.Phase.REVIEW&&!reviewing){
            abilities.clear();PropSummons.clear(server);
            reviewing=true;
            for(var m:members.values()){if(m.prop!=null)mark(m);var body=body(m.id);if(body!=null)rush(body,false);if(body instanceof BotEntity bot)BotManager.propReview(bot);var p=server.getPlayerList().getPlayer(m.id);if(p!=null){p.removeEffect(MobEffects.BLINDNESS);p.setCamera(p);p.setGameMode(GameType.SPECTATOR);p.sendSystemMessage(Component.literal(""+game.winner()+" 승리! 30초 동안 숨었던 위치를 둘러보세요."));}}
        }
        boolean rush=!test&&game.finalMinute();
        if(rush&&!rushing)for(var m:members.values()){var p=server.getPlayerList().getPlayer(m.id);if(p!=null)p.sendSystemMessage(Component.literal("마지막 1분 · 블록 교체 없음 · 술래 이동속도 +35%"));}
        rushing=rush;
        if(!reviewing)for(var m:members.values()){
            if(m.bot){botTick(m,change,rush);continue;}
            var p=server.getPlayerList().getPlayer(m.id);if(p==null)continue;
            if(m.team.equals("ALPHA")){
                if(!game.alive(m.id))continue;
                p.setInvisible(true);
                if(change)change(p,m);
                // First disguise: on the pick (see action) or when the 10 s run out.
                else if(m.prop==null&&(game.offers(m.id).isEmpty()||server.getTickCount()>=m.choiceUntil))change(p,m);
                if(m.watching!=null){var camera=camera(m.watching);if(camera==null)home(p,m);else if(p.getCamera()!=camera)p.setCamera(camera);}
                if(m.fixed&&m.watching==null){hold(p,m.anchor,m.yaw,m.pitch);}
                // Off the wall (moved past its edge, or a new disguise that no longer fits there): fall as usual.
                if(m.wall!=null&&m.watching==null){if(PropCling.attached(p.level(),p.getBoundingBox(),m.wall,PropCling.SERVER_REACH)){p.setNoGravity(true);p.fallDistance=0;}else release(p,m);}
                if(m.prop!=null){p.setAirSupply(p.getMaxAirSupply());m.prop.setPos(spot(m,p));if(m.fixed)look(m);submerge(m.prop);hazards(p,m);abilities.tick(p,m.prop);}
            }else{
                rush(p,rush);
                if(phase()==PropHuntGame.Phase.HIDING||m.waiting>server.getTickCount()){
                    var s=map.bravoSpawn();hold(p,new Vec3(s.x(),s.y(),s.z()),s.yaw(),s.pitch());
                    if(phase()==PropHuntGame.Phase.HIDING)p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,30,0,false,false,false));
                    p.stopUsingItem();
                }else p.removeEffect(MobEffects.BLINDNESS);
                // Preserve either hand order, including the offhand macro's temporary swap.
                for(int i=1;i<p.getInventory().getContainerSize();i++){
                    var stack=p.getInventory().getItem(i);
                    if(PropAwn.issued(stack)){
                        if(i!=40){int home=PropAwn.is(stack)||PropAwn.jetpack(stack)?40:0;if(p.getInventory().getItem(home).isEmpty()){p.getInventory().setItem(home,stack);p.getInventory().setItem(i,ItemStack.EMPTY);}}
                    }else p.getInventory().setItem(i,ItemStack.EMPTY);
                }
                PropAwn.tick(p,p.getInventory().getItem(0));PropAwn.tick(p,p.getOffhandItem());
                p.getInventory().selected=0;
            }
        }
        abilities.orphanTick();
        if(server.getTickCount()%4==0)sync();
    }
    /**
     * A bot member's share of the match tick, the server-side half of what a player's client does: the disguise follows
     * the body, seekers wait in their room while hiding or after a death. The bot's own choices live in {@link BotPropHunt}.
     */
    private void botTick(Member m,boolean change,boolean rush){
        // Bots switched off mid-match: a hider that can no longer be found counts as found, so the hunt can end.
        if(BotManager.agent(m.id)==null){if(hider(m.id)&&game.alive(m.id)){game.eliminate(m.id,null);if(m.prop!=null)mark(m);}return;}
        var body=body(m.id);if(!(body instanceof BotEntity bot))return;
        if(m.team.equals("ALPHA")){
            if(!game.alive(m.id))return;
            bot.setInvisible(true);
            if(change)change(bot,m);
            if(m.prop!=null){
                bot.setAirSupply(bot.getMaxAirSupply());
                // A prop whose shape changed (rotation, sugar cane growth, sunk pack) resizes the body with it.
                var size=bot.getDimensions(bot.getPose());if(Math.abs(size.width-bot.getBbWidth())>.01||Math.abs(size.height-bot.getBbHeight())>.01)bot.refreshDimensions();
                m.prop.setPos(spot(m,bot));submerge(m.prop);hazards(bot,m);abilities.tick(bot,m.prop);
            }
            return;
        }
        rush(bot,rush);
        if(phase()==PropHuntGame.Phase.HIDING||m.waiting>server.getTickCount()){
            var s=map.bravoSpawn();
            if(bot.distanceToSqr(s.x(),s.y(),s.z())>.25){teleport(bot,new Vec3(s.x(),s.y(),s.z()));bot.setYRot(s.yaw());}
            bot.getNavigation().stop();bot.setDeltaMovement(Vec3.ZERO);
        }
    }
    /** Whether this member's seeker is still held in the waiting room (hiding, or the 5 s after a death). */
    boolean waiting(UUID id){var m=members.get(id);return m!=null&&!hider(id)&&(phase()==PropHuntGame.Phase.HIDING||m.waiting>server.getTickCount());}
    /** Phase helpers for the bots. */
    boolean finalMinute(){return !test&&game.finalMinute();}
    int huntRemaining(){return test?Integer.MAX_VALUE:game.huntRemaining();}
    int nextChange(){return test?0:game.nextChange();}
    boolean alive(UUID id){return game.alive(id);}
    /** Live hiders' disguises: what seekers can see standing in the map (bots must still tell them from real blocks). */
    List<PropEntity> disguises(){return members.values().stream().filter(m->hider(m.id)&&game.alive(m.id)&&m.prop!=null&&!m.prop.isRemoved()).map(m->m.prop).toList();}
    /** A bigger new disguise must not start inside walls or the floor: step aside, or failing that, up onto free space. */
    private static void unstick(LivingEntity p){
        if(p.level().noCollision(p))return;
        for(double up=0;up<=2;up+=.25)for(double x=-1;x<=1;x+=.5)for(double z=-1;z<=1;z+=.5){
            var to=p.position().add(x,up,z);
            if(p.level().noCollision(p,p.getBoundingBox().move(to.subtract(p.position())))){
                if(p instanceof ServerPlayer sp)sp.connection.teleport(to.x,to.y,to.z,p.getYRot(),p.getXRot());else p.teleportTo(to.x,to.y,to.z);
                return;
            }
        }
    }
    private void hold(ServerPlayer p,Vec3 pos,float yaw,float pitch){
        p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;
        if(p.position().distanceToSqr(pos)>.0001||p.getYRot()!=yaw||p.getXRot()!=pitch)p.connection.teleport(pos.x,pos.y,pos.z,yaw,pitch);
    }
    private void hazards(LivingEntity owner,Member m){
        if(phase()!=PropHuntGame.Phase.HUNTING)return;
        var prop=m.prop;String id=prop.item();
        // Turtle eggs would crack (and place or break) real blocks at the disguise's cell when stepped on.
        if(Set.of("minecraft:nether_portal","minecraft:fire","minecraft:soul_fire","minecraft:torchflower","minecraft:minecart","minecraft:turtle_egg","minecraft:water","minecraft:bubble_column","gungang:hot_spring_water").contains(id))return;
        if(prop.state().getBlock() instanceof NozzleBlock nozzle){
            // Spray particles keep running; only seekers in the column are affected (acid: 50 per second, water: puts out fire).
            nozzle.particles((ServerLevel)owner.level(),prop.blockPosition());
            var column=nozzle.column(prop.blockPosition());boolean hit=false;
            if(nozzle.acid()&&server.getTickCount()<m.hazard)return;
            for(var target:seekers()){
                if(target.level()!=owner.level()||!target.getBoundingBox().intersects(column))continue;
                if(nozzle.acid()){target.hurt(owner.damageSources().magic(),CONTACT_DAMAGE);AcidBlock.poison(owner.level(),target);hit=true;}else target.clearFire();
            }
            if(hit)m.hazard=server.getTickCount()+20;
            return;
        }
        if(server.getTickCount()<m.hazard)return;
        for(var target:seekers()){
            if(target.level()!=owner.level())continue;
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
                if(prop.state().getBlock() instanceof LiquidBlock)target.setSecondsOnFire(15);
            }else{
                // Other blocks keep their real contact behavior (webs, acid, water, etc.).
                prop.state().entityInside(owner.level(),prop.blockPosition(),target);
                prop.state().getBlock().stepOn(owner.level(),prop.blockPosition(),prop.state(),target);
            }
        }
    }
    public boolean hit(PropEntity prop,DamageSource source,float amount){
        if(reviewing||phase()!=PropHuntGame.Phase.HUNTING||!game.alive(prop.owner())||!Float.isFinite(amount)||amount<=0||SpawnGateProtection.blocked(prop,source)||SafeZones.blocked(prop,source))return false;
        if(!(source.getEntity() instanceof LivingEntity shooter)||hider(shooter.getUUID())||!participant(shooter.getUUID())||frozen(shooter.getUUID()))return false;
        var direct=source.getDirectEntity();
        if(direct==null||!direct.getClass().getName().equals("com.tacz.guns.entity.EntityKineticBullet")||!(PropAwn.issued(shooter.getMainHandItem())||PropAwn.issued(shooter.getOffhandItem())))return false;
        var p=body(prop.owner());if(p==null)return false;
        // A cloaked doll (191) cannot be hurt.
        if(abilities.cloaked(prop.owner()))return false;
        // A full volley deals SEEKER_DAMAGE, whatever the gun's own damage: each pellet its share (M1014: 10 pellets, 10 each).
        amount=pelletDamage();
        if(p.getHealth()<=amount+1e-3f)eliminate(p,shooter);else {p.setHealth(p.getHealth()-amount);abilities.struck(p,prop);}
        // A bot hider feels the hit (it is found out and runs); a bot seeker learns that this block is a hider.
        BotManager.propShot(p,shooter,prop,!game.alive(prop.owner()));
        if(prop.item().equals("gungang:gas_barrel")){
            var m=members.get(prop.owner());if(server.getTickCount()>=m.hazard){m.hazard=server.getTickCount()+100;explode(prop,GimmickConfig.gas.radius());}
        }
        return true;
    }
    /** Contact damage of cactus, lit campfires, magma, pointed dripstone and the stonecutter blade; explosions of landmines and gas barrels.
     *  Blocks that only apply a status effect (cobweb slowness, acid poison, ...) keep their real behavior. */
    static final float CONTACT_DAMAGE=50,EXPLOSION_DAMAGE=120,SEEKER_DAMAGE=100;
    private int pellets;
    /** One pellet's share of SEEKER_DAMAGE for the issued shotgun. */
    float pelletDamage(){if(pellets<=0)pellets=pellets(shotgun==null?ItemStack.EMPTY:shotgun.stack());return SEEKER_DAMAGE/pellets;}
    /** Pellets per shot from the gun pack's data ({@code bullet_amount}); 1 when TaCZ cannot say. */
    static int pellets(ItemStack stack){
        try{
            var api=Class.forName("com.tacz.guns.api.TimelessAPI");
            var index=(Optional<?>)api.getMethod("getCommonGunIndex",net.minecraft.resources.ResourceLocation.class).invoke(null,PropCatalog.id(stack.getOrCreateTag().getString("GunId")));
            if(index.isEmpty())return 1;
            var data=index.get().getClass().getMethod("getGunData").invoke(index.get());
            var bullet=data.getClass().getMethod("getBulletData").invoke(data);
            return Math.max(1,(Integer)bullet.getClass().getMethod("getBulletAmount").invoke(bullet));
        }catch(ReflectiveOperationException|RuntimeException e){return 1;}
    }
    static final double TNT_RADIUS=4;
    static boolean contactDamage(net.minecraft.world.level.block.state.BlockState state){
        var block=state.getBlock();
        return block instanceof CactusBlock||block instanceof MagmaBlock||block instanceof StonecutterBlock||block instanceof PointedDripstoneBlock
                ||block instanceof PuddleBlock puddle&&puddle.acid
                ||block instanceof CampfireBlock&&state.getValue(CampfireBlock.LIT)
                ||block instanceof LiquidBlock&&state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA);
    }
    private static DamageSource contactSource(LivingEntity owner,net.minecraft.world.level.block.state.BlockState state){
        var sources=owner.damageSources();var block=state.getBlock();
        return block instanceof CactusBlock?sources.cactus():block instanceof MagmaBlock?sources.hotFloor():block instanceof CampfireBlock?sources.inFire():block instanceof PointedDripstoneBlock?sources.stalagmite():block instanceof PuddleBlock?sources.magic():block instanceof LiquidBlock?sources.lava():sources.generic();
    }
    /** Explosion effect at the disguise; hurts seekers in range only, never hiders. */
    private void explode(PropEntity prop,double radius){blast((ServerLevel)prop.level(),prop.position(),radius,EXPLOSION_DAMAGE,prop.owner());}
    /** A blast that breaks nothing: seekers within {@code radius} of {@code at} take {@code damage}, hiders none. */
    void blast(ServerLevel level,Vec3 at,double radius,float damage,UUID source){
        for(var target:bodies())if(target.level()==level&&!hider(target.getUUID())&&target.position().distanceTo(at)<=radius)
            target.hurt(level.damageSources().explosion(null,null),damage);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,at.x,at.y+.5,at.z,1,0,0,0,0);
        BotManager.propNoise(at,source);
        level.playSound(null,BlockPos.containing(at),net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,net.minecraft.sounds.SoundSource.BLOCKS,1,1);
    }
    public void eliminate(LivingEntity body,LivingEntity killer){
        var m=members.get(body.getUUID());if(m==null||reviewing)return;
        body.setHealth(body.getMaxHealth());body.clearFire();body.fallDistance=0;
        if(test){body.sendSystemMessage(Component.literal("[테스트] "+(m.team.equals("ALPHA")?"발견됨":"쓰러짐")+" · 체력을 회복하고 계속합니다."));return;}
        if(!(body instanceof ServerPlayer p)){eliminateBot(body,m,killer);sync();return;}
        p.setCamera(p);
        if(m.team.equals("ALPHA")){
            abilities.found(p,m.prop);
            if(!game.eliminate(m.id,killer==null?null:killer.getUUID()))return;
            // A found gas barrel or TNT goes off where it stood.
            if(m.prop!=null&&(m.prop.item().equals("gungang:gas_barrel")||m.prop.item().equals("minecraft:tnt"))){
                m.prop.setPos(spot(m,p));explode(m.prop,radius(m.prop));
            }
            if(m.prop!=null){m.prop.setPos(spot(m,p));mark(m);}release(p,m);home(p,m);m.fixed=false;p.setInvisible(false);p.setGameMode(GameType.SPECTATOR);p.refreshDimensions();
        }else{
            if(m.waiting>server.getTickCount())return;
            var s=map.bravoSpawn();p.teleportTo(p.serverLevel(),s.x(),s.y(),s.z(),s.yaw(),s.pitch());p.setGameMode(GameType.ADVENTURE);m.waiting=server.getTickCount()+100;equip(p);
        }
        sync();
    }
    /** A bot found or killed: hiders leave their marker block behind and the body goes; seekers go back to wait 5 s. */
    private void eliminateBot(LivingEntity body,Member m,LivingEntity killer){
        if(m.team.equals("ALPHA")){
            abilities.found(body,m.prop);
            if(!game.eliminate(m.id,killer==null?null:killer.getUUID()))return;
            if(m.prop!=null&&explosive(m.prop.item())){m.prop.setPos(spot(m,body));explode(m.prop,radius(m.prop));}
            if(m.prop!=null){m.prop.setPos(spot(m,body));mark(m);}
            m.fixed=false;abilities.remove(body);
            BotManager.propFound(m.id);
            return;
        }
        if(m.waiting>server.getTickCount())return;
        var s=map.bravoSpawn();teleport(body,new Vec3(s.x(),s.y(),s.z()));body.setYRot(s.yaw());m.waiting=server.getTickCount()+100;
        BotManager.propRespawn(m.id);
    }
    private void mark(Member m){m.prop.markFound();m.prop.setCustomName(Component.literal(m.name));m.prop.setCustomNameVisible(true);}
    public void leave(ServerPlayer p){if(test){test(p,null,"");return;}var m=members.get(p.getUUID());if(m==null)return;if(hider(m.id)){game.eliminate(m.id,null);if(m.prop!=null)mark(m);}restore(p,m);}
    private void restore(ServerPlayer p,Member m){release(p,m);PropAwn.clear(p.getUUID());abilities.remove(p);rush(p,false);p.setInvisible(m.invisible);p.removeEffect(MobEffects.BLINDNESS);p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(m.health);p.setHealth(Math.min(p.getHealth(),p.getMaxHealth()));}
    public void close(){abilities.clear();PropSummons.clear(server);arrows.forEach(net.minecraft.world.entity.Entity::discard);arrows.clear();if(test){for(var id:List.copyOf(members.keySet())){var p=server.getPlayerList().getPlayer(id);if(p!=null)stop(p);}return;}reviewing=true;PropInteraction.clear();for(var m:members.values()){if(m.prop!=null)m.prop.discard();var p=server.getPlayerList().getPlayer(m.id);if(p!=null){restore(p,m);p.refreshDimensions();}}for(var p:server.getPlayerList().getPlayers())GungangNetwork.send(p,new GungangNetwork.PropHunt(ServerFiles.GSON.toJson(new PropHuntSnapshot(false,"",0,0,List.of()))));}
    public List<MatchSnapshot.Row> rows(List<MatchSnapshot.Row> original){return original.stream().map(r->{
        int finds=game.found(UUID.fromString(r.id()));
        return new MatchSnapshot.Row(r.id(),r.name(),r.team(),finds,r.deaths(),r.assists(),r.damage(),game.hidden(UUID.fromString(r.id())),
                finds,r.color(),r.protectionTicks(),r.protectionTotal(),r.skin());
    }).toList();}
    public void action(ServerPlayer p,String action){
        var m=members.get(p.getUUID());if(m==null||!hider(m.id)||!game.alive(m.id)||reviewing)return;
        int now=server.getTickCount();if(now-m.actions.getOrDefault(action,-100)<2)return;m.actions.put(action,now);
        if(action.startsWith("pick")){
            int index;try{index=Integer.parseInt(action.substring(4))-1;}catch(NumberFormatException e){return;}
            if(index<0||index>=game.offers(m.id).size())return;
            m.picked=index;
            // The first disguise starts at once; a change keeps its time and uses the pick then.
            if(m.prop==null)change(p,m);
            return;
        }
        if(m.prop==null)return;
        switch(action){
            case "lock"->{home(p,m);m.fixed=!m.fixed;m.anchor=p.position();m.yaw=p.getYRot();m.pitch=p.getXRot();m.charge=-1;}
            case "next"->cycle(p,m,1);
            case "previous"->cycle(p,m,-1);
            case "home"->home(p,m);
            case "rotate"->{if(m.prop!=null&&m.watching==null&&abilities.rotate(p,m.prop)){}else if(!m.fixed&&m.prop!=null){m.prop.rotate();p.refreshDimensions();}}
            case "snap"->{if(!m.fixed&&m.wall==null)snap(p);}
            case "cling"->cling(p,m);
            case "interact"->{noise(m);if(m.prop!=null&&m.watching==null&&abilities.use(p,m.prop)){m.prop.setPos(spot(m,p));p.refreshDimensions();}else if(m.prop!=null&&m.watching==null&&m.prop.item().equals("minecraft:dispenser"))fire(p,m);else if(m.prop!=null&&m.watching==null&&explosive(m.prop.item()))blast(p,m);else if(m.prop!=null&&m.watching==null){PropInteraction.use(p,m.prop);if(m.prop.sinkable()){m.prop.setPos(spot(m,p));p.displayClientMessage(Component.literal(m.prop.sunk()?"생명력 팩: 바닥 아래로 들어갔습니다 · E로 나오기":"생명력 팩: 바닥 위로 나왔습니다 · E로 들어가기"),true);}p.refreshDimensions();}}
            // On a jump pad the jump key launches at once, as for everyone else; elsewhere it starts the charge.
            case "charge"->{if(!m.fixed&&m.wall==null&&p.onGround()){if(PropSurfacePhysics.pad(p)!=null){p.setDeltaMovement(p.getDeltaMovement().x,PropSurfacePhysics.jump(p,0),p.getDeltaMovement().z);p.hurtMarked=true;m.charge=-1;}else m.charge=server.getTickCount();}}
            case "cancelCharge"->m.charge=-1;
            case "horn"->horn(m);
            // Dolls (184, 191): the attack key held down and let go.
            case "primaryDown","primaryUp"->{if(m.prop!=null&&m.watching==null)abilities.primary(p,m.prop,action.equals("primaryDown"));}
            case "jump"->{if(!m.fixed&&m.wall==null&&p.onGround()&&m.charge>=0){p.setDeltaMovement(p.getDeltaMovement().x,PropSurfacePhysics.jump(p,PropHuntGame.jumpVelocity(server.getTickCount()-m.charge)),p.getDeltaMovement().z);p.hurtMarked=true;}m.charge=-1;}
        }
        sync();
    }
    /** Q: hang on the wall beside the disguise, or let go of it. Not while locked or watching someone else. */
    private void cling(ServerPlayer p,Member m){
        if(m.wall!=null){release(p,m);return;}
        if(m.fixed||m.watching!=null)return;
        var wall=PropCling.find(p.level(),p.getBoundingBox(),p.getYRot());
        if(wall==null){p.displayClientMessage(Component.literal("붙을 벽이 없습니다 · 몸보다 0.6블록 이상 높은 벽 옆에서 사용하세요"),true);return;}
        m.wall=wall;m.charge=-1;p.setNoGravity(true);p.setDeltaMovement(Vec3.ZERO);p.hurtMarked=true;p.fallDistance=0;
    }
    private static void release(ServerPlayer p,Member m){if(m.wall!=null){m.wall=null;p.setNoGravity(false);}}
    /** G: a goat horn blast from the disguise itself, never the watched player's spot; at most once every 3 seconds. */
    private void horn(Member m){
        int now=server.getTickCount();if(m.prop==null||now<m.horn)return;m.horn=now+60;
        BotManager.propNoise(m.prop.position(),m.id);
        var variants=net.minecraft.sounds.SoundEvents.GOAT_HORN_SOUND_VARIANTS;
        m.prop.level().playSound(null,m.prop.getX(),m.prop.getY()+.5,m.prop.getZ(),variants.get(random.nextInt(variants.size())).value(),net.minecraft.sounds.SoundSource.PLAYERS,4,1);
    }
    static final String ARROW_TAG="gungang_prop_arrow";
    static final float ARROW_DAMAGE=25;
    /** E on a dispenser disguise: an arrow out of its facing side against seekers, once a second while hunting. */
    private void fire(LivingEntity p,Member m){
        int now=server.getTickCount();
        if(phase()!=PropHuntGame.Phase.HUNTING){tell(p,"화살은 찾는 시간에만 쏠 수 있습니다.");return;}
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
        var t=members.get(id);if(t==null)return null;
        // A bot is watched through its own body: a seeker's eyes, or a hider's view from inside its block.
        if(t.bot){var body=body(id);return body==null||!body.isAlive()||hider(id)&&!game.alive(id)?null:body;}
        var player=server.getPlayerList().getPlayer(id);if(player==null)return null;
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
    private void blast(LivingEntity p,Member m){
        int now=server.getTickCount();
        if(phase()!=PropHuntGame.Phase.HUNTING){tell(p,"폭발은 찾는 시간에만 쓸 수 있습니다.");return;}
        if(now<m.blast){tell(p,"폭발 재사용 대기 "+((m.blast-now+19)/20)+"초");return;}
        m.blast=now+BLAST_COOLDOWN;explode(m.prop,radius(m.prop));
        tell(p,"폭발! 30초 후 다시 사용할 수 있습니다.");
    }
    // ---- bot hiders: the same controls a player's keys reach (E, R, G), without menus or camera tricks ----
    /** E for a bot hider: the block's ability, arrow, blast or plain toggle. False when nothing could be done now. */
    boolean botUse(BotEntity bot){
        var m=members.get(bot.getUUID());if(m==null||!hider(m.id)||!game.alive(m.id)||reviewing||m.prop==null)return false;
        int now=server.getTickCount();if(now-m.actions.getOrDefault("interact",-100)<2)return false;m.actions.put("interact",now);
        String item=m.prop.item();noise(m);
        if(abilities.use(bot,m.prop)){m.prop.setPos(spot(m,bot));bot.refreshDimensions();}
        else if(item.equals("minecraft:dispenser")){if(now<m.shot||!hunting())return false;fire(bot,m);}
        else if(explosive(item)){if(now<m.blast||!hunting())return false;blast(bot,m);}
        else{PropInteraction.use(bot,m.prop);if(m.prop.sinkable())m.prop.setPos(spot(m,bot));bot.refreshDimensions();}
        sync();return true;
    }
    /** 178: the owner's text for their lectern's book (from the client's editor). */
    public void lecternText(ServerPlayer p,String text){
        var m=members.get(p.getUUID());if(m==null||!hider(m.id)||!game.alive(m.id)||reviewing||m.prop==null||!m.prop.item().equals("minecraft:lectern"))return;
        var clean=new StringBuilder();for(char c:text.toCharArray())if(c=='\n'||c>=' '&&c!='\u00a7')clean.append(c);
        String value=clean.length()>LECTERN_LIMIT?clean.substring(0,LECTERN_LIMIT):clean.toString();
        m.prop.text(value);p.level().playSound(null,m.prop.blockPosition(),net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN,net.minecraft.sounds.SoundSource.BLOCKS,1,1);
    }
    public static final int LECTERN_LIMIT=1024;
    /** R for a bot hider: a 90° turn (a dispenser steps through its six facings). */
    void botRotate(BotEntity bot){
        var m=members.get(bot.getUUID());if(m==null||!hider(m.id)||!game.alive(m.id)||reviewing||m.prop==null)return;
        if(!abilities.rotate(bot,m.prop)){m.prop.rotate();bot.refreshDimensions();}
    }
    /** G for a bot hider: the goat horn taunt, at most every 3 s. */
    void botHorn(BotEntity bot){var m=members.get(bot.getUUID());if(m!=null&&hider(m.id)&&game.alive(m.id)&&!reviewing)horn(m);}
    /** Ticks until this hider's E is ready again (arrows, blasts, abilities, health packs). */
    int botCooldown(UUID id){var m=members.get(id);return m==null?0:abilityRemaining(m);}
    /** Where the disguise stands: the owner (or the locked anchor), one block lower for a sunk health pack. */
    private static Vec3 spot(Member m,LivingEntity p){var base=m.fixed?m.anchor:p.position();return m.prop!=null?base.add(0,-m.prop.sinkDepth(),0):base;}
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
                rows.add(new PropHuntSnapshot.Row(m.id.toString(),m.name,m.team,visible&&m.prop!=null?m.prop.item():"",visible&&m.prop!=null?Block.getId(m.prop.state()):0,m.prop==null?-1:m.prop.getId(),found,m.fixed,game.hidden(m.id),game.found(m.id),visible?abilityRemaining(m):0,m.prop==null?0:PropAbilities.cooldown(m.prop.item()),visible?abilities.active(m.id):0,visible?abilities.auxiliary(m.id):0,visible?abilities.auxiliaryMax(m.id):0,abilities.stunned(m.id),m.wall==null?-1:m.wall.get2DDataValue()));
            }
            // Only the viewer's own block choice is sent, never anyone else's.
            var own=members.get(viewer.getUUID());
            var offers=own==null||reviewing||!hider(own.id)||!game.alive(own.id)?List.<String>of():game.offers(own.id);
            int choice=offers.isEmpty()?0:Math.max(0,(own.choiceUntil-server.getTickCount()+19)/20);
            GungangNetwork.send(viewer,new GungangNetwork.PropHunt(ServerFiles.GSON.toJson(new PropHuntSnapshot(true,phase().name(),test?0:(game.remaining()+19)/20,test?0:(game.nextChange()+19)/20,rows,test?0:(game.huntRemaining()+19)/20,!test&&game.finalMinute(),offers,offers.isEmpty()?-1:own.picked,choice,!offers.isEmpty()&&own.initial))));
        }
    }
    private int abilityRemaining(Member m){
        if(m.prop==null)return 0;String item=m.prop.item();
        return Math.max(0,item.equals("minecraft:dispenser")?m.shot-now():explosive(item)?m.blast-now():m.prop.healthPack()?m.hazard-now():abilities.remaining(m.id));
    }
    /** Players and bots of the match. */
    private static boolean member(PropHuntMatch m,net.minecraft.world.entity.Entity entity){return (entity instanceof ServerPlayer||entity instanceof BotEntity)&&m.participant(entity.getUUID());}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void death(net.minecraftforge.event.entity.living.LivingDeathEvent e){var m=MatchManager.propHunt();if(m!=null&&member(m,e.getEntity())){e.setCanceled(true);m.eliminate(e.getEntity(),null);}}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void attack(net.minecraftforge.event.entity.living.LivingAttackEvent e){
        var m=MatchManager.propHunt();if(m==null)return;
        if(member(m,e.getEntity())){var p=e.getEntity();
            // A disguise never drowns or takes acid/poison damage, in water, acid or a nozzle spray,
            // nor suffocates: low props keep a raised eye (camera) that may poke into a block above.
            var source=e.getSource();boolean liquid=source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)||source.is(net.minecraft.world.damagesource.DamageTypes.DROWN)||source.is(net.minecraft.world.damagesource.DamageTypes.MAGIC)||source.is(net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC);
            if(liquid&&m.prop(p.getUUID())!=null){e.setCanceled(true);p.removeEffect(MobEffects.POISON);return;}
            if(m.abilities.cloaked(p.getUUID())){e.setCanceled(true);return;}
            if(m.reviewing||m.frozen(p.getUUID())&&!m.hider(p.getUUID())||m.hider(p.getUUID())&&(propArrow(e.getSource())||e.getSource().getEntity()!=null&&e.getSource().getEntity()!=p))e.setCanceled(true);
        }
        if(member(m,e.getSource().getEntity())&&(m.frozen(e.getSource().getEntity().getUUID())||m.hider(e.getSource().getEntity().getUUID())))e.setCanceled(true);
    }
    @SubscribeEvent public static void arrow(net.minecraftforge.event.entity.living.LivingHurtEvent e){if(MatchManager.propHunt()!=null&&propArrow(e.getSource()))e.setAmount(ARROW_DAMAGE);}
    @SubscribeEvent public static void pickup(net.minecraftforge.event.entity.player.EntityItemPickupEvent e){var m=MatchManager.propHunt();if(m!=null&&m.participant(e.getEntity().getUUID()))e.setCanceled(true);}
}
