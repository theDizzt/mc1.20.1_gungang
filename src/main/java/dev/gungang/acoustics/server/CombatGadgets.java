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
    public record Hud(List<Gauge> gauges,int mines,int barriers,int maxMines,String equipped) { }
    public record Target(UUID id,double x,double y,double z,float yaw,boolean crouching,int color) { }
    public record ScanView(List<Target> targets) { }
    private record Scan(String team,String dimension,long end) { }
    private static final class Shield {float health=ShieldRules.CAPACITY;long repairEnd;}
    private static final Map<UUID,Shield> SHIELDS=new HashMap<>();
    private static final Map<UUID,Scan> SCANS=new HashMap<>();
    private static final Map<UUID,Long> SCAN_READY=new HashMap<>();
    private static final Map<UUID,String> LAST_HUD=new HashMap<>(),LAST_SCAN=new HashMap<>();
    public static boolean is(ItemStack stack,String kind) {return stack.getItem() instanceof GadgetItem item&&item.kind.equals(kind);}
    private static ItemStack heldShield(Player p) {
        if(is(p.getMainHandItem(),"shield"))return p.getMainHandItem();
        return is(p.getOffhandItem(),"shield")?p.getOffhandItem():ItemStack.EMPTY;
    }
    private static Shield shield(ServerPlayer p) {
        var shield=SHIELDS.computeIfAbsent(p.getUUID(),id->new Shield());
        if(shield.health<=0&&p.level().getGameTime()>=shield.repairEnd) {shield.health=ShieldRules.CAPACITY;shield.repairEnd=0;}
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
        SCANS.put(p.getUUID(),new Scan(p.getTeam().getName(),p.level().dimension().location().toString(),now+SCAN_TICKS));
        SCAN_READY.put(p.getUUID(),now+SCAN_COOLDOWN);
        var tag=stack.getOrCreateTag();tag.putInt("GunGangCooldownTicks",SCAN_COOLDOWN);tag.putLong("GunGangCooldownEnd",now+SCAN_COOLDOWN);
        SecondaryRuntime.commit(p,stack);p.inventoryMenu.broadcastChanges();return true;
    }
    public static void configure(Player p,ItemStack stack) {
        if(!(p instanceof ServerPlayer server))return;
        if(is(stack,"shield"))writeShield(server,stack);
        if(is(stack,"infrared_scanner")) {
            var tag=stack.getOrCreateTag();tag.putInt("GunGangCooldownTicks",SCAN_COOLDOWN);
            tag.putLong("GunGangCooldownEnd",SCAN_READY.getOrDefault(p.getUUID(),0L));
        }
    }
    private static void writeShield(ServerPlayer p,ItemStack stack) {
        var shield=shield(p);var tag=stack.getOrCreateTag();tag.putFloat("GunGangShieldHealth",shield.health);
        tag.putInt("GunGangCooldownTicks",REPAIR_TICKS);tag.putLong("GunGangCooldownEnd",shield.repairEnd);
    }
    private static void syncShield(ServerPlayer p) {
        boolean found=false;
        for(int i=0;i<p.getInventory().getContainerSize();i++)if(is(p.getInventory().getItem(i),"shield")) {writeShield(p,p.getInventory().getItem(i));found=true;}
        if(found)p.inventoryMenu.broadcastChanges();
    }
    @SubscribeEvent(priority=EventPriority.HIGH) public static void hurt(LivingHurtEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer p)||!holdingShield(p)||event.getAmount()<=0)return;
        Vec3 source=event.getSource().getSourcePosition();if(source==null)return;
        Vec3 delta=source.subtract(p.position());
        if(event.getSource().getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile
                &&projectile.getDeltaMovement().horizontalDistanceSqr()>1e-8)delta=projectile.getDeltaMovement().scale(-1);
        else if(delta.horizontalDistanceSqr()<1e-8&&event.getSource().getEntity()!=null)delta=event.getSource().getEntity().position().subtract(p.position());
        var look=p.getLookAngle();if(!ShieldRules.inFront(look.x,look.z,delta.x,delta.z))return;
        var shield=shield(p);var hit=ShieldRules.absorb(shield.health,event.getAmount());shield.health=hit.durability();
        if(shield.health==0)shield.repairEnd=p.level().getGameTime()+REPAIR_TICKS;
        event.setAmount(hit.damage());if(hit.damage()==0)event.setCanceled(true);
        p.level().playSound(null,p.blockPosition(),shield.health==0?net.minecraft.sounds.SoundEvents.SHIELD_BREAK:net.minecraft.sounds.SoundEvents.SHIELD_BLOCK,net.minecraft.sounds.SoundSource.PLAYERS,1,1);
        syncShield(p);
    }
    @SubscribeEvent public static void attack(AttackEntityEvent event) {if(holdingShield(event.getEntity())||SurvivalGadgets.jetting(event.getEntity()))event.setCanceled(true);}
    @SubscribeEvent public static void projectile(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if(!event.getLevel().isClientSide&&event.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile
                &&projectile.getOwner() instanceof Player owner&&(holdingShield(owner)||SurvivalGadgets.jetting(owner)))event.setCanceled(true);
    }
    public static boolean enemies(Player viewer,Player target) {
        return viewer!=target&&target.isAlive()&&!target.isSpectator()&&viewer.getTeam()!=null&&target.getTeam()!=null&&!viewer.getTeam().isAlliedTo(target.getTeam());
    }
    public static ScanView view(ServerPlayer viewer) {
        return view(viewer,viewer.serverLevel().players());
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
        if(holdingShield(p))gauges.add(new Gauge("shield",shield(p).health,ShieldRules.CAPACITY,false));
        var scan=SCANS.get(p.getUUID());if(scan!=null&&scan.end()>now)gauges.add(new Gauge("infrared_scanner",scan.end()-now,SCAN_TICKS,true));
        long boost=GadgetManager.boostRemaining(p);if(boost>0)gauges.add(new Gauge("adrenaline",boost,(float)(GadgetConfig.adrenaline.durationSeconds()*20),true));
        var ledger=DeployableLedger.get(p.server);String equipped="";
        for(int i=0;i<p.getInventory().getContainerSize();i++) {
            var stack=p.getInventory().getItem(i);
            if(stack.getItem() instanceof GadgetItem item&&(item.kind.equals("landmine")||item.kind.equals("barrier"))) {equipped=item.kind;break;}
        }
        return new Hud(gauges,(int)ledger.count(p.getUUID(),false),(int)ledger.count(p.getUUID(),true),Math.min(3,GadgetConfig.mine.maxPlaced()),equipped);
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer p)||p.tickCount%4!=0)return;
        SCANS.entrySet().removeIf(entry->entry.getValue().end()<=p.level().getGameTime());
        if(!p.isAlive()||p.isSpectator()||LobbyManager.respawning(p.getUUID()))SCANS.remove(p.getUUID());
        syncShield(p);
        String hud=ServerFiles.GSON.toJson(hud(p)),scan=ServerFiles.GSON.toJson(view(p));
        if(!hud.equals(LAST_HUD.put(p.getUUID(),hud)))GungangNetwork.send(p,new GungangNetwork.Utility(hud));
        if(!scan.equals(LAST_SCAN.put(p.getUUID(),scan)))GungangNetwork.send(p,new GungangNetwork.Scan(scan));
    }
    public static void clear(ServerPlayer p) {SHIELDS.remove(p.getUUID());SCANS.remove(p.getUUID());SCAN_READY.remove(p.getUUID());LAST_HUD.remove(p.getUUID());LAST_SCAN.remove(p.getUUID());}
    public static void reset(net.minecraft.server.MinecraftServer server) {
        SurvivalGadgets.reset(server);
        SHIELDS.clear();SCANS.clear();SCAN_READY.clear();LAST_HUD.clear();LAST_SCAN.clear();
        for(var p:server.getPlayerList().getPlayers()) {
            GungangNetwork.send(p,new GungangNetwork.Scan(ServerFiles.GSON.toJson(new ScanView(List.of()))));
            GungangNetwork.send(p,new GungangNetwork.Utility(ServerFiles.GSON.toJson(new Hud(List.of(),0,0,Math.min(3,GadgetConfig.mine.maxPlaced()),""))));
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {if(event.getEntity() instanceof ServerPlayer p)clear(p);}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {SHIELDS.clear();SCANS.clear();SCAN_READY.clear();LAST_HUD.clear();LAST_SCAN.clear();}
    private CombatGadgets() { }
}
