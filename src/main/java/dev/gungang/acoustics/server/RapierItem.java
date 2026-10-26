package dev.gungang.acoustics.server;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/** Server-authoritative crosshair thrust and player-owned backstep energy. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID)
public final class RapierItem extends Item {
    public static final double RANGE=4.5;
    private static final String ENERGY="RapierEnergy",WAIT="RapierRecovery",ATTACK="RapierAttackTick";
    private final Multimap<Attribute,AttributeModifier> attributes=ImmutableMultimap.<Attribute,AttributeModifier>builder()
            .put(Attributes.ATTACK_DAMAGE,new AttributeModifier(BASE_ATTACK_DAMAGE_UUID,"Rapier damage",32,AttributeModifier.Operation.ADDITION))
            .put(Attributes.ATTACK_SPEED,new AttributeModifier(BASE_ATTACK_SPEED_UUID,"Rapier speed",-2.0/3,AttributeModifier.Operation.ADDITION))
            .put(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get(),new AttributeModifier(java.util.UUID.fromString("ecdf4756-1d78-45af-9792-098cbb4c5f28"),"Rapier reach",1.5,AttributeModifier.Operation.ADDITION)).build();
    public RapierItem(){super(new Properties().stacksTo(1));}
    /** Gauge synchronization must not lower/re-equip the blade every four ticks. */
    @Override public boolean shouldCauseReequipAnimation(ItemStack oldStack,ItemStack newStack,boolean slotChanged){return slotChanged||oldStack.getItem()!=newStack.getItem();}
    @Override public Multimap<Attribute,AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot){return slot==EquipmentSlot.MAINHAND?attributes:super.getDefaultAttributeModifiers(slot);}
    @Override public boolean canAttackBlock(net.minecraft.world.level.block.state.BlockState state,Level level,net.minecraft.core.BlockPos pos,Player player){return false;}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        var stack=player.getItemInHand(hand);
        if(hand!=InteractionHand.MAIN_HAND||player.getMainHandItem().getItem()!=this||stack.getItem()!=this||player.isSpectator()||!player.isAlive()||!player.onGround()||player.isPassenger()
                ||LobbyManager.respawning(player.getUUID())||(!level.isClientSide&&!energy(player).canStep()))return InteractionResultHolder.fail(stack);
        if(!level.isClientSide){
            // Velocity uses vanilla collision resolution, so the player cannot teleport through walls.
            double angle=Math.toRadians(player.getYRot());
            player.setDeltaMovement(-Math.sin(angle)*1.1,Math.max(0,player.getDeltaMovement().y),Math.cos(angle)*1.1);
            player.hurtMarked=true;save(player,energy(player).spend());sync(player);
            level.playSound(null,player.blockPosition(),net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP,net.minecraft.sounds.SoundSource.PLAYERS,.55f,1.5f);
        }
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
    private static RapierEnergy energy(Player p){var tag=p.getPersistentData();return new RapierEnergy(tag.contains(ENERGY)?tag.getInt(ENERGY):100,tag.getInt(WAIT));}
    private static void save(Player p,RapierEnergy energy){p.getPersistentData().putInt(ENERGY,energy.amount());p.getPersistentData().putInt(WAIT,energy.waitTicks());}
    public static int gauge(ItemStack stack){return stack.hasTag()&&stack.getTag().contains(ENERGY)?Math.max(0,Math.min(100,stack.getTag().getInt(ENERGY))):100;}
    private static void sync(Player p){
        int amount=energy(p).amount();boolean changed=false;
        for(int i=0;i<p.getInventory().getContainerSize();i++){var stack=p.getInventory().getItem(i);
            if(stack.getItem() instanceof RapierItem&&(!stack.hasTag()||!stack.getTag().contains(ENERGY)||gauge(stack)!=amount)){stack.getOrCreateTag().putInt(ENERGY,amount);changed=true;}
        }
        if(changed)p.inventoryMenu.broadcastChanges();
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.PlayerTickEvent event){
        var p=event.player;if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END||p.level().isClientSide)return;
        if(p.getPersistentData().contains(ENERGY))save(p,energy(p).tick());sync(p);
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void attack(net.minecraftforge.event.entity.player.AttackEntityEvent event){
        var p=event.getEntity();if(!(p.getMainHandItem().getItem() instanceof RapierItem))return;
        event.setCanceled(true);
        if(p.level().isClientSide||p.isSpectator()||!p.isAlive()||LobbyManager.respawning(p.getUUID())||SafeZones.contains(p)||SafeZones.contains(event.getTarget()))return;
        if(!(event.getTarget() instanceof net.minecraft.world.entity.LivingEntity target)||!target.isAlive())return;
        if(!beginAttack(p))return;
        var eye=p.getEyePosition();var end=eye.add(p.getLookAngle().scale(RANGE));
        var hit=target.getBoundingBox().clip(eye,end);if(hit.isEmpty())return;
        var block=p.level().clip(new net.minecraft.world.level.ClipContext(eye,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
        if(block.getType()!=net.minecraft.world.phys.HitResult.Type.MISS&&eye.distanceToSqr(block.getLocation())<eye.distanceToSqr(hit.get()))return;
        if(target instanceof Player victim&&!p.canHarmPlayer(victim))return;
        boolean head=hit.get().y>=target.getEyeY()-.2;
        int immunity=target.invulnerableTime;target.invulnerableTime=0;
        if(!target.hurt(p.damageSources().playerAttack(p),head?55:33))target.invulnerableTime=immunity;
        else p.level().playSound(null,target.blockPosition(),net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_STRONG,net.minecraft.sounds.SoundSource.PLAYERS,.65f,1.35f);
        p.resetAttackStrengthTicker();
    }
    static boolean beginAttack(Player p){
        long now=p.level().getGameTime();var tag=p.getPersistentData();
        if(tag.contains(ATTACK)&&now-tag.getLong(ATTACK)>=0&&now-tag.getLong(ATTACK)<6)return false;
        tag.putLong(ATTACK,now);return true;
    }
    @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer){
        consumer.accept(new dev.gungang.acoustics.client.RapierAnimation());
    }
}
