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

/** Server-authoritative crosshair thrust and player-owned dash energy. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID)
public final class RapierItem extends Item {
    public static final double RANGE=4.5;
    public static final int DASH_TICKS=6;
    private static final String DASH="RapierDashUntil";
    @net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void dodge(net.minecraftforge.event.entity.living.LivingAttackEvent event){
        var p=event.getEntity();long left=p.getPersistentData().getLong(DASH)-p.level().getGameTime();
        if(p instanceof Player&&left>0&&left<=DASH_TICKS&&!event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY))event.setCanceled(true);
    }
    @Override public boolean onEntitySwing(ItemStack stack,net.minecraft.world.entity.LivingEntity entity){
        if(!entity.level().isClientSide){
            long now=entity.level().getGameTime();var tag=entity.getPersistentData();
            if(!tag.contains("RapierSoundTick")||now-tag.getLong("RapierSoundTick")>=6){
                tag.putLong("RapierSoundTick",now);
                entity.level().playSound(null,entity.blockPosition(),net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP,net.minecraft.sounds.SoundSource.PLAYERS,.8f,1.35f);
            }
        }
        return false;
    }
    private static final String ENERGY="RapierEnergy",WAIT="RapierRecovery",ATTACK="RapierAttackTick";
    private final Multimap<Attribute,AttributeModifier> attributes=ImmutableMultimap.<Attribute,AttributeModifier>builder()
            .put(Attributes.ATTACK_DAMAGE,new AttributeModifier(BASE_ATTACK_DAMAGE_UUID,"Rapier damage",20,AttributeModifier.Operation.ADDITION))
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
                ||player.getCooldowns().isOnCooldown(this)||LobbyManager.respawning(player.getUUID())||(!level.isClientSide&&!energy(player).canStep()))return InteractionResultHolder.fail(stack);
        if(!level.isClientSide){
            // Velocity uses vanilla collision resolution, so the player cannot teleport through walls.
            double angle=Math.toRadians(player.getYRot());
            player.setDeltaMovement(-Math.sin(angle)*1.1,Math.max(0,player.getDeltaMovement().y),Math.cos(angle)*1.1);
            player.hurtMarked=true;save(player,energy(player).spend());sync(player);
            player.getPersistentData().putLong(DASH,level.getGameTime()+DASH_TICKS);
            player.getCooldowns().addCooldown(this,DASH_TICKS);
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
        if(!(p.getMainHandItem().getItem() instanceof RapierItem)||!p.isAlive())resetCombo(p);
        if(p.getPersistentData().contains(ENERGY))save(p,energy(p).tick());sync(p);
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void attack(net.minecraftforge.event.entity.player.AttackEntityEvent event){
        var p=event.getEntity();if(!(p.getMainHandItem().getItem() instanceof RapierItem))return;
        event.setCanceled(true);
        if(!p.level().isClientSide)thrust(p);
    }
    /** One authoritative ray for every click, including clicks that the client considers empty space. */
    public static void thrust(Player p){
        if(p.level().isClientSide||!(p.getMainHandItem().getItem() instanceof RapierItem)||p.isSpectator()||!p.isAlive()
                ||LobbyManager.respawning(p.getUUID())||FreeForAllMatch.blocked(p)||SafeZones.contains(p)
                ||CombatGadgets.holdingShield(p)||SurvivalGadgets.jetting(p)||GadgetManager.placing(p))return;
        if(!beginAttack(p))return;
        var eye=p.getEyePosition();var end=eye.add(p.getLookAngle().scale(RANGE));
        var block=p.level().clip(new net.minecraft.world.level.ClipContext(eye,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
        if(block.getType()!=net.minecraft.world.phys.HitResult.Type.MISS)end=block.getLocation();
        net.minecraft.world.entity.Entity hit=null;
        double nearest=eye.distanceToSqr(end);
        for(var candidate:p.level().getEntities(p,p.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1),e->e.isPickable()&&!e.isSpectator())){
            var box=candidate.getBoundingBox();
            var point=box.contains(eye)?java.util.Optional.of(eye):box.clip(eye,end);
            if(point.isPresent()&&eye.distanceToSqr(point.get())<nearest){nearest=eye.distanceToSqr(point.get());hit=candidate;}
        }
        int damage=comboDamage(p);
        p.resetAttackStrengthTicker();
        if(hit==null){
            // Scenery cannot build a combat combo.
            resetCombo(p);
            if(block.getType()!=net.minecraft.world.phys.HitResult.Type.MISS)GimmickEntity.hit(p.level(),block.getBlockPos(),(float)(21*AdrenalineDamage.multiplier(p)));
            return;
        }
        if(!(hit instanceof net.minecraft.world.entity.LivingEntity target)||!target.isAlive()||SafeZones.contains(target)
                ||target instanceof Player victim&&!p.canHarmPlayer(victim)){resetCombo(p);return;}
        int immunity=target.invulnerableTime;target.invulnerableTime=0;
        if(!target.hurt(p.damageSources().playerAttack(p),damage)){target.invulnerableTime=immunity;resetCombo(p);}
        else {
            p.getPersistentData().putInt("RapierComboDamage",Math.min(36,damage+3));
            p.getPersistentData().putLong("RapierComboHit",p.level().getGameTime());
            p.level().playSound(null,target.blockPosition(),net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_STRONG,net.minecraft.sounds.SoundSource.PLAYERS,.65f,1.35f);
        }
    }
    static int comboDamage(Player p){
        var tag=p.getPersistentData();long elapsed=p.level().getGameTime()-tag.getLong("RapierComboHit");
        return tag.contains("RapierComboHit")&&elapsed>=0&&elapsed<=20?Math.max(21,Math.min(36,tag.getInt("RapierComboDamage"))):21;
    }
    private static void resetCombo(Player p){p.getPersistentData().remove("RapierComboHit");p.getPersistentData().remove("RapierComboDamage");}
    static boolean beginAttack(Player p){
        long now=p.level().getGameTime();var tag=p.getPersistentData();
        if(tag.contains(ATTACK)&&now-tag.getLong(ATTACK)>=0&&now-tag.getLong(ATTACK)<6)return false;
        tag.putLong(ATTACK,now);return true;
    }
    @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer){
        consumer.accept(new dev.gungang.acoustics.client.RapierAnimation());
    }
}
