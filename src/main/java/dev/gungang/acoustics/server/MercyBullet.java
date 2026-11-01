package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Secondary 78 (자비의 총알): one SEMI round, replaced when the 30 s cooldown ends; no reloading or fire-mode change.
 * It fires only at a teammate in the crosshair, its bullet passes through everything else, and a hit gives that teammate
 * up to 30 bonus (absorption) health, never above maximum health + 30. Nothing else restores the bonus. The bonus lasts
 * 15 s (whatever is left of it then disappears) and never outlives the game. Spawn gate holograms never stop the bullet.
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class MercyBullet {
    public static final String ID="78",BULLET="GunGangMercy";
    static final float BONUS=30;
    static final double RANGE=128;
    static final int SHIELD_TICKS=15*20;
    /** Who carries a mercy bonus, until when (game time). */
    private record Shield(LivingEntity target,long end){}
    private static final java.util.Map<java.util.UUID,Shield> SHIELDS=new java.util.HashMap<>();
    /** A mercy bullet in flight; spawn gates let it through like a teammate (SpawnGateBlock). */
    public static boolean bullet(Entity entity){return entity!=null&&entity.getPersistentData().getBoolean(BULLET);}
    public static boolean is(ItemStack stack){return SecondaryRuntime.secondary(stack)&&ID.equals(stack.getTag().getString("GunGangSecondaryId"));}
    /** Called by SecondaryRuntime.configure once the cooldown deadline is on the stack. */
    static void configure(Player p,ItemStack stack){
        var tag=stack.getOrCreateTag();
        tag.putString("GunFireMode","SEMI");tag.putBoolean("OverHeated",false);tag.putFloat("HeatAmount",0);
        tag.putInt("GunCurrentAmmoCount",0);tag.putBoolean("HasBulletInBarrel",SecondaryRuntime.remaining(p,stack)==0);
    }
    /** A living, non-spectating player or bot on the shooter's scoreboard team; never the shooter. */
    public static boolean ally(Entity shooter,Entity target){
        return shooter instanceof Player a&&target!=a&&BotEntity.participant(target)&&a.getTeam()!=null&&a.isAlliedTo(target);
    }
    /** The nearest teammate (player or bot) under the crosshair; other entities never block the aim, walls do. */
    public static LivingEntity target(Player p){
        var start=p.getEyePosition();var end=start.add(p.getLookAngle().scale(RANGE));
        var wall=p.level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        if(wall.getType()!=HitResult.Type.MISS)end=wall.getLocation();
        LivingEntity best=null;double distance=Double.MAX_VALUE;
        for(var other:p.level().getEntitiesOfClass(LivingEntity.class,new net.minecraft.world.phys.AABB(start,end).inflate(1),e->ally(p,e))){
            var hit=other.getBoundingBox().inflate(.3).clip(start,end);
            if(hit.isPresent()&&hit.get().distanceToSqr(start)<distance){distance=hit.get().distanceToSqr(start);best=other;}
        }
        return best;
    }
    /** Read by TaczNpcPassMixin on both sides: a mercy bullet flies through anything but a teammate. */
    public static boolean passes(Entity bullet,Entity target){
        return bullet.getPersistentData().getBoolean(BULLET)&&!(bullet instanceof Projectile projectile&&ally(projectile.getOwner(),target));
    }
    static void grant(ServerPlayer shooter,LivingEntity target){
        float before=target.getAbsorptionAmount();
        float bonus=Math.max(before,Math.min(BONUS,target.getMaxHealth()+BONUS-target.getHealth()));
        if(bonus>before)target.setAbsorptionAmount(bonus);
        SHIELDS.put(target.getUUID(),new Shield(target,target.level().getGameTime()+SHIELD_TICKS));
        var level=shooter.serverLevel();
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART,target.getX(),target.getY()+target.getBbHeight()+.3,target.getZ(),5,.3,.2,.3,0);
        level.playSound(null,target.blockPosition(),net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,net.minecraft.sounds.SoundSource.PLAYERS,1.2f,1.4f);
        if(target instanceof Player player)player.displayClientMessage(Component.literal(shooter.getGameProfile().getName()+"의 자비의 총알 · 추가 체력 "+Math.round(bonus)),true);
        shooter.displayClientMessage(Component.literal(target.getName().getString()+"에게 추가 체력 "+Math.round(bonus)),true);
    }
    @SubscribeEvent public static void join(EntityJoinLevelEvent event){
        if(event.getEntity() instanceof Projectile bullet&&bullet.getClass().getName().equals("com.tacz.guns.entity.EntityKineticBullet")
                &&bullet.getOwner() instanceof Player p&&is(p.getMainHandItem()))bullet.getPersistentData().putBoolean(BULLET,true);
    }
    /** Expired bonuses vanish; a running life support (which also uses absorption) keeps its own. */
    @SubscribeEvent public static void expire(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||SHIELDS.isEmpty())return;
        SHIELDS.values().removeIf(s->{
            var t=s.target();if(t.isRemoved()||!t.isAlive())return true;
            if(t.level().getGameTime()<s.end())return false;
            if(!SurvivalGadgets.lifeSupport(t))t.setAbsorptionAmount(0);
            return true;
        });
    }
    /** The game ended: every mercy bonus goes with it. */
    static void reset(){
        for(var s:SHIELDS.values())if(!s.target().isRemoved()&&!SurvivalGadgets.lifeSupport(s.target()))s.target().setAbsorptionAmount(0);
        SHIELDS.clear();
    }
    /** The single round comes back when the cooldown ends, even while holstered. */
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer p)||p.tickCount%5!=0)return;
        var inventory=p.getInventory();boolean changed=false;
        for(int i=0;i<inventory.getContainerSize();i++){
            var stack=inventory.getItem(i);if(!is(stack))continue;
            var tag=stack.getTag();boolean ready=SecondaryRuntime.remaining(p,stack)==0;
            if(tag.getInt("GunCurrentAmmoCount")!=0||tag.getBoolean("HasBulletInBarrel")!=ready||!"SEMI".equals(tag.getString("GunFireMode"))){
                tag.putInt("GunCurrentAmmoCount",0);tag.putBoolean("HasBulletInBarrel",ready);tag.putString("GunFireMode","SEMI");changed=true;
            }
        }
        if(changed)p.inventoryMenu.broadcastChanges();
    }
    /** Fire-mode switching and reloading are refused; a hit never deals damage. */
    @SuppressWarnings("unchecked") static void registerTacz(){
        try{
            for(String name:java.util.List.of("GunFireSelectEvent","GunReloadEvent")){
                var type=(Class<net.minecraftforge.eventbus.api.Event>)Class.forName("com.tacz.guns.api.event.common."+name);var item=type.getMethod("getGunItemStack");
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST,false,type,event->{
                    try{if(item.invoke(event) instanceof ItemStack stack&&is(stack))event.setCanceled(true);}
                    catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
                });
            }
            var type=(Class<net.minecraftforge.eventbus.api.Event>)Class.forName("com.tacz.guns.api.event.common.EntityHurtByGunEvent$Pre");
            var bullet=type.getMethod("getBullet");var hurt=type.getMethod("getHurtEntity");var side=type.getMethod("getLogicalSide");
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST,false,type,event->{
                try{
                    if(!(bullet.invoke(event) instanceof Projectile shot)||!shot.getPersistentData().getBoolean(BULLET))return;
                    event.setCanceled(true);
                    if(side.invoke(event)==net.minecraftforge.fml.LogicalSide.SERVER&&shot.getOwner() instanceof ServerPlayer shooter&&hurt.invoke(event) instanceof LivingEntity target&&ally(shooter,target))grant(shooter,target);
                }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
            });
        }catch(ClassNotFoundException absent){/* Optional TaCZ. */}
        catch(ReflectiveOperationException e){throw new IllegalStateException("TaCZ mercy bullet hooks unavailable",e);}
    }
    private MercyBullet(){}
}
