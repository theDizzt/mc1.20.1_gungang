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
    public static final String SWEEP_ANIMATION="RapierSweepTick";
    public static final int SWEEP_ANIMATION_TICKS=10;
    private static final String DASH="RapierDashUntil";
    public static final String COMBO="RapierComboStacks",COMBO_END="RapierComboEnd";
    private static final java.util.Map<java.util.UUID,DashContact> DASHES=new java.util.HashMap<>();
    private static final class DashContact {
        net.minecraft.world.phys.Vec3 previous;final java.util.Set<java.util.UUID> hit=new java.util.HashSet<>();
        final long deadline;double travelled;
        DashContact(Player p){
            previous=p.position();
            // Accepted movement packets arrive after the S2C dash start. Bound both time and travelled distance.
            int transit=p instanceof net.minecraft.server.level.ServerPlayer sp?Math.min(20,(sp.latency+49)/50):0;
            deadline=p.level().getGameTime()+DASH_TICKS+Math.max(2,transit);
        }
    }
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
        if(hand!=InteractionHand.MAIN_HAND||player.getMainHandItem().getItem()!=this||stack.getItem()!=this||player.isSpectator()||!player.isAlive()||player.isPassenger()
                ||player.getCooldowns().isOnCooldown(this)||LobbyManager.respawning(player.getUUID())||(!level.isClientSide&&!energy(player).canStep()))return InteractionResultHolder.fail(stack);
        if(!level.isClientSide){
            RapierDash.start(player,player.getYRot());
            DASHES.put(player.getUUID(),new DashContact(player));
            if(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                GungangNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->serverPlayer),new GungangNetwork.RapierStep(player.getId(),player.getYRot()));
            save(player,energy(player).spend());
            player.getPersistentData().putLong(DASH,level.getGameTime()+DASH_TICKS);
            // Vanilla inventory/equipment synchronization also carries this to third-person observers.
            stack.getOrCreateTag().putLong(SWEEP_ANIMATION,level.getGameTime());
            sync(player);
            player.getCooldowns().addCooldown(this,DASH_TICKS);
            level.playSound(null,player.blockPosition(),net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP,net.minecraft.sounds.SoundSource.PLAYERS,.55f,1.5f);
        }
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
    private static RapierEnergy energy(Player p){var tag=p.getPersistentData();return new RapierEnergy(tag.contains(ENERGY)?tag.getInt(ENERGY):100,tag.getInt(WAIT));}
    private static void save(Player p,RapierEnergy energy){p.getPersistentData().putInt(ENERGY,energy.amount());p.getPersistentData().putInt(WAIT,energy.waitTicks());}
    public static int gauge(ItemStack stack){return stack.hasTag()&&stack.getTag().contains(ENERGY)?Math.max(0,Math.min(100,stack.getTag().getInt(ENERGY))):100;}
    private static RapierCombo combo(Player p){var tag=p.getPersistentData();return new RapierCombo(tag.getInt(COMBO),tag.getLong(COMBO_END));}
    private static void syncCombo(Player p,ItemStack stack){
        var tag=stack.getOrCreateTag();tag.putInt(COMBO,combo(p).active(p.level().getGameTime()));tag.putLong(COMBO_END,p.getPersistentData().getLong(COMBO_END));
    }
    private static void sync(Player p){
        int amount=energy(p).amount();boolean changed=false;
        for(int i=0;i<p.getInventory().getContainerSize();i++){var stack=p.getInventory().getItem(i);
            if(stack.getItem() instanceof RapierItem&&(!stack.hasTag()||!stack.getTag().contains(ENERGY)||gauge(stack)!=amount)){stack.getOrCreateTag().putInt(ENERGY,amount);changed=true;}
        }
        if(changed)p.inventoryMenu.broadcastChanges();
    }
    @Override public void inventoryTick(ItemStack stack,Level level,net.minecraft.world.entity.Entity entity,int slot,boolean selected){
        if(!level.isClientSide&&entity instanceof Player p){
            syncCombo(p,stack);
            int amount=p.getPersistentData().contains(ENERGY)?p.getPersistentData().getInt(ENERGY):100;
            if(!stack.hasTag()||!stack.getTag().contains(ENERGY)||gauge(stack)!=amount)
                stack.getOrCreateTag().putInt(ENERGY,amount);
            // Vanilla menu/equipment updates publish changed stacks, including newly acquired rapiers.
        }
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.PlayerTickEvent event){
        var p=event.player;if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END||p.level().isClientSide)return;
        if(!(p.getMainHandItem().getItem() instanceof RapierItem)||!p.isAlive())resetCombo(p);
        if(p.getPersistentData().contains(ENERGY)&&p.getPersistentData().getInt(ENERGY)<100)save(p,energy(p).tick());
        dashContacts(p);
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
        // A new thrust can interrupt the visual recovery of the blade beat.
        var animation=p.getMainHandItem().getTag();
        if(animation!=null&&animation.contains(SWEEP_ANIMATION)){
            animation.remove(SWEEP_ANIMATION);p.inventoryMenu.broadcastChanges();
        }
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
        p.resetAttackStrengthTicker();
        if(hit==null){
            // Scenery cannot build a combat combo.
            if(block.getType()!=net.minecraft.world.phys.HitResult.Type.MISS)GimmickEntity.hit(p.level(),block.getBlockPos(),(float)(21*AdrenalineDamage.multiplier(p)));
            return;
        }
        if(hit instanceof net.minecraft.world.entity.LivingEntity target)hit(p,target);
    }
    /** Shared left-click damage path, also used once per enemy touched by a dash. */
    static boolean hit(Player p,net.minecraft.world.entity.LivingEntity target){
        if(!target.isAlive()||SafeZones.contains(target)||target instanceof Player victim&&!p.canHarmPlayer(victim))return false;
        var next=combo(p).hit(p.level().getGameTime());
        int immunity=target.invulnerableTime;float before=target.getHealth()+target.getAbsorptionAmount();target.invulnerableTime=0;
        if(!target.hurt(p.damageSources().playerAttack(p),RapierCombo.damage(next.stacks()))||target.getHealth()+target.getAbsorptionAmount()>=before){target.invulnerableTime=immunity;return false;}
        else {
            p.getPersistentData().putInt(COMBO,next.stacks());p.getPersistentData().putLong(COMBO_END,next.expires());
            syncCombo(p,p.getMainHandItem());p.inventoryMenu.broadcastChanges();
            p.level().playSound(null,target.blockPosition(),net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_STRONG,net.minecraft.sounds.SoundSource.PLAYERS,.65f,1.35f);
            return true;
        }
    }
    static int comboDamage(Player p){
        return RapierCombo.damage(combo(p).hit(p.level().getGameTime()).stacks());
    }
    private static void resetCombo(Player p){p.getPersistentData().remove(COMBO);p.getPersistentData().remove(COMBO_END);}
    private static void dashContacts(Player p){
        var dash=DASHES.get(p.getUUID());if(dash==null)return;
        long now=p.level().getGameTime();
        if(now>dash.deadline||!p.isAlive()||p.isSpectator()||p.isPassenger()
                ||!(p.getMainHandItem().getItem() instanceof RapierItem)){DASHES.remove(p.getUUID());return;}
        var end=p.position();var delta=end.subtract(dash.previous);
        dash.travelled+=delta.length();
        if(dash.travelled>RapierDash.SPEED*DASH_TICKS+.1){DASHES.remove(p.getUUID());return;}
        if(delta.lengthSqr()<1e-8)return;
        if(!SafeZones.contains(p)&&!FreeForAllMatch.blocked(p)&&!LobbyManager.respawning(p.getUUID())
                &&!CombatGadgets.holdingShield(p)&&!SurvivalGadgets.jetting(p)&&!GadgetManager.placing(p)) {
            var start=dash.previous.add(0,p.getBbHeight()/2,0);var finish=end.add(0,p.getBbHeight()/2,0);
            for(var target:p.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,p.getBoundingBox().expandTowards(delta.scale(-1)).inflate(.1),e->e!=p&&e.isAlive()&&!e.isSpectator())) {
                if(dash.hit.contains(target.getUUID()))continue;
                var box=target.getBoundingBox().inflate(p.getBbWidth()/2,p.getBbHeight()/2,p.getBbWidth()/2);
                if(!box.contains(start)&&box.clip(start,finish).isEmpty())continue;
                if(!p.hasLineOfSight(target))continue;
                dash.hit.add(target.getUUID());
                if(hit(p,target)){p.getPersistentData().putLong(ATTACK,now);p.resetAttackStrengthTicker();}
            }
        }
        dash.previous=end;
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event){DASHES.remove(event.getEntity().getUUID());}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event){DASHES.clear();}
    static boolean beginAttack(Player p){
        long now=p.level().getGameTime();var tag=p.getPersistentData();
        if(tag.contains(ATTACK)&&now-tag.getLong(ATTACK)>=0&&now-tag.getLong(ATTACK)<6)return false;
        tag.putLong(ATTACK,now);return true;
    }
    @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer){
        consumer.accept(new dev.gungang.acoustics.client.RapierAnimation());
    }
}
