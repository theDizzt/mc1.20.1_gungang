package dev.gungang.acoustics.server;

import java.util.*;
import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class ToeWeapon {
    private static final Map<UUID,ToeTrailEntity> TRAILS=new HashMap<>();
    /** Players holding left click (sprint), with the fraction of a block of length consumed so far. */
    private static final Map<UUID,Double> SPRINTING=new HashMap<>();
    private static final UUID SPRINT_SPEED=UUID.fromString("5f0b7f4e-3c1a-4d8e-9b62-0c7d1e9a4b37");
    /** Growth pauses while sprinting, so the length drops exactly at the configured rate. */
    public static boolean sprinting(LivingEntity owner){return owner!=null&&SPRINTING.containsKey(owner.getUUID());}
    private static int length(net.minecraft.world.item.ItemStack stack){
        var tag=stack.getOrCreateTag();return tag.contains("ToeLength")?tag.getInt("ToeLength"):ExtraGadgetConfig.toe.initialLength();
    }
    /** Left click pressed or released. Starts only on a press, off cooldown and above the minimum length. */
    public static void sprint(net.minecraft.server.level.ServerPlayer player,boolean down){
        if(!down){stopSprint(player,true);return;}
        if(sprinting(player))return;
        var stack=player.getMainHandItem();
        if(!ToeItem.is(stack)||!allowed(player)||ToeItem.cooldown(player.level(),stack)>0||length(stack)<=ExtraGadgetConfig.toe.initialLength())return;
        SPRINTING.put(player.getUUID(),0.0);
        var speed=player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if(speed!=null){
            // On top of the primary's own speed: 150% total from the loadout's 120%.
            speed.removeModifier(SPRINT_SPEED);
            speed.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(SPRINT_SPEED,"GunGang toe sprint",
                    ExtraGadgetConfig.toe.sprintMovementSpeed()/100/LoadoutAttributes.primarySpeed(player)-1,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }
    /** Ends the sprint; {@code cooldown} starts the left-click cooldown on the carried toe. */
    static void stopSprint(LivingEntity owner,boolean cooldown){
        if(SPRINTING.remove(owner.getUUID())==null)return;
        var speed=owner.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);if(speed!=null)speed.removeModifier(SPRINT_SPEED);
        var stack=ToeItem.carried(owner);
        if(cooldown&&!stack.isEmpty()){
            int ticks=ExtraGadgetConfig.ticks(ExtraGadgetConfig.toe.sprintCooldownSeconds());var tag=stack.getOrCreateTag();
            tag.putLong("ToeCooldownEnd",owner.level().getGameTime()+ticks);tag.putInt("ToeCooldownTicks",ticks);
        }
    }
    /** One tick of sprinting: spends length, and stops (forced) at the minimum length or off the toe. */
    private static void sprintTick(net.minecraft.server.MinecraftServer server){
        var c=ExtraGadgetConfig.toe;
        for(var id:new ArrayList<>(SPRINTING.keySet())){
            var player=server.getPlayerList().getPlayer(id);
            if(player==null){SPRINTING.remove(id);continue;}
            var stack=player.getMainHandItem();
            if(!ToeItem.is(stack)||!allowed(player)){stopSprint(player,true);continue;}
            double spent=SPRINTING.get(id)+c.sprintLengthPerSecond()/20;
            var tag=stack.getOrCreateTag();long interval=Math.max(1,Math.round(c.growthSeconds()*20));
            long growth=tag.contains("ToeGrowthTicks")?tag.getLong("ToeGrowthTicks"):Math.max(0,length(stack)-c.initialLength())*interval;
            while(spent>=1&&growth>0){spent-=1;growth=Math.max(0,growth-interval);}
            tag.putLong("ToeGrowthTicks",growth);
            int length=Math.min(c.maximumLength(),c.initialLength()+(int)(growth/interval));tag.putInt("ToeLength",length);
            if(length<=c.initialLength())stopSprint(player,true);else SPRINTING.put(id,spent);
        }
    }
    static boolean aliveEquipped(LivingEntity owner){
        return owner!=null&&owner.isAlive()&&!owner.isDeadOrDying()&&!owner.isRemoved()&&!owner.isSpectator()
                &&!ToeItem.carried(owner).isEmpty()&&!LobbyManager.respawning(owner.getUUID());
    }
    static boolean allowed(LivingEntity owner){
        return aliveEquipped(owner)&&!SafeZones.contains(owner)&&!FreeForAllMatch.blocked(owner)
                &&!LobbyManager.inEitherSpawn(owner.level(),owner.getBoundingBox());
    }
    public static void reset(LivingEntity owner){
        stopSprint(owner,false);
        var trail=TRAILS.remove(owner.getUUID());if(trail!=null)trail.discard();
        resetStack(ToeItem.carried(owner));
        if(owner instanceof net.minecraft.world.entity.player.Player player)for(var stack:player.getInventory().items)resetStack(stack);
    }
    private static void resetStack(net.minecraft.world.item.ItemStack stack){
        if(ToeItem.is(stack)){var tag=stack.getOrCreateTag();tag.putInt("ToeLength",ExtraGadgetConfig.toe.initialLength());tag.putLong("ToeGrowthTicks",0);tag.remove("ToeCooldownEnd");tag.remove("ToeCooldownTicks");}
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void death(net.minecraftforge.event.entity.living.LivingDeathEvent event){reset(event.getEntity());}
    @SubscribeEvent public static void respawn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event){reset(event.getEntity());}
    @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event){reset(event.getEntity());}
    static void ensureLoadout(java.nio.file.Path directory)throws java.io.IOException{DefaultWeapons.ensure(directory,"39","39_toe.json");}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        // Do not rely on entity ticks: an old trail can be in an unloaded chunk after death/teleport.
        sprintTick(event.getServer());
        TRAILS.entrySet().removeIf(e->{var trail=e.getValue();if(trail.validOwner())return false;trail.discard();return true;});
        var bodies=new ArrayList<LivingEntity>(event.getServer().getPlayerList().getPlayers());bodies.addAll(BotManager.entities());
        for(var owner:bodies){
            if(!aliveEquipped(owner)){reset(owner);continue;}
            // Protected areas remove the geometry but preserve both length and partial growth progress.
            if(!allowed(owner))continue;
            var existing=TRAILS.get(owner.getUUID());
            if(existing!=null){if(existing.ownedBy(owner))continue;reset(owner);}
            var level=(ServerLevel)owner.level();var trail=GadgetItems.TOE_TRAIL.get().create(level);
            if(trail!=null){trail.start(owner);if(level.addFreshEntity(trail))TRAILS.put(owner.getUUID(),trail);}
        }
    }
    /** Integrate vanilla vertical drag/gravity to find a velocity whose apex is the configured height. */
    public static double jumpVelocity(double height){
        double low=0,high=3;
        for(int i=0;i<30;i++){double v=(low+high)/2,y=0;for(double step=v;step>0;step=(step-.08)*.98)y+=step;if(y<height)low=v;else high=v;}
        return (low+high)/2;
    }
    @SubscribeEvent public static void jump(net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent event){
        var entity=event.getEntity();var stack=ToeItem.carried(entity);if(stack.isEmpty())return;
        double height=entity.level().isClientSide?stack.getOrCreateTag().getDouble("ToeJumpHeight"):ExtraGadgetConfig.toe.jumpHeight();
        if(entity.level().isClientSide&&!stack.getOrCreateTag().contains("ToeJumpHeight"))height=3;
        var velocity=entity.getDeltaMovement();entity.setDeltaMovement(velocity.x,jumpVelocity(height),velocity.z);entity.hasImpulse=true;
    }
    @SubscribeEvent public static void attack(net.minecraftforge.event.entity.player.AttackEntityEvent event){
        var p=event.getEntity();if(ToeItem.is(p.getMainHandItem())||ToeItem.blocksSecondary(p,p.getMainHandItem()))event.setCanceled(true);
    }
    @SubscribeEvent public static void use(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem event){
        if(ToeItem.blocksSecondary(event.getEntity(),event.getItemStack()))event.setCanceled(true);
    }
    @SubscribeEvent public static void hurt(net.minecraftforge.event.entity.living.LivingHurtEvent event){
        if(event.getSource().getEntity() instanceof LivingEntity owner&&event.getSource().getDirectEntity()==owner
                &&(ToeItem.is(owner.getMainHandItem())||ToeItem.blocksSecondary(owner,owner.getMainHandItem())))event.setCanceled(true);
    }
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event){TRAILS.clear();SPRINTING.clear();}
    private ToeWeapon(){}
}
