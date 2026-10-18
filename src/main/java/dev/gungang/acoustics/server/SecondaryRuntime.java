package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class SecondaryRuntime {
    private static final Map<UUID,Map<String,Long>> DEADLINES=new HashMap<>();
    private static final Map<ItemCooldowns,java.lang.ref.WeakReference<Player>> OWNERS=Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Scope> CURRENT=new ThreadLocal<>();
    private static final class Scope { final ItemStack stack; boolean consumed; Scope(ItemStack stack) { this.stack=stack; } }
    public static boolean secondary(ItemStack stack) {
        if(stack.getTag()==null)return false;
        var tag=stack.getTag();
        return (tag.contains("GunGangIssued",3)&&tag.getInt("GunGangIssued")==40)||(tag.contains("GunGangFreeSlot",3)&&tag.getInt("GunGangFreeSlot")==40);
    }
    public static void configure(Player player,ItemStack stack,Loadouts.Weapon weapon) {
        OWNERS.put(player.getCooldowns(),new java.lang.ref.WeakReference<>(player));
        var tag=stack.getOrCreateTag();tag.putString("GunGangSecondaryId",weapon.id());
        tag.putInt("GunGangCooldownTicks",(int)Math.ceil(weapon.cooldownSeconds()*20));
        if(CombatGadgets.is(stack,"molotov"))tag.putInt("GunGangCooldownTicks",ExtraGadgetConfig.ticks(ExtraGadgetConfig.molotov.cooldownSeconds()));
        if(CombatGadgets.is(stack,"life_support"))tag.putInt("GunGangCooldownTicks",ExtraGadgetConfig.ticks(ExtraGadgetConfig.life.cooldownSeconds()));
        if(CombatGadgets.is(stack,"jetpack")) {
            tag.putInt("GunGangCooldownTicks",ExtraGadgetConfig.ticks(ExtraGadgetConfig.jet.cooldownSeconds()));
            tag.putBoolean("GunGangJetpackVerticalFlight",weapon.jetpackVerticalFlight()||ExtraGadgetConfig.jet.verticalFlight());
        }
        if(CombatGadgets.is(stack,"orbital_strike")) {
            tag.putInt("GunGangCooldownTicks",ExtraGadgetConfig.ticks(ExtraGadgetConfig.orbital.cooldownSeconds()));
            if(!player.level().isClientSide)DEADLINES.computeIfAbsent(player.getUUID(),id->new HashMap<>())
                    .computeIfAbsent(weapon.id(),id->player.level().getGameTime()+ExtraGadgetConfig.ticks(ExtraGadgetConfig.orbital.cooldownSeconds()));
        }
        tag.putLong("GunGangCooldownEnd",DEADLINES.getOrDefault(player.getUUID(),Map.of()).getOrDefault(weapon.id(),0L));
        CombatGadgets.configure(player,stack);
    }
    public static long remaining(Player player,ItemStack stack) {
        return secondary(stack)&&stack.getTag().getInt("GunGangCooldownTicks")>0?Math.max(0,stack.getTag().getLong("GunGangCooldownEnd")-player.level().getGameTime()):0;
    }
    public static boolean ready(Player player,ItemStack stack) { return !FreeForAllMatch.blocked(player)&&remaining(player,stack)==0; }
    public static void commit(Player player,ItemStack stack) {
        if(!secondary(stack)||player.level().isClientSide)return;
        var tag=stack.getOrCreateTag();long end=player.level().getGameTime()+tag.getInt("GunGangCooldownTicks");
        tag.putLong("GunGangCooldownEnd",end);
        DEADLINES.computeIfAbsent(player.getUUID(),id->new HashMap<>()).put(tag.getString("GunGangSecondaryId"),end);
        player.inventoryMenu.broadcastChanges();
    }
    public static <T> T use(ItemStack stack,LivingEntity entity,Supplier<T> action,T blocked) {
        if(FreeForAllMatch.blocked(entity))return blocked;
        if(SurvivalGadgets.jetting(entity))return blocked;
        if(entity instanceof Player p&&CombatGadgets.holdingShield(p)&&!CombatGadgets.is(stack,"shield"))return blocked;
        if(SafeZones.contains(entity)&&SafeZones.offensive(stack))return blocked;
        if(!(entity instanceof Player player)||!secondary(stack))return action.get();
        if(!ready(player,stack)&&!(stack.getItem() instanceof GadgetItem))return blocked;
        Scope before=CURRENT.get(),scope=new Scope(stack);CURRENT.set(scope);
        try { return action.get(); }
        finally { CURRENT.set(before); if(before==null)CURRENT.remove(); if(scope.consumed)commit(player,stack); }
    }
    public static boolean preventDecrease(ItemStack stack,int next) {
        Scope scope=CURRENT.get();
        if(scope!=null&&scope.stack==stack&&next<stack.getCount()) { scope.consumed=true;return true; }
        return false;
    }
    public static boolean overriding() { return CURRENT.get()!=null; }
    public static void requestedCooldown() { var scope=CURRENT.get();if(scope!=null)scope.consumed=true; }
    @SuppressWarnings("unchecked")
    public static void registerTacz() {
        for(String name:List.of("GunFireEvent","GunMeleeEvent"))try {
            Class<net.minecraftforge.eventbus.api.Event> type=(Class<net.minecraftforge.eventbus.api.Event>)Class.forName("com.tacz.guns.api.event.common."+name);
            var shooter=type.getMethod("getShooter");var item=type.getMethod("getGunItemStack");
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,false,type,event->{
                try {
                    if(shooter.invoke(event) instanceof Player player&&(FreeForAllMatch.blocked(player)||CombatGadgets.holdingShield(player)||SurvivalGadgets.jetting(player)||GadgetManager.placing(player))) {if(event.isCancelable())event.setCanceled(true);return;}
                    if(shooter.invoke(event) instanceof Player player&&item.invoke(event) instanceof ItemStack stack&&secondary(stack)) {
                        if(!ready(player,stack)) {if(event.isCancelable())event.setCanceled(true);}
                        else commit(player,stack);
                    }
                }catch(ReflectiveOperationException failure) {com.mojang.logging.LogUtils.getLogger().error("GunGang TaCZ cooldown hook failed",failure);}
            });
        }catch(ClassNotFoundException absent) { /* TaCZ is optional. */ }
        catch(ReflectiveOperationException incompatible) {com.mojang.logging.LogUtils.getLogger().warn("GunGang TaCZ cooldown event unavailable",incompatible);}
    }
    public static Float cooldown(ItemCooldowns cooldowns,Item item,float partial) {
        if(overriding()) return 0f;
        var ref=OWNERS.get(cooldowns);Player p=ref==null?null:ref.get();if(p==null)return null;
        ItemStack stack=p.getOffhandItem();
        if(!stack.is(item)||!secondary(stack)) {
            stack=ItemStack.EMPTY;
            for(int i=0;i<p.getInventory().getContainerSize();i++) {
                var candidate=p.getInventory().getItem(i);
                if(candidate.is(item)&&secondary(candidate)) {stack=candidate;break;}
            }
        }
        if(stack.isEmpty())return null;
        if(item instanceof GadgetItem gadget&&gadget.kind.equals("landmine")&&GadgetManager.aimedMine(p)!=null)return 0f;
        int total=stack.getTag().getInt("GunGangCooldownTicks");
        return total==0?0f:Math.max(0,Math.min(1,(remaining(p,stack)-partial)/total));
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e) {
        if(e.phase==TickEvent.Phase.START)OWNERS.put(e.player.getCooldowns(),new java.lang.ref.WeakReference<>(e.player));
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { DEADLINES.remove(e.getEntity().getUUID());OWNERS.remove(e.getEntity().getCooldowns()); }
    public static void clear(Player p) { DEADLINES.remove(p.getUUID()); }
    /** /gungang cooldown reset: secondary/gadget deadlines, the item tags the HUD reads, and vanilla item cooldowns. */
    public static void resetCooldowns(net.minecraft.server.level.ServerPlayer p) {
        DEADLINES.remove(p.getUUID());CombatGadgets.resetScan(p);
        var inventory=p.getInventory();
        for(int i=0;i<inventory.getContainerSize();i++) {
            var stack=inventory.getItem(i);if(stack.isEmpty())continue;
            p.getCooldowns().removeCooldown(stack.getItem());
            if(stack.getTag()!=null&&stack.getTag().contains("GunGangCooldownEnd"))stack.getTag().putLong("GunGangCooldownEnd",0);
        }
        p.inventoryMenu.broadcastChanges();
    }
    private SecondaryRuntime() { }
}
