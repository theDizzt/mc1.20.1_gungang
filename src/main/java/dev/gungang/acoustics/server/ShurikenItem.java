package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Primary 33 shuriken. The magazine, fire mode and reload live in the stack's tag (synced to the holder for the HUD);
 * every throw is server-authoritative ({@code shurikenThrow} action from the attack key). The fire mode is switched
 * with TaCZ's fire select key and reloaded with TaCZ's reload key ({@code dev.gungang.acoustics.client.ShurikenInput}).
 * Holding one in the inventory also grants a double jump ({@link #doubleJump}). Settings: {@link ExtraGadgetConfig.Shuriken}.
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class ShurikenItem extends Item {
    public enum Mode {SEMI("Semi"),BURST("Burst"),RICOCHET("Ricochet");
        public final String label;Mode(String label){this.label=label;}
        public Mode next(){return values()[(ordinal()+1)%values().length];}
    }
    public static final String AMMO="ShurikenAmmo",CAPACITY="ShurikenCapacity",MODE="ShurikenMode",RELOAD_END="ShurikenReloadEnd",RELOAD_TICKS="ShurikenReloadTicks",
            JUMP="ShurikenJump",CLIMB="ShurikenClimb",INTERVAL="ShurikenInterval",PER_THROW="ShurikenPerThrow";
    private static final String LAST_THROW="ShurikenLastThrow",AIR_JUMPED="ShurikenAirJumped",LAST_CLIMB="ShurikenLastClimb";
    /** Fewest ticks between two wall-climb steps (the client paces taps the same way). */
    public static final int CLIMB_TICKS=4;
    /** Network jitter the server forgives between two throws. */
    private static final int THROW_JITTER_TICKS=2;
    /** Semi-mode follow-up throws still to come: player, game time due, damage. */
    private record Pending(UUID player,long due,double damage) { }
    private static final List<Pending> PENDING=new ArrayList<>();

    public ShurikenItem(){super(new Properties().stacksTo(1));}
    public static boolean is(ItemStack stack){return stack.getItem() instanceof ShurikenItem;}
    /** Ammo and reload updates must not lower and re-raise the held shuriken. */
    @Override public boolean shouldCauseReequipAnimation(ItemStack oldStack,ItemStack newStack,boolean slotChanged){return slotChanged||oldStack.getItem()!=newStack.getItem();}
    @Override public boolean canAttackBlock(net.minecraft.world.level.block.state.BlockState state,Level level,net.minecraft.core.BlockPos pos,Player player){return false;}
    @Override public boolean onEntitySwing(ItemStack stack,net.minecraft.world.entity.LivingEntity entity){return true;}

    private static ExtraGadgetConfig.Shuriken config(){return ExtraGadgetConfig.shuriken;}
    public static int ammo(ItemStack stack){
        return stack.hasTag()&&stack.getTag().contains(AMMO)?Math.max(0,stack.getTag().getInt(AMMO)):capacity(stack);
    }
    /** The magazine size the server last synced to this stack (HUD). */
    public static int capacity(ItemStack stack){return stack.hasTag()&&stack.getTag().contains(CAPACITY)?Math.max(1,stack.getTag().getInt(CAPACITY)):ExtraGadgetConfig.SHURIKEN_DEFAULTS.capacity();}
    public static Mode mode(ItemStack stack){
        int value=stack.hasTag()?stack.getTag().getInt(MODE):0;
        return Mode.values()[Math.floorMod(value,Mode.values().length)];
    }
    /** Game time the running reload ends (0: not reloading). */
    public static long reloadEnd(ItemStack stack){return stack.hasTag()?stack.getTag().getLong(RELOAD_END):0;}
    public static int reloadTicks(ItemStack stack){return stack.hasTag()?Math.max(1,stack.getTag().getInt(RELOAD_TICKS)):1;}
    /** Upward speed of the double jump, as last synced (blocks per tick). */
    public static double jumpVelocity(ItemStack stack){return stack.hasTag()&&stack.getTag().contains(JUMP)?stack.getTag().getDouble(JUMP):ExtraGadgetConfig.SHURIKEN_DEFAULTS.doubleJumpVelocity();}
    /** Ammo one throw spends, as last synced (HUD countdown, client throw animation). */
    public static int perThrow(ItemStack stack){return stack.hasTag()&&stack.getTag().contains(PER_THROW)?Math.max(1,stack.getTag().getInt(PER_THROW)):ExtraGadgetConfig.SHURIKEN_DEFAULTS.ammoPerThrow();}
    /** Upward speed of one wall-climb step, as last synced (blocks per tick). */
    public static double climbVelocity(ItemStack stack){return stack.hasTag()&&stack.getTag().contains(CLIMB)?stack.getTag().getDouble(CLIMB):ExtraGadgetConfig.SHURIKEN_DEFAULTS.wallClimbVelocity();}
    /** Ticks between throws as last synced (client input pacing). */
    public static int throwTicks(ItemStack stack){return stack.hasTag()&&stack.getTag().contains(INTERVAL)?Math.max(1,stack.getTag().getInt(INTERVAL)):interval(ExtraGadgetConfig.SHURIKEN_DEFAULTS);}
    private static int interval(ExtraGadgetConfig.Shuriken c){return Math.max(1,(int)Math.round(c.attackIntervalSeconds()*20));}

    @Override public void inventoryTick(ItemStack stack,Level level,net.minecraft.world.entity.Entity entity,int slot,boolean selected){
        if(level.isClientSide||!(entity instanceof Player p))return;
        var c=config();var tag=stack.getOrCreateTag();
        if(tag.getInt(CAPACITY)!=c.capacity())tag.putInt(CAPACITY,c.capacity());
        if(!tag.contains(AMMO)||tag.getInt(AMMO)>c.capacity())tag.putInt(AMMO,c.capacity());
        if(tag.getDouble(JUMP)!=c.doubleJumpVelocity())tag.putDouble(JUMP,c.doubleJumpVelocity());
        if(tag.getInt(PER_THROW)!=c.ammoPerThrow())tag.putInt(PER_THROW,c.ammoPerThrow());
        if(!tag.contains(CLIMB)||tag.getDouble(CLIMB)!=c.wallClimbVelocity())tag.putDouble(CLIMB,c.wallClimbVelocity());
        if(tag.getInt(INTERVAL)!=interval(c))tag.putInt(INTERVAL,interval(c));
        long end=tag.getLong(RELOAD_END);
        if(end>0){
            // Like a TaCZ gun, putting it away cancels the reload.
            if(p.getMainHandItem()!=stack){tag.remove(RELOAD_END);}
            else if(level.getGameTime()>=end){
                tag.remove(RELOAD_END);tag.putInt(AMMO,c.capacity());
                level.playSound(null,p.blockPosition(),SoundEvents.ARMOR_EQUIP_CHAIN,SoundSource.PLAYERS,.8f,1.4f);
            }
        }
    }
    private static boolean usable(Player p){
        return p.getMainHandItem().getItem() instanceof ShurikenItem&&!p.isSpectator()&&p.isAlive()&&!LobbyManager.respawning(p.getUUID())
                &&!FreeForAllMatch.blocked(p)&&!SafeZones.contains(p)&&!CombatGadgets.holdingShield(p)&&!SurvivalGadgets.jetting(p)&&!GadgetManager.placing(p);
    }
    /** Starts a reload when the magazine is not full (TaCZ reload key, or an empty magazine). */
    public static void reload(Player p){
        var stack=p.getMainHandItem();
        if(p.level().isClientSide||!is(stack)||!usable(p))return;
        var c=config();var tag=stack.getOrCreateTag();
        if(tag.getLong(RELOAD_END)>0||ammo(stack)>=c.capacity())return;
        int ticks=Math.max(1,ExtraGadgetConfig.ticks(c.reloadSeconds()));
        tag.putLong(RELOAD_END,p.level().getGameTime()+ticks);tag.putInt(RELOAD_TICKS,ticks);
        // Pending semi throws belong to the magazine being swapped out.
        PENDING.removeIf(pending->pending.player().equals(p.getUUID()));
        p.level().playSound(null,p.blockPosition(),SoundEvents.ARMOR_EQUIP_LEATHER,SoundSource.PLAYERS,.8f,1.2f);
        p.inventoryMenu.broadcastChanges();
    }
    /** TaCZ fire select key: Semi → Burst → Ricochet → Semi. */
    public static void cycleMode(Player p){
        var stack=p.getMainHandItem();
        if(p.level().isClientSide||!is(stack)||p.isSpectator())return;
        stack.getOrCreateTag().putInt(MODE,mode(stack).next().ordinal());
        p.level().playSound(null,p.blockPosition(),SoundEvents.UI_BUTTON_CLICK.value(),SoundSource.PLAYERS,.4f,1.6f);
        p.inventoryMenu.broadcastChanges();
    }
    /** One click of the attack key: spends ammoPerThrow and throws by the current mode. */
    public static void attack(Player p){
        var stack=p.getMainHandItem();
        if(p.level().isClientSide||!is(stack)||!usable(p))return;
        var c=config();var tag=stack.getOrCreateTag();
        if(tag.getLong(RELOAD_END)>0)return;
        long now=p.level().getGameTime();var data=p.getPersistentData();
        if(data.contains(LAST_THROW)&&now-data.getLong(LAST_THROW)>=0&&now-data.getLong(LAST_THROW)<Math.max(1,interval(c)-THROW_JITTER_TICKS))return;
        int ammo=ammo(stack);
        if(ammo<c.ammoPerThrow()){reload(p);return;}
        data.putLong(LAST_THROW,now);
        tag.putInt(AMMO,ammo-c.ammoPerThrow());
        switch(mode(stack)){
            case SEMI->{
                launch(p,p.getLookAngle(),c.semiDamage(),0);
                for(int i=1;i<3;i++){
                    if(c.semiIntervalTicks()==0)launch(p,p.getLookAngle(),c.semiDamage(),0);
                    else PENDING.add(new Pending(p.getUUID(),now+(long)i*c.semiIntervalTicks(),c.semiDamage()));
                }
            }
            case BURST->{for(int i=-1;i<=1;i++)launch(p,Vec3.directionFromRotation(p.getXRot(),p.getYRot()+(float)(i*c.burstAngle())),c.burstDamage(),0);}
            case RICOCHET->launch(p,p.getLookAngle(),c.ricochetDamage(1),1);
        }
        p.level().playSound(null,p.blockPosition(),SoundEvents.TRIDENT_THROW,SoundSource.PLAYERS,.7f,1.7f);
        if(ammo-c.ammoPerThrow()<c.ammoPerThrow())reload(p);
        p.inventoryMenu.broadcastChanges();
    }
    /** {@code hop}: 0 for semi/burst, 1 for the first ricochet throw. */
    private static void launch(Player p,Vec3 direction,double damage,int hop){
        var c=config();var shuriken=GadgetItems.SHURIKEN_PROJECTILE.get().create(p.level());if(shuriken==null)return;
        shuriken.setOwner(p);shuriken.setItem(new ItemStack(GadgetItems.SHURIKEN.get()));
        var eye=p.getEyePosition();shuriken.setPos(eye.x,eye.y-.1,eye.z);
        shuriken.configure(damage,hop,ExtraGadgetConfig.ticks(c.flightSeconds()));
        shuriken.shoot(direction.x,direction.y,direction.z,(float)c.projectileSpeed(),(float)c.spreadDegrees());
        p.level().addFreshEntity(shuriken);
    }
    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||PENDING.isEmpty())return;
        var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(server==null){PENDING.clear();return;}
        for(var it=PENDING.iterator();it.hasNext();){
            var pending=it.next();var p=server.getPlayerList().getPlayer(pending.player());
            if(p==null){it.remove();continue;}
            if(p.level().getGameTime()<pending.due())continue;
            it.remove();
            if(!usable(p)||is(p.getMainHandItem())&&reloadEnd(p.getMainHandItem())>0)continue;
            launch(p,p.getLookAngle(),pending.damage(),0);
            p.level().playSound(null,p.blockPosition(),SoundEvents.TRIDENT_THROW,SoundSource.PLAYERS,.5f,1.9f);
        }
    }
    /** Whether the player carries a shuriken (the double jump comes with the weapon, not only while it is held). */
    public static boolean carried(Player p){
        if(p.getOffhandItem().getItem() instanceof ShurikenItem)return true;
        for(var stack:p.getInventory().items)if(is(stack))return true;
        return false;
    }
    /** The client already applied the jump to its own movement; the server allows one per airtime and adds the effects. */
    public static void doubleJump(ServerPlayer p){
        var data=p.getPersistentData();
        if(!carried(p)||p.isSpectator()||!p.isAlive()||p.isPassenger()||p.getAbilities().flying||data.getBoolean(AIR_JUMPED)||LobbyManager.respawning(p.getUUID()))return;
        data.putBoolean(AIR_JUMPED,true);
        p.fallDistance=0;
        p.level().playSound(null,p.blockPosition(),SoundEvents.ENDER_DRAGON_FLAP,SoundSource.PLAYERS,.35f,1.8f);
        ((net.minecraft.server.level.ServerLevel)p.level()).sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,p.getX(),p.getY(),p.getZ(),8,.25,.05,.25,.02);
    }
    /**
     * One wall-climb step: the client applied the climb to its own movement while facing a wall; the server only paces
     * it, clears the fall (so a climb never ends in fall damage) and plays the step on the wall.
     */
    public static void climb(ServerPlayer p){
        var data=p.getPersistentData();long now=p.level().getGameTime();
        if(!carried(p)||p.isSpectator()||!p.isAlive()||p.isPassenger()||LobbyManager.respawning(p.getUUID()))return;
        if(data.contains(LAST_CLIMB)&&now-data.getLong(LAST_CLIMB)>=0&&now-data.getLong(LAST_CLIMB)<CLIMB_TICKS-1)return;
        data.putLong(LAST_CLIMB,now);
        p.fallDistance=0;
        var look=p.getLookAngle();
        var wall=net.minecraft.core.BlockPos.containing(p.getX()+Math.signum(look.x)*.6,p.getY()+.5,p.getZ()+Math.signum(look.z)*.6);
        var state=p.level().getBlockState(wall);
        var sound=state.isAir()?net.minecraft.world.level.block.SoundType.STONE:state.getSoundType();
        p.level().playSound(null,p.blockPosition(),sound.getStepSound(),SoundSource.PLAYERS,sound.getVolume()*.6f,sound.getPitch()*1.1f);
    }
    @SubscribeEvent(priority=EventPriority.LOW) public static void playerTick(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||event.player.level().isClientSide)return;
        var p=event.player;
        if((p.onGround()||p.isInWater()||p.onClimbable())&&p.getPersistentData().getBoolean(AIR_JUMPED))p.getPersistentData().remove(AIR_JUMPED);
    }
    /** Vanilla melee with a shuriken does nothing; the attack key throws instead. */
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void swing(net.minecraftforge.event.entity.player.AttackEntityEvent event){
        if(is(event.getEntity().getMainHandItem())){
            event.setCanceled(true);
            if(!event.getEntity().level().isClientSide)attack(event.getEntity());
        }
    }
    @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event){PENDING.removeIf(p->p.player().equals(event.getEntity().getUUID()));}
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event){PENDING.clear();}
    @Override public void appendHoverText(ItemStack stack,Level level,List<net.minecraft.network.chat.Component> lines,TooltipFlag flag){
        lines.add(net.minecraft.network.chat.Component.literal(mode(stack).label+" · "+ammo(stack)+"/"+capacity(stack)).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
