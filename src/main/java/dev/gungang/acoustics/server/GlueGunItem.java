package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Primary 37 glue gun. The magazine and reload live in the stack's tag (synced to the holder for the HUD); every shot is
 * server-authoritative ({@code glueFire} action while the attack key is held, {@code glueReload} from TaCZ's reload key,
 * see {@code dev.gungang.acoustics.client.GlueGunInput}). Right click only zooms, on the client. Rounds:
 * {@link GlueShotEntity}; pools: {@link GluePoolEntity}. Settings: {@link ExtraGadgetConfig.GlueGun}.
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class GlueGunItem extends Item {
    public static final String AMMO="GlueAmmo",CAPACITY="GlueCapacity",RELOAD_END="GlueReloadEnd",RELOAD_TICKS="GlueReloadTicks",
            INTERVAL="GlueInterval",ZOOM="GlueZoom";
    private static final String LAST_SHOT="GlueLastShot";
    /** Network jitter the server forgives between two shots. */
    private static final int SHOT_JITTER_TICKS=1;

    public GlueGunItem(){super(new Properties().stacksTo(1));}
    /** First person: hip pose, centred while zoomed, recoil on every shot ({@code GlueGunHand}). */
    @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer){
        consumer.accept(new dev.gungang.acoustics.client.GlueGunHand());
    }
    public static boolean is(ItemStack stack){return stack!=null&&stack.getItem() instanceof GlueGunItem;}
    private static ExtraGadgetConfig.GlueGun config(){return ExtraGadgetConfig.glueGun;}
    /** Ammo and reload updates must not lower and re-raise the held gun. */
    @Override public boolean shouldCauseReequipAnimation(ItemStack oldStack,ItemStack newStack,boolean slotChanged){return slotChanged||oldStack.getItem()!=newStack.getItem();}
    @Override public boolean canAttackBlock(net.minecraft.world.level.block.state.BlockState state,Level level,net.minecraft.core.BlockPos pos,Player player){return false;}
    @Override public boolean onEntitySwing(ItemStack stack,net.minecraft.world.entity.LivingEntity entity){return true;}
    /** Right click is the zoom (client only): no use animation, no block interaction. */
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){return InteractionResultHolder.pass(player.getItemInHand(hand));}

    public static int ammo(ItemStack stack){return stack.hasTag()&&stack.getTag().contains(AMMO)?Math.max(0,stack.getTag().getInt(AMMO)):capacity(stack);}
    /** The magazine size the server last synced to this stack (HUD). */
    public static int capacity(ItemStack stack){return stack.hasTag()&&stack.getTag().contains(CAPACITY)?Math.max(1,stack.getTag().getInt(CAPACITY)):ExtraGadgetConfig.GLUE_GUN_DEFAULTS.magazine();}
    /** Game time the running reload ends (0: not reloading). */
    public static long reloadEnd(ItemStack stack){return stack.hasTag()?stack.getTag().getLong(RELOAD_END):0;}
    public static int reloadTicks(ItemStack stack){return stack.hasTag()?Math.max(1,stack.getTag().getInt(RELOAD_TICKS)):1;}
    /** Ticks between shots as last synced (client input pacing). */
    public static int shotTicks(ItemStack stack){return stack.hasTag()&&stack.getTag().contains(INTERVAL)?Math.max(1,stack.getTag().getInt(INTERVAL)):interval(ExtraGadgetConfig.GLUE_GUN_DEFAULTS);}
    /** Field of view multiplier while zoomed, as last synced. */
    public static double zoom(ItemStack stack){return stack.hasTag()&&stack.getTag().contains(ZOOM)?stack.getTag().getDouble(ZOOM):ExtraGadgetConfig.GLUE_GUN_DEFAULTS.zoomFovMultiplier();}
    /** Ticks between shots: 1200 / RPM, in whole ticks (171 RPM -> 7 ticks). */
    private static int interval(ExtraGadgetConfig.GlueGun c){return Math.max(1,(int)Math.round(1200/c.roundsPerMinute()));}

    @Override public void inventoryTick(ItemStack stack,Level level,net.minecraft.world.entity.Entity entity,int slot,boolean selected){
        if(level.isClientSide||!(entity instanceof Player p))return;
        var c=config();var tag=stack.getOrCreateTag();
        if(tag.getInt(CAPACITY)!=c.magazine())tag.putInt(CAPACITY,c.magazine());
        if(!tag.contains(AMMO)||tag.getInt(AMMO)>c.magazine())tag.putInt(AMMO,c.magazine());
        if(tag.getInt(INTERVAL)!=interval(c))tag.putInt(INTERVAL,interval(c));
        if(!tag.contains(ZOOM)||tag.getDouble(ZOOM)!=c.zoomFovMultiplier())tag.putDouble(ZOOM,c.zoomFovMultiplier());
        long end=tag.getLong(RELOAD_END);
        if(end>0){
            // Like a TaCZ gun, putting it away cancels the reload.
            if(p.getMainHandItem()!=stack)tag.remove(RELOAD_END);
            else if(level.getGameTime()>=end){
                tag.remove(RELOAD_END);tag.putInt(AMMO,c.magazine());
                level.playSound(null,p.blockPosition(),SoundEvents.HONEY_BLOCK_PLACE,SoundSource.PLAYERS,.9f,1.3f);
            }
        }
    }
    private static boolean usable(Player p){
        return is(p.getMainHandItem())&&!p.isSpectator()&&p.isAlive()&&!LobbyManager.respawning(p.getUUID())
                &&!FreeForAllMatch.blocked(p)&&!SafeZones.contains(p)&&!CombatGadgets.holdingShield(p)&&!SurvivalGadgets.jetting(p)&&!GadgetManager.placing(p);
    }
    /** Swaps the glue stick when the magazine is not full (TaCZ reload key, or a shot on an empty magazine). */
    public static void reload(Player p){
        var stack=p.getMainHandItem();
        if(p.level().isClientSide||!is(stack)||!usable(p))return;
        var c=config();var tag=stack.getOrCreateTag();
        if(tag.getLong(RELOAD_END)>0||ammo(stack)>=c.magazine())return;
        int ticks=Math.max(1,ExtraGadgetConfig.ticks(c.reloadSeconds()));
        tag.putLong(RELOAD_END,p.level().getGameTime()+ticks);tag.putInt(RELOAD_TICKS,ticks);
        p.level().playSound(null,p.blockPosition(),SoundEvents.ARMOR_EQUIP_LEATHER,SoundSource.PLAYERS,.8f,1.1f);
        p.inventoryMenu.broadcastChanges();
    }
    /**
     * One shot while the attack key is held: one round, one glue round toward the crosshair, within spreadDegrees of it
     * ({@code zoomed}, reported by the client: zoomSpreadDegrees).
     */
    public static void fire(Player p,boolean zoomed){
        var stack=p.getMainHandItem();
        if(p.level().isClientSide||!is(stack)||!usable(p))return;
        var c=config();var tag=stack.getOrCreateTag();
        if(tag.getLong(RELOAD_END)>0)return;
        long now=p.level().getGameTime();var data=p.getPersistentData();
        if(data.contains(LAST_SHOT)&&now-data.getLong(LAST_SHOT)>=0&&now-data.getLong(LAST_SHOT)<Math.max(1,interval(c)-SHOT_JITTER_TICKS))return;
        int ammo=ammo(stack);
        boolean creative=p.getAbilities().instabuild;
        if(ammo<1&&!creative){reload(p);return;}
        data.putLong(LAST_SHOT,now);
        if(!creative)tag.putInt(AMMO,ammo-1);
        var shot=GadgetItems.GLUE_SHOT.get().create(p.level());
        if(shot!=null){
            shot.setOwner(p);shot.setItem(new ItemStack(net.minecraft.world.item.Items.SNOWBALL));
            var eye=p.getEyePosition();shot.setPos(eye.x,eye.y-.1,eye.z);
            shot.configure(c.directDamage(),(float)c.projectileGravity(),c.range());
            var look=spread(p,zoomed?c.zoomSpreadDegrees():c.spreadDegrees());
            shot.shoot(look.x,look.y,look.z,(float)c.projectileSpeed(),0);
            p.level().addFreshEntity(shot);
        }
        p.level().playSound(null,p.blockPosition(),SoundEvents.SLIME_SQUISH_SMALL,SoundSource.PLAYERS,.9f,1.5f);
        p.level().playSound(null,p.blockPosition(),SoundEvents.DISPENSER_LAUNCH,SoundSource.PLAYERS,.35f,1.8f);
        if(ammo-1<1&&!creative)reload(p);
        p.inventoryMenu.broadcastChanges();
    }
    /** The aim turned by up to {@code degrees}, evenly over the cone (not clustered at its centre). */
    private static net.minecraft.world.phys.Vec3 spread(Player p,double degrees){
        if(degrees<=0)return p.getLookAngle();
        var random=p.getRandom();double angle=degrees*Math.sqrt(random.nextDouble()),around=random.nextDouble()*Math.PI*2;
        return net.minecraft.world.phys.Vec3.directionFromRotation((float)(p.getXRot()+angle*Math.sin(around)),(float)(p.getYRot()+angle*Math.cos(around)));
    }
    /** Vanilla melee with the glue gun does nothing; the attack key fires instead (client input). */
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void swing(net.minecraftforge.event.entity.player.AttackEntityEvent event){
        if(is(event.getEntity().getMainHandItem()))event.setCanceled(true);
    }
    /** No block interaction (doors, buttons) on the zoom button while holding it. */
    @SubscribeEvent public static void useBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event){
        if(is(event.getItemStack())&&event.getHand()==InteractionHand.MAIN_HAND)event.setCanceled(true);
    }
    @Override public void appendHoverText(ItemStack stack,Level level,java.util.List<net.minecraft.network.chat.Component> lines,TooltipFlag flag){
        lines.add(net.minecraft.network.chat.Component.literal(ammo(stack)+"/"+capacity(stack)).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
