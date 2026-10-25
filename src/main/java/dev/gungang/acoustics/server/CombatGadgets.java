package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class CombatGadgets {
    public static final int SCAN_TICKS=200,SCAN_COOLDOWN=2400,REPAIR_TICKS=1800;
    public record Gauge(String kind,float remaining,float maximum,boolean timed) { }
    public record Hud(List<Gauge> gauges,int mines,int barriers,int maxMines,int maxBarriers,String equipped,int bearTraps,int maxBearTraps,boolean bearTrapArmed) {
        public Hud(List<Gauge> gauges,int mines,int barriers,int maxMines,int maxBarriers,String equipped){this(gauges,mines,barriers,maxMines,maxBarriers,equipped,0,1,false);}
    }
    public record Target(UUID id,double x,double y,double z,float yaw,boolean crouching,int color) { }
    public record ScanView(List<Target> targets) { }
    private record Scan(String team,String dimension,long end) { }
    private static final class Shield {float health=(float)ExtraGadgetConfig.shield.capacity();long repairEnd;}
    private static final Map<UUID,Shield> SHIELDS=new HashMap<>();
    private static final Map<UUID,Scan> SCANS=new HashMap<>();
    private static final Map<UUID,Long> SCAN_READY=new HashMap<>();
    /** Game time of the last hit a shield took (no knockback that tick) and of each player's last damage dealt or taken. */
    private static final Map<UUID,Long> BLOCKED=new HashMap<>(),COMBAT=new HashMap<>();
    private static final Map<UUID,String> LAST_HUD=new HashMap<>(),LAST_SCAN=new HashMap<>();
    private static final UUID SHIELD_SLOW=UUID.fromString("bf8dc095-4b54-495a-8f07-f821e859e41b");
    public static void updateShieldMovement(ServerPlayer p){
        var speed=p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);if(speed==null)return;
        var existing=speed.getModifier(SHIELD_SLOW);double amount=-ExtraGadgetConfig.shield.movementPenaltyPercent()/100;
        boolean raised=holdingShield(p)&&!LobbyManager.respawning(p.getUUID());
        if(existing!=null&&(!raised||existing.getAmount()!=amount))speed.removeModifier(SHIELD_SLOW);
        if(raised&&speed.getModifier(SHIELD_SLOW)==null)speed.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(SHIELD_SLOW,"GunGang shield movement",amount,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
    }
    public static boolean is(ItemStack stack,String kind) {return stack.getItem() instanceof GadgetItem item&&item.kind.equals(kind);}
    private static ItemStack heldShield(Player p) {
        if(is(p.getMainHandItem(),"shield"))return p.getMainHandItem();
        return ItemStack.EMPTY;
    }
    private static Shield shield(ServerPlayer p) {
        var shield=SHIELDS.computeIfAbsent(p.getUUID(),id->new Shield());
        if(shield.health<=0&&p.level().getGameTime()>=shield.repairEnd) {shield.health=(float)ExtraGadgetConfig.shield.capacity();shield.repairEnd=0;}
        return shield;
    }
    public static boolean holdingShield(Player p) {
        if(p==null||!p.isAlive()||p.isSpectator()||heldShield(p).isEmpty())return false;
        if(p instanceof ServerPlayer server)return shield(server).health>0;
        var tag=heldShield(p).getTag();return tag==null||!tag.contains("GunGangShieldHealth")||tag.getFloat("GunGangShieldHealth")>0;
    }
    public static boolean use(ServerPlayer p,ItemStack stack,String kind) {
        if(kind.equals("shield")) {syncShield(p);return true;}
        if(!kind.equals("infrared_scanner"))return false;
        long now=p.level().getGameTime();
        if(now<SCAN_READY.getOrDefault(p.getUUID(),0L))return true;
        if(p.getTeam()==null)return true;
        SCANS.put(p.getUUID(),new Scan(p.getTeam().getName(),p.level().dimension().location().toString(),now+ExtraGadgetConfig.ticks(ExtraGadgetConfig.scanner.durationSeconds())));
        SCAN_READY.put(p.getUUID(),now+ExtraGadgetConfig.ticks(ExtraGadgetConfig.scanner.cooldownSeconds()));
        var tag=stack.getOrCreateTag();tag.putInt("GunGangCooldownTicks",ExtraGadgetConfig.ticks(ExtraGadgetConfig.scanner.cooldownSeconds()));tag.putLong("GunGangCooldownEnd",now+ExtraGadgetConfig.ticks(ExtraGadgetConfig.scanner.cooldownSeconds()));
        SecondaryRuntime.commit(p,stack);p.inventoryMenu.broadcastChanges();return true;
    }
    public static void configure(Player p,ItemStack stack) {
        if(!(p instanceof ServerPlayer server))return;
        if(is(stack,"shield"))writeShield(server,stack);
        if(is(stack,"infrared_scanner")) {
            var tag=stack.getOrCreateTag();tag.putInt("GunGangCooldownTicks",ExtraGadgetConfig.ticks(ExtraGadgetConfig.scanner.cooldownSeconds()));
            tag.putLong("GunGangCooldownEnd",SCAN_READY.getOrDefault(p.getUUID(),0L));
        }
    }
    private static void writeShield(ServerPlayer p,ItemStack stack) {
        var shield=shield(p);var tag=stack.getOrCreateTag();tag.putFloat("GunGangShieldHealth",shield.health);
        tag.putInt("GunGangCooldownTicks",ExtraGadgetConfig.ticks(ExtraGadgetConfig.shield.repairSeconds()));tag.putLong("GunGangCooldownEnd",shield.repairEnd);
    }
    private static void syncShield(ServerPlayer p) {
        boolean found=false;
        for(int i=0;i<p.getInventory().getContainerSize();i++)if(is(p.getInventory().getItem(i),"shield")) {writeShield(p,p.getInventory().getItem(i));found=true;}
        if(found)p.inventoryMenu.broadcastChanges();
    }
    @SubscribeEvent(priority=EventPriority.HIGH) public static void hurt(LivingHurtEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer p)||!holdingShield(p)||event.getAmount()<=0)return;
        // Raised shields soften melee from any side; a jetpack ram uses the same damage type but is not melee.
        var type=event.getSource();
        if((type.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)||type.is(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK)||type.is(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK_NO_AGGRO))
                &&!(type.getEntity() instanceof Player attacker&&SurvivalGadgets.jetting(attacker)))
            event.setAmount((float)(event.getAmount()*(1-ExtraGadgetConfig.shield.meleeReductionPercent()/100)));
        if(event.getAmount()<=0) {event.setCanceled(true);return;}
        Vec3 source=event.getSource().getSourcePosition();if(source==null)return;
        Vec3 delta=source.subtract(p.position());
        if(event.getSource().getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile
                &&projectile.getDeltaMovement().horizontalDistanceSqr()>1e-8)delta=projectile.getDeltaMovement().scale(-1);
        else if(delta.horizontalDistanceSqr()<1e-8&&event.getSource().getEntity()!=null)delta=event.getSource().getEntity().position().subtract(p.position());
        var look=p.getLookAngle();if(!ShieldRules.inFront(look.x,look.z,delta.x,delta.z))return;
        var shield=shield(p);var hit=ShieldRules.absorb(shield.health,event.getAmount());shield.health=hit.durability();
        BLOCKED.put(p.getUUID(),p.level().getGameTime());
        if(shield.health==0)shield.repairEnd=p.level().getGameTime()+ExtraGadgetConfig.ticks(ExtraGadgetConfig.shield.repairSeconds());
        event.setAmount(hit.damage());if(hit.damage()==0)event.setCanceled(true);
        p.level().playSound(null,p.blockPosition(),shield.health==0?net.minecraft.sounds.SoundEvents.SHIELD_BREAK:net.minecraft.sounds.SoundEvents.SHIELD_BLOCK,net.minecraft.sounds.SoundSource.PLAYERS,1,1);
        syncShield(p);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void combat(LivingHurtEvent event) {
        if(event.getAmount()<=0)return;
        if(event.getEntity() instanceof ServerPlayer victim)COMBAT.put(victim.getUUID(),victim.level().getGameTime());
        if(event.getSource().getEntity() instanceof ServerPlayer attacker&&attacker!=event.getEntity())COMBAT.put(attacker.getUUID(),attacker.level().getGameTime());
    }
    public static boolean shieldBlocked(ServerPlayer p) {return BLOCKED.getOrDefault(p.getUUID(),Long.MIN_VALUE)==p.level().getGameTime();}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void knockback(net.minecraftforge.event.entity.living.LivingKnockBackEvent event) {
        if(event.getEntity() instanceof ServerPlayer p&&shieldBlocked(p))event.setCanceled(true);
    }
    private static void regenerate(ServerPlayer p) {
        var config=ExtraGadgetConfig.shield;var shield=SHIELDS.get(p.getUUID());
        if(!config.outOfCombatRegen()||shield==null||shield.health<=0||shield.health>=config.capacity())return;
        long now=p.level().getGameTime();
        if(now-COMBAT.getOrDefault(p.getUUID(),Long.MIN_VALUE/2)<ExtraGadgetConfig.ticks(config.regenDelaySeconds()))return;
        shield.health=(float)Math.min(config.capacity(),shield.health+config.regenPerSecond()/20);
    }
    @SubscribeEvent public static void attack(AttackEntityEvent event) {if(holdingShield(event.getEntity())||SurvivalGadgets.jetting(event.getEntity()))event.setCanceled(true);}
    @SubscribeEvent public static void projectile(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if(!event.getLevel().isClientSide&&event.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile
                &&projectile.getOwner() instanceof Player owner&&(holdingShield(owner)||SurvivalGadgets.jetting(owner)))event.setCanceled(true);
    }
    public static boolean enemies(Player viewer,Player target) {
        return !FreeForAllMatch.blocked(target)&&viewer!=target&&target.isAlive()&&!target.isSpectator()&&viewer.getTeam()!=null&&target.getTeam()!=null&&!viewer.getTeam().isAlliedTo(target.getTeam());
    }
    public static ScanView view(ServerPlayer viewer) {
        var view=view(viewer,viewer.serverLevel().players());
        // Bots are enemies like any other: a team scan shows them too.
        if(viewer.getTeam()==null||!scanActive(viewer))return view;
        var targets=new ArrayList<>(view.targets());
        for(var bot:BotManager.entities()) {
            if(bot.level()!=viewer.level()||bot.getTeam()==null||viewer.getTeam().isAlliedTo(bot.getTeam()))continue;
            String name=bot.getTeam().getName();Integer fallback=bot.getTeam().getColor().getColor();
            int color=name.equals("Red")?LobbyManager.options().color("ALPHA"):name.equals("Blue")?LobbyManager.options().color("BRAVO"):fallback==null?0xFFFFFFFF:0xFF000000|fallback;
            targets.add(new Target(bot.getUUID(),bot.getX(),bot.getY(),bot.getZ(),bot.getYRot(),bot.isCrouching(),color));
        }
        return new ScanView(targets);
    }
    private static boolean scanActive(ServerPlayer viewer) {
        long now=viewer.level().getGameTime();String team=viewer.getTeam().getName();String dimension=viewer.level().dimension().location().toString();
        return SCANS.values().stream().anyMatch(scan->scan.end()>now&&scan.team().equals(team)&&scan.dimension().equals(dimension));
    }
    static ScanView view(ServerPlayer viewer,List<ServerPlayer> candidates) {
        long now=viewer.level().getGameTime();String team=viewer.getTeam()==null?"":viewer.getTeam().getName();
        String dimension=viewer.level().dimension().location().toString();
        boolean active=!team.isEmpty()&&SCANS.values().stream().anyMatch(scan->scan.end()>now&&scan.team().equals(team)&&scan.dimension().equals(dimension));
        if(!active)return new ScanView(List.of());
        return new ScanView(candidates.stream().filter(target->target.level()==viewer.level()&&enemies(viewer,target)).map(target->{
            String name=target.getTeam().getName();Integer fallback=target.getTeam().getColor().getColor();
            int color=name.equals("Red")?LobbyManager.options().color("ALPHA"):name.equals("Blue")?LobbyManager.options().color("BRAVO"):fallback==null?0xFFFFFFFF:0xFF000000|fallback;
            return new Target(target.getUUID(),target.getX(),target.getY(),target.getZ(),target.getYRot(),target.isCrouching(),color);
        }).toList());
    }
    public static Hud hud(ServerPlayer p) {
        var gauges=new ArrayList<Gauge>();long now=p.level().getGameTime();
        var turret=Turrets.owned(p.getUUID());if(turret!=null)gauges.add(new Gauge("turret_ammo",turret.ammo(),turret.capacity(),false));
        if(MatchManager.bomb()!=null){var gauge=MatchManager.bomb().gauge(p);if(gauge!=null)gauges.add(gauge);}
        long jet=SurvivalGadgets.jetRemaining(p);if(jet>0)gauges.add(new Gauge("jetpack",jet,ExtraGadgetConfig.ticks(ExtraGadgetConfig.jet.durationSeconds()),true));
        if(holdingShield(p))gauges.add(new Gauge("shield",shield(p).health,(float)ExtraGadgetConfig.shield.capacity(),false));
        var scan=SCANS.get(p.getUUID());if(scan!=null&&scan.end()>now)gauges.add(new Gauge("infrared_scanner",scan.end()-now,ExtraGadgetConfig.ticks(ExtraGadgetConfig.scanner.durationSeconds()),true));
        var root=BearTraps.rootGauge(p);if(root!=null)gauges.add(root);
        if(p.isUsingItem()&&is(p.getUseItem(),"bear_trap")&&BearTraps.recallable(p)) {
            int recall=ExtraGadgetConfig.ticks(GadgetConfig.bearTrap.recallSeconds());
            gauges.add(new Gauge("bear_trap_recall",Math.max(0,recall-p.getTicksUsingItem()),recall,true));
        }
        long boost=GadgetManager.boostRemaining(p);if(boost>0)gauges.add(new Gauge("adrenaline",boost,(float)(GadgetConfig.adrenaline.durationSeconds()*20),true));
        var ledger=DeployableLedger.get(p.server);String equipped="";
        for(int i=0;i<p.getInventory().getContainerSize();i++) {
            var stack=p.getInventory().getItem(i);
            if(stack.getItem() instanceof GadgetItem item&&(item.kind.equals("landmine")||item.kind.equals("barrier")||item.kind.equals("bear_trap"))) {equipped=item.kind;break;}
        }
        return new Hud(gauges,(int)ledger.count(p.getUUID(),false),(int)ledger.count(p.getUUID(),true),GadgetConfig.mine.maxPlaced(),GadgetConfig.barrier.maxPlaced(),equipped,
                BearTraps.count(p.getUUID()),GadgetConfig.bearTrap.maxPlaced(),BearTraps.recallable(p));
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer p))return;
        updateShieldMovement(p);regenerate(p);if(p.tickCount%4!=0)return;
        SCANS.entrySet().removeIf(entry->entry.getValue().end()<=p.level().getGameTime());
        if(!p.isAlive()||p.isSpectator()||LobbyManager.respawning(p.getUUID()))SCANS.remove(p.getUUID());
        syncShield(p);
        String hud=ServerFiles.GSON.toJson(hud(p)),scan=ServerFiles.GSON.toJson(view(p));
        if(!hud.equals(LAST_HUD.put(p.getUUID(),hud)))GungangNetwork.send(p,new GungangNetwork.Utility(hud));
        if(!scan.equals(LAST_SCAN.put(p.getUUID(),scan)))GungangNetwork.send(p,new GungangNetwork.Scan(scan));
    }
    /** Team scan started by a non-player (the bot): its teammates see enemies exactly as with a player's scanner. */
    static void scan(net.minecraft.world.entity.LivingEntity source,long end) {
        if(source.getTeam()!=null)SCANS.put(source.getUUID(),new Scan(source.getTeam().getName(),source.level().dimension().location().toString(),end));
    }
    /** Any scan by the viewer's team (a teammate's scanner or the viewer's own) is running in its dimension. */
    static boolean teamScanning(net.minecraft.world.entity.LivingEntity viewer) {
        if(viewer.getTeam()==null)return false;
        long now=viewer.level().getGameTime();String team=viewer.getTeam().getName();String dimension=viewer.level().dimension().location().toString();
        return SCANS.values().stream().anyMatch(scan->scan.end()>now&&scan.team().equals(team)&&scan.dimension().equals(dimension));
    }
    static boolean scanning(net.minecraft.world.entity.LivingEntity source) {var scan=SCANS.get(source.getUUID());return scan!=null&&scan.end()>source.level().getGameTime();}
    static void resetScan(ServerPlayer p) {SCAN_READY.remove(p.getUUID());}
    public static void clear(ServerPlayer p) {p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).removeModifier(SHIELD_SLOW);SHIELDS.remove(p.getUUID());BLOCKED.remove(p.getUUID());COMBAT.remove(p.getUUID());SCANS.remove(p.getUUID());SCAN_READY.remove(p.getUUID());LAST_HUD.remove(p.getUUID());LAST_SCAN.remove(p.getUUID());}
    public static void reset(net.minecraft.server.MinecraftServer server) {
        SurvivalGadgets.reset(server);
        SHIELDS.clear();BLOCKED.clear();COMBAT.clear();SCANS.clear();SCAN_READY.clear();LAST_HUD.clear();LAST_SCAN.clear();
        for(var p:server.getPlayerList().getPlayers()) {
            p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).removeModifier(SHIELD_SLOW);
            GungangNetwork.send(p,new GungangNetwork.Scan(ServerFiles.GSON.toJson(new ScanView(List.of()))));
            GungangNetwork.send(p,new GungangNetwork.Utility(ServerFiles.GSON.toJson(new Hud(List.of(),0,0,GadgetConfig.mine.maxPlaced(),GadgetConfig.barrier.maxPlaced(),""))));
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {if(event.getEntity() instanceof ServerPlayer p)clear(p);}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {SHIELDS.clear();BLOCKED.clear();COMBAT.clear();SCANS.clear();SCAN_READY.clear();LAST_HUD.clear();LAST_SCAN.clear();}
    private CombatGadgets() { }
}
