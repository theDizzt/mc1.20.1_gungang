package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.*;
import net.minecraft.world.damagesource.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class SafeZones {
    private static SafeZoneRules serverRules=SafeZoneRules.empty(),clientRules=SafeZoneRules.empty();
    private static final java.util.Set<java.util.UUID> NOTIFIED=new java.util.HashSet<>();
    public static void receive(String json){clientRules=ServerFiles.GSON.fromJson(json,SafeZoneRules.class);}
    public static void clearClient(){clientRules=SafeZoneRules.empty();}
    public static void load(net.minecraft.server.MinecraftServer server)throws java.io.IOException {
        var path=ServerFiles.root().resolve("server/safe_zones.json");
        ConfigLayout.writeNew(path,ServerFiles.GSON.toJson(SafeZoneRules.empty()));
        if(java.nio.file.Files.size(path)>262144)throw new java.io.IOException("safe_zones.json exceeds 256 KiB");
        var next=ServerFiles.GSON.fromJson(java.nio.file.Files.readString(path),SafeZoneRules.class);
        if(next==null)throw new java.io.IOException("Empty safe_zones.json");serverRules=next;
        for(var player:server.getPlayerList().getPlayers())sync(player);
    }
    public static void sync(net.minecraft.server.level.ServerPlayer player){GungangNetwork.send(player,new GungangNetwork.SafeZoneConfig(ServerFiles.GSON.toJson(serverRules)));}
    /** Client side of {@link #armed}: set by PropHuntClient for the local seeker. */
    public static java.util.function.Predicate<Entity> clientArmed=e->false;
    /** Client: the local player is in a running prop hunt (set by PropHuntClient). */
    public static java.util.function.BooleanSupplier clientInMatch=()->false;
    public static boolean contains(Entity entity){return entity!=null&&!armed(entity)&&contains(entity.level(),entity.getX(),entity.getY(),entity.getZ());}
    /** During a prop hunt match seekers (and their shots) may fire inside safe zones, and disguises there can be hit. */
    private static boolean armed(Entity entity){
        if(entity instanceof Projectile projectile&&projectile.getOwner()!=null&&projectile.getOwner()!=entity)return armed(projectile.getOwner());
        if(entity.level().isClientSide)return clientArmed.test(entity);
        var match=MatchManager.propHunt();return match!=null&&match.armed(entity);
    }
    public static boolean contains(net.minecraft.world.level.Level level,double x,double y,double z){
        return (level.isClientSide?clientRules:serverRules).contains(level.dimension().location().toString(),x,y,z);
    }
    public static boolean offensive(ItemStack stack){
        var item=stack.getItem();var id=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item);
        return item instanceof ProjectileWeaponItem||item instanceof TridentItem||item instanceof SnowballItem||item instanceof EggItem
                ||item instanceof FireworkRocketItem||item instanceof ThrowablePotionItem&&net.minecraft.world.item.alchemy.PotionUtils.getMobEffects(stack).stream().anyMatch(e->!e.getEffect().isBeneficial())
                ||item instanceof HandAxeItem||item instanceof GadgetItem gadget&&java.util.Set.of("landmine","molotov","orbital_strike","bear_trap","turret").contains(gadget.kind)
                ||id!=null&&(id.getNamespace().equals("tacz")||id.getNamespace().equals("lrtactical"));
    }
    public static boolean blocked(Entity target,DamageSource source){
        boolean combat=source.getEntity()!=null||source.getDirectEntity()!=null||source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)
                ||source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)||source.is(DamageTypes.MAGIC)||source.is(DamageTypes.INDIRECT_MAGIC);
        return contains(source.getEntity())||contains(source.getDirectEntity())||combat&&contains(target);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void attack(AttackEntityEvent event){if(contains(event.getEntity())||contains(event.getTarget()))event.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void hurt(LivingAttackEvent event){if(blocked(event.getEntity(),event.getSource()))event.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void damage(LivingHurtEvent event){if(blocked(event.getEntity(),event.getSource()))event.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void use(PlayerInteractEvent.RightClickItem event){
        if(contains(event.getEntity())&&offensive(event.getItemStack())){event.setCanceled(true);event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void using(LivingEntityUseItemEvent.Start event){if(contains(event.getEntity())&&offensive(event.getItem()))event.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void projectile(EntityJoinLevelEvent event){
        if(event.getLevel().isClientSide||!(event.getEntity() instanceof Projectile projectile)||projectile instanceof ThrownEnderpearl||projectile instanceof FishingHook)return;
        if(projectile instanceof ThrownPotion potion&&!offensive(potion.getItem()))return;
        if(contains(projectile)||contains(projectile.getOwner()))event.setCanceled(true);
    }
    @SubscribeEvent public static void notice(net.minecraftforge.event.TickEvent.PlayerTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END||!(event.player instanceof net.minecraft.server.level.ServerPlayer player))return;
        if(contains(player)){
            if(NOTIFIED.add(player.getUUID())||player.tickCount%20==0)player.displayClientMessage(net.minecraft.network.chat.Component.literal("비무장 지대"),true);
        }else if(NOTIFIED.remove(player.getUUID()))player.displayClientMessage(net.minecraft.network.chat.Component.empty(),true);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){NOTIFIED.remove(event.getEntity().getUUID());}
    @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppedEvent event){serverRules=SafeZoneRules.empty();NOTIFIED.clear();}
    @SuppressWarnings("unchecked") public static void registerTacz(){
        for(String name:java.util.List.of("GunFireEvent","GunMeleeEvent"))try {
            var type=(Class<Event>)Class.forName("com.tacz.guns.api.event.common."+name);var shooter=type.getMethod("getShooter");
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,false,type,event->{
                try {if(shooter.invoke(event) instanceof Entity entity&&contains(entity)&&event.isCancelable())event.setCanceled(true);}
                catch(ReflectiveOperationException failure){com.mojang.logging.LogUtils.getLogger().error("Safe-zone gun hook failed",failure);}
            });
        }catch(ClassNotFoundException absent){ /* Optional gun mod. */ }
        catch(ReflectiveOperationException incompatible){com.mojang.logging.LogUtils.getLogger().warn("Safe-zone gun hook unavailable",incompatible);}
    }
}
