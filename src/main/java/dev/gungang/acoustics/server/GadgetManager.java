package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class GadgetManager {
    public static int clearAll(net.minecraft.server.MinecraftServer server) {
        DeployableLedger.get(server).clearAll();
        int count=0;
        for(var level:server.getAllLevels()) {
            var attacks=new ArrayList<net.minecraft.world.entity.Entity>();
            for(var entity:level.getAllEntities())if(entity instanceof OrdnanceEntity||entity instanceof MolotovEntity)attacks.add(entity);
            attacks.forEach(net.minecraft.world.entity.Entity::discard);
            var entities=new ArrayList<DeployableEntity>();
            for(var entity:level.getAllEntities())if(entity instanceof DeployableEntity placed)entities.add(placed);
            for(var entity:entities) {entity.discard();count++;}
        }
        PLACED.clear();
        for(var player:server.getPlayerList().getPlayers())cancel(player);
        PREVIEW.clear();return count;
    }
    private record Healing(int remaining,float perTick) { }
    private record Boost(long end) { }
    private record Preview(String kind,int slot) { }
    private static final Map<UUID,Preview> PREVIEW=new HashMap<>();
    private static final Map<UUID,List<Healing>> HEALING=new HashMap<>();
    private static final Map<UUID,Boost> BOOST=new HashMap<>();
    private static final Map<UUID,DeployableEntity> PLACED=new HashMap<>();
    public static long boostRemaining(ServerPlayer p) {var boost=BOOST.get(p.getUUID());return boost==null?0:Math.max(0,boost.end()-p.level().getGameTime());}
    private static final UUID SPEED=UUID.fromString("e63b9b9f-7490-4c48-aeeb-fc89f52f1bce"),ATTACK=UUID.fromString("4057aaec-a6fa-4694-b849-a5f16f9a065d");
    public static void track(DeployableEntity entity) {
        if(!DeployableLedger.get(entity.level().getServer()).contains(entity.getUUID())) {entity.discard();return;}
        PLACED.put(entity.getUUID(),entity);
    }
    public static void use(ServerPlayer p,ItemStack stack,String kind) {
        if(SurvivalGadgets.jetting(p))return;
        if((kind.equals("landmine")||kind.equals("molotov")||kind.equals("orbital_strike"))&&SafeZones.contains(p))return;
        if(!p.isAlive()||p.isSpectator()||LobbyManager.respawning(p.getUUID())||!SecondaryRuntime.ready(p,stack))return;
        if(kind.equals("jetpack")) {SurvivalGadgets.startJet(p,stack);return;}
        if(CombatGadgets.use(p,stack,kind))return;
        if(kind.equals("landmine")||kind.equals("barrier")||kind.equals("orbital_strike")) {
            PREVIEW.put(p.getUUID(),new Preview(kind,p.getInventory().selected));GungangNetwork.send(p,new GungangNetwork.Gadget(kind));return;
        }
        if(kind.equals("molotov")) {
            var projectile=GadgetItems.MOLOTOV_PROJECTILE.get().create(p.serverLevel());if(projectile==null)return;
            projectile.setOwner(p);projectile.setPos(p.getX(),p.getEyeY()-.1,p.getZ());
            projectile.shootFromRotation(p,p.getXRot(),p.getYRot(),-10,1.1f,1);
            if(!p.serverLevel().addFreshEntity(projectile))return;
            p.level().playSound(null,p.blockPosition(),net.minecraft.sounds.SoundEvents.SPLASH_POTION_THROW,net.minecraft.sounds.SoundSource.PLAYERS,.7f,1);
        } else if(kind.equals("recovery_potion")) {
            var c=GadgetConfig.recovery;int ticks=Math.max(1,(int)Math.round(c.durationSeconds()*20));
            HEALING.computeIfAbsent(p.getUUID(),id->new ArrayList<>()).add(new Healing(ticks,(float)c.heal()/ticks));
        } else if(kind.equals("adrenaline")) {
            var c=GadgetConfig.adrenaline;float cost=(float)(p.getMaxHealth()*c.healthCostPercent()/100);
            if(p.getHealth()<=cost) {p.displayClientMessage(Component.literal("전투자극제를 사용할 체력이 부족합니다."),true);return;}
            p.setHealth(p.getHealth()-cost);
            modifier(p,Attributes.MOVEMENT_SPEED,SPEED,c.movementBonusPercent()/100);
            modifier(p,Attributes.ATTACK_SPEED,ATTACK,c.attackBonusPercent()/100);
            BOOST.put(p.getUUID(),new Boost(p.level().getGameTime()+Math.max(1,(int)Math.round(c.durationSeconds()*20))));
        } else return;
        SecondaryRuntime.commit(p,stack);
    }
    private static void modifier(Player p,Attribute type,UUID id,double amount) {
        var attr=p.getAttribute(type);attr.removeModifier(id);attr.addTransientModifier(new AttributeModifier(id,"GunGang adrenaline",amount,AttributeModifier.Operation.MULTIPLY_TOTAL));
    }
    public static BlockPos target(Player p,double range) {
        var hit=p.pick(range,0,false);
        if(!(hit instanceof BlockHitResult block)||hit.getType()!=HitResult.Type.BLOCK||block.getDirection()!=Direction.UP)return null;
        return block.getBlockPos().above();
    }
    public static AABB bounds(BlockPos pos,boolean wall,boolean east) {
        double x=pos.getX()+.5,z=pos.getZ()+.5,y=pos.getY();
        return wall?new AABB(x-(east?.18:1.5),y,z-(east?1.5:.18),x+(east?.18:1.5),y+3,z+(east?1.5:.18)):new AABB(x-.35,y,z-.35,x+.35,y+.15,z+.35);
    }
    public static boolean valid(Player p,BlockPos pos,boolean wall,boolean east) {
        if(!p.isAlive()||p.isSpectator())return false;
        if(!wall&&(SafeZones.contains(p)||pos!=null&&SafeZones.contains(p.level(),pos.getX()+.5,pos.getY(),pos.getZ()+.5)))return false;
        if(pos==null||!p.level().getWorldBorder().isWithinBounds(pos)||pos.getY()<p.level().getMinBuildHeight()||pos.getY()+(wall?3:1)>p.level().getMaxBuildHeight())return false;
        AABB area=bounds(pos,wall,east);
        if(!p.level().noCollision(area)||!p.level().getEntities(p,area,e->!e.isSpectator()).isEmpty()||area.intersects(p.getBoundingBox()))return false;
        int width=wall?1:0;
        for(int offset=-width;offset<=width;offset++) {
            var support=pos.offset(east?0:offset,-1,east?offset:0);
            if(!p.level().getBlockState(support).isFaceSturdy(p.level(),support,Direction.UP))return false;
            // Deployables are entities, not block edits: arena adventure mode must allow them.
        }
        return true;
    }
    public static void action(ServerPlayer p,String action) {
        if(SurvivalGadgets.jetting(p)) {cancel(p);return;}
        var preview=PREVIEW.get(p.getUUID());
        String kind=preview==null?null:preview.kind();
        if(action.equals("cancelplace")) {cancel(p);return;}
        if(kind==null||!validPreview(p,preview)) {cancel(p);return;}
        ItemStack stack=p.getMainHandItem().getItem() instanceof GadgetItem item&&item.kind.equals(kind)?p.getMainHandItem():p.getOffhandItem();
        if(!(stack.getItem() instanceof GadgetItem item)||!item.kind.equals(kind)||!SecondaryRuntime.ready(p,stack)) {cancel(p);return;}
        boolean wall=kind.equals("barrier"),east=p.getDirection().getAxis()==Direction.Axis.X;
        if(kind.equals("orbital_strike")) {
            var pos=target(p,ExtraGadgetConfig.orbital.range());
            if(!valid(p,pos,false,false)) {p.displayClientMessage(Component.literal("이 위치에는 폭격을 지정할 수 없습니다."),true);return;}
            var strike=GadgetItems.ORDNANCE.get().create(p.serverLevel());if(strike==null)return;
            strike.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);strike.setup(p,true);
            if(p.serverLevel().addFreshEntity(strike)) {
                p.serverLevel().playSound(null,pos,net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE,net.minecraft.sounds.SoundSource.PLAYERS,3,1.2f);
                SecondaryRuntime.commit(p,stack);cancel(p);
            }
            return;
        }
        var pos=target(p,wall?GadgetConfig.barrier.range():GadgetConfig.mine.range());
        if(!valid(p,pos,wall,east)) {p.displayClientMessage(Component.literal("이 위치에는 설치할 수 없습니다."),true);return;}
        PLACED.values().removeIf(DeployableEntity::isRemoved);
        long count=DeployableLedger.get(p.server).count(p.getUUID(),wall);
        if(count>=(wall?GadgetConfig.barrier.maxPlaced():GadgetConfig.mine.maxPlaced())) {p.displayClientMessage(Component.literal(wall?"설치 가능한 방벽 개수를 초과했습니다.":"설치 가능한 지뢰 개수를 초과했습니다."),true);return;}
        var entity=GadgetItems.DEPLOYABLE.get().create(p.serverLevel());if(entity==null)return;
        entity.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);entity.setup(p,wall,east);
        if(p.serverLevel().addFreshEntity(entity)) {track(entity);SecondaryRuntime.commit(p,stack);cancel(p);}else entity.discard();
    }
    public static DeployableEntity aimedMine(Player p) {
        Vec3 eye=p.getEyePosition(),end=eye.add(p.getLookAngle().scale(5));
        return p.level().getEntitiesOfClass(DeployableEntity.class,p.getBoundingBox().inflate(5),e->!e.wall()&&p.getUUID().equals(e.owner())&&e.getBoundingBox().inflate(.2).clip(eye,end).isPresent()).stream()
                .min(Comparator.comparingDouble(p::distanceToSqr)).orElse(null);
    }
    private static void cancel(ServerPlayer p) {PREVIEW.remove(p.getUUID());GungangNetwork.send(p,new GungangNetwork.Gadget(""));}
    private static boolean validPreview(ServerPlayer p,Preview preview) {
        return p.isAlive()&&!p.isSpectator()&&p.getInventory().selected==preview.slot()
                &&(CombatGadgets.is(p.getMainHandItem(),preview.kind())||CombatGadgets.is(p.getOffhandItem(),preview.kind()));
    }
    public static boolean placing(Player player) {
        if(!(player instanceof ServerPlayer p))return false;
        var preview=PREVIEW.get(p.getUUID());return preview!=null&&validPreview(p,preview);
    }
    public static void clear(ServerPlayer p) {
        SurvivalGadgets.clear(p);
        cancel(p);HEALING.remove(p.getUUID());BOOST.remove(p.getUUID());
        CombatGadgets.clear(p);
        clearDeployables(p);
        p.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(SPEED);p.getAttribute(Attributes.ATTACK_SPEED).removeModifier(ATTACK);
    }
    public static void clearDeployables(ServerPlayer p) {
        cancel(p);
        DeployableLedger.get(p.server).clear(p.getUUID());
        for(var level:p.server.getAllLevels()) {
            var owned=new ArrayList<DeployableEntity>();
            for(var entity:level.getAllEntities())if(entity instanceof DeployableEntity placed&&p.getUUID().equals(placed.owner()))owned.add(placed);
            owned.forEach(DeployableEntity::discard);
        }
        PLACED.values().removeIf(e->{if(p.getUUID().equals(e.owner())) {e.discard();return true;}return false;});
    }
    public static void changingSecondary(ServerPlayer p,Loadouts.Weapon weapon) {
        for(int i=0;i<p.getInventory().getContainerSize();i++) {
            var stack=p.getInventory().getItem(i);
            if(SecondaryRuntime.secondary(stack)&&!weapon.id().equals(stack.getTag().getString("GunGangSecondaryId"))) {clearDeployables(p);return;}
        }
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer p))return;
        var preview=PREVIEW.get(p.getUUID());if(preview!=null&&!validPreview(p,preview))cancel(p);
        if(!p.isAlive()||p.isSpectator()) {HEALING.remove(p.getUUID());if(PREVIEW.containsKey(p.getUUID()))cancel(p);}
        var heals=HEALING.get(p.getUUID());
        if(heals!=null) {var next=new ArrayList<Healing>();for(var h:heals) {p.heal(h.perTick());if(h.remaining()>1)next.add(new Healing(h.remaining()-1,h.perTick()));}if(next.isEmpty())HEALING.remove(p.getUUID());else HEALING.put(p.getUUID(),next);}
        var boost=BOOST.get(p.getUUID());
        if(boost!=null&&(p.level().getGameTime()>=boost.end()||!p.isAlive()||p.isSpectator())) {BOOST.remove(p.getUUID());p.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(SPEED);p.getAttribute(Attributes.ATTACK_SPEED).removeModifier(ATTACK);}
    }
    @SubscribeEvent public static void leave(PlayerEvent.PlayerLoggedOutEvent event) {if(event.getEntity() instanceof ServerPlayer p)clear(p);}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) {PREVIEW.clear();HEALING.clear();BOOST.clear();PLACED.clear();}
    private GadgetManager() { }
}
