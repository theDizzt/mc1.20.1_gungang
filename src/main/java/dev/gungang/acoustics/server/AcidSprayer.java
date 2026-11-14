package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import java.util.*;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 36 산성가스분사기 ({@code hlm:dis}, {@link ExtraGadgetConfig.AcidSprayer}). Holding left click spends one round every
 * two ticks for a forward spray, bypassing the original gun's delayed shot. Right click
 * spends more rounds to drive a new cloud, and the clouds ahead, along the aim. Clouds hurt
 * everyone touching them, the user and allies included, and leave a short poison. The gun cannot be reloaded: rounds
 * come back on their own while it rests.
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class AcidSprayer {
    public static final String GUN_ID="hlm:dis";
    private static final String AMMO="GunCurrentAmmoCount";
    private static final DustParticleOptions GAS=new DustParticleOptions(new org.joml.Vector3f(.55f,.95f,.12f),1.8f);
    private static final DustParticleOptions GAS_DARK=new DustParticleOptions(new org.joml.Vector3f(.3f,.62f,.05f),2.4f);
    /** One acid cloud: where it is, where a push carries it (from {@code from} between the two ticks), and its end. */
    private static final class Cloud {
        final ServerLevel level;final UUID owner;final long expires;
        Vec3 position,from,to;long pushStart,pushEnd;
        Cloud(ServerLevel level,UUID owner,Vec3 position,long expires){this.level=level;this.owner=owner;this.position=position;this.expires=expires;}
    }
    private static final List<Cloud> CLOUDS=new ArrayList<>();
    /** Game tick each shooter last fired (left or right click), for the ammo regeneration delay. */
    private static final Map<UUID,Long> LAST_USE=new HashMap<>();
    /** Game tick a shooter last got a muzzle cloud: one per tick however many pellets the pack fires. */
    private static final Map<UUID,Long> LAST_CLOUD=new HashMap<>();
    public static final int SPRAY_INTERVAL=2;
    private static final Map<UUID,Long> LAST_SOUND=new HashMap<>();

    private static boolean allowed(ServerPlayer owner){
        return is(owner.getMainHandItem())&&owner.isAlive()&&!owner.isSpectator()
                &&!LobbyManager.respawning(owner.getUUID())&&!FreeForAllMatch.blocked(owner)&&!SafeZones.contains(owner);
    }
    /** Server rate limiting also prevents simultaneous mouse buttons from consuming twice in one tick. */
    private static boolean consume(ServerPlayer owner,int cost){
        long now=owner.level().getGameTime();
        if(now-LAST_USE.getOrDefault(owner.getUUID(),Long.MIN_VALUE/2)<SPRAY_INTERVAL)return false;
        var tag=owner.getMainHandItem().getOrCreateTag();int ammo=tag.getInt(AMMO);
        if(!owner.getAbilities().instabuild){
            if(ammo<cost)return false;
            tag.putInt(AMMO,ammo-cost);owner.inventoryMenu.broadcastChanges();
        }
        LAST_USE.put(owner.getUUID(),now);
        return true;
    }
    public static void spray(ServerPlayer owner){
        if(!allowed(owner)||!consume(owner,1))return;
        var config=ExtraGadgetConfig.acidSprayer;var level=owner.serverLevel();long now=level.getGameTime();
        var position=muzzle(level,owner,config.sprayDistance());
        if(position!=null){
            var cloud=new Cloud(level,owner.getUUID(),position,now+ExtraGadgetConfig.ticks(config.cloudSeconds()));
            var target=position.add(owner.getLookAngle().scale(2));
            var wall=level.clip(new ClipContext(position,target,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner));
            cloud.from=position;cloud.to=wall.getType()==HitResult.Type.MISS?target:wall.getLocation().subtract(owner.getLookAngle().scale(.3));
            if(cloud.to.subtract(position).dot(owner.getLookAngle())<0)cloud.to=position;
            cloud.pushStart=now;cloud.pushEnd=now+6;
            CLOUDS.add(cloud);
        }
        feedback(owner,false);
    }
    private static void feedback(ServerPlayer owner,boolean push){
        long now=owner.level().getGameTime();
        if(now-LAST_SOUND.getOrDefault(owner.getUUID(),Long.MIN_VALUE/2)>=12){
            sound(owner,"acid_warmup");LAST_SOUND.put(owner.getUUID(),now);
        }
        if(!owner.getAbilities().instabuild&&owner.getMainHandItem().getOrCreateTag().getInt(AMMO)==0){
            sound(owner,"acid_empty");LAST_SOUND.remove(owner.getUUID());
        }
        GungangNetwork.send(owner,new GungangNetwork.AcidShot(push));
    }
    private static void sound(ServerPlayer owner,String name){
        owner.serverLevel().playSound(null,owner.blockPosition(),SoundEvent.createVariableRangeEvent(
                new ResourceLocation(GungangAcoustics.ID,name)),SoundSource.PLAYERS,1f,1f);
    }

    public static boolean is(ItemStack stack){var tag=stack==null?null:stack.getTag();return tag!=null&&GUN_ID.equals(tag.getString("GunId"));}
    static boolean round(Entity entity){return entity instanceof Las5Damage.BulletAccess bullet&&GUN_ID.equals(String.valueOf(bullet.getGunId()));}

    /** A fired round never flies: it becomes the muzzle cloud. */
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void fired(EntityJoinLevelEvent event){
        if(event.getLevel().isClientSide()||!round(event.getEntity()))return;
        event.setCanceled(true);
        if(!(event.getEntity() instanceof Projectile round)||!(round.getOwner() instanceof LivingEntity owner)||!(event.getLevel() instanceof ServerLevel level))return;
        long now=level.getGameTime();
        LAST_USE.put(owner.getUUID(),now);
        if(Long.valueOf(now).equals(LAST_CLOUD.put(owner.getUUID(),now)))return;
        var config=ExtraGadgetConfig.acidSprayer;
        var position=muzzle(level,owner,config.sprayDistance());
        if(position!=null)CLOUDS.add(new Cloud(level,owner.getUUID(),position,now+ExtraGadgetConfig.ticks(config.cloudSeconds())));
    }
    /** Prefer clearance from the shooter, but a nearby wall may force the cloud to overlap their body. */
    private static Vec3 muzzle(ServerLevel level,LivingEntity owner,double distance){
        var eye=owner.getEyePosition();var look=owner.getLookAngle();
        double radius=ExtraGadgetConfig.acidSprayer.cloudRadius();
        distance=Math.max(.5,distance);
        while(touches(owner,eye.add(look.scale(distance)),radius+.25))distance+=.25;
        var hit=level.clip(new ClipContext(eye,eye.add(look.scale(distance)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner));
        var position=hit.getType()==HitResult.Type.MISS?eye.add(look.scale(distance)):hit.getLocation().subtract(look.scale(.3));
        return position;
    }

    /** Right click: a new cloud at the muzzle, and every cloud ahead, driven along the aim up to pushRange blocks. */
    public static void push(ServerPlayer owner){
        var stack=owner.getMainHandItem();
        if(!allowed(owner))return;
        var config=ExtraGadgetConfig.acidSprayer;var level=owner.serverLevel();long now=level.getGameTime();
        var tag=stack.getOrCreateTag();int ammo=tag.getInt(AMMO),cost=config.pushAmmoCost();
        if(!owner.getAbilities().instabuild&&ammo<cost){owner.displayClientMessage(Component.literal("산성가스 밀어내기 탄약 부족 ("+ammo+"/"+cost+")"),true);return;}
        if(!consume(owner,cost))return;
        var eye=owner.getEyePosition();var look=owner.getLookAngle().normalize();
        var position=muzzle(level,owner,config.sprayDistance());
        var fresh=position==null?null:new Cloud(level,owner.getUUID(),position,now+ExtraGadgetConfig.ticks(config.cloudSeconds()));
        if(fresh!=null)CLOUDS.add(fresh);
        int ticks=Math.max(1,ExtraGadgetConfig.ticks(config.pushSeconds()));
        for(var cloud:CLOUDS){
            if(cloud.level!=level||cloud.expires<=now)continue;
            var offset=cloud.position.subtract(eye);double along=offset.dot(look);
            // Ahead of the user and within a cloud's reach of the aim line.
            if(cloud!=fresh&&(along<0||along>config.pushRange()||offset.subtract(look.scale(along)).length()>config.cloudRadius()+1))continue;
            var target=cloud.position.add(look.scale(Math.max(0,config.pushRange()-Math.max(0,along))));
            var wall=level.clip(new ClipContext(cloud.position,target,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner));
            if(wall.getType()!=HitResult.Type.MISS)target=wall.getLocation().subtract(look.scale(.5));
            if(target.subtract(cloud.position).dot(look)<0)target=cloud.position;
            cloud.from=cloud.position;cloud.to=target;cloud.pushStart=now;cloud.pushEnd=now+ticks;
        }
        feedback(owner,true);
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||CLOUDS.isEmpty())return;
        var config=ExtraGadgetConfig.acidSprayer;
        CLOUDS.removeIf(c->c.level.getGameTime()>=c.expires||event.getServer().getLevel(c.level.dimension())!=c.level);
        int interval=Math.max(1,ExtraGadgetConfig.ticks(config.damageIntervalSeconds()));
        var hurt=new HashSet<LivingEntity>();
        int clouds=CLOUDS.size();
        for(var cloud:CLOUDS){
            var level=cloud.level;long now=level.getGameTime();
            if(cloud.to!=null){
                double t=Math.min(1,(double)(now-cloud.pushStart)/Math.max(1,cloud.pushEnd-cloud.pushStart));
                double eased=1-Math.pow(1-t,3);
                cloud.position=cloud.from.add(cloud.to.subtract(cloud.from).scale(eased));
                if(t>=1)cloud.to=null;
            }
            particles(level,cloud,config.cloudRadius(),clouds);
            if(now%interval!=0)continue;
            double r=config.cloudRadius();var c=cloud.position;
            for(var body:level.getEntitiesOfClass(LivingEntity.class,new AABB(c,c).inflate(r+1),BotEntity::participant)){
                if(!body.isAlive()||body.isSpectator()||SafeZones.contains(body)||!touches(body,c,r)||!hurt.add(body))continue;
                hurt(body,cloud,config,interval);
            }
        }
    }
    static boolean touches(LivingEntity body,Vec3 c,double r){
        var box=body.getBoundingBox();
        double dx=Math.max(Math.max(box.minX-c.x,c.x-box.maxX),0),dy=Math.max(Math.max(box.minY-c.y,c.y-box.maxY),0),dz=Math.max(Math.max(box.minZ-c.z,c.z-box.maxZ),0);
        return dx*dx+dy*dy+dz*dz<=r*r;
    }
    private static void hurt(LivingEntity body,Cloud cloud,ExtraGadgetConfig.AcidSprayer config,int interval){
        var level=cloud.level;
        Entity attacker=cloud.owner==null?null:level.getServer().getPlayerList().getPlayer(cloud.owner);
        if(attacker==null&&cloud.owner!=null)attacker=level.getEntity(cloud.owner);
        var type=level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.INDIRECT_MAGIC);
        int immunity=body.invulnerableTime;body.invulnerableTime=0;
        // Area damage: hits the user and teammates too (OrdnanceDamage passes friendly-fire protection).
        OrdnanceDamage.hurt(body,new DamageSource(type,null,attacker),(float)config.damage());
        if(body.isAlive())body.invulnerableTime=Math.min(immunity,body.invulnerableTime);
        // The poison outlasts the gas by lingerPoisonSeconds.
        int linger=ExtraGadgetConfig.ticks(config.lingerPoisonSeconds());
        if(linger>0&&body.isAlive())body.addEffect(new MobEffectInstance(MobEffects.POISON,linger+interval,0,false,true,true));
    }
    /** Gas particles all clouds together may show per emission (every 2 ticks); overlapping clouds share it. */
    static final int PARTICLE_BUDGET=72;
    /** Particles one cloud shows per emission when few clouds exist. */
    static int particlesPerCloud(double radius,int clouds){
        int own=(int)Math.max(2,Math.min(12,Math.round(radius*radius*1.3)));
        return Math.max(2,Math.min(own,PARTICLE_BUDGET/Math.max(1,clouds)));
    }
    /**
     * Two batched packets per cloud (light and dark gas) instead of one per particle: auto fire adds a cloud every
     * tick, and 60 overlapping clouds once sent about 800 particle packets per tick to everyone nearby.
     */
    private static void particles(ServerLevel level,Cloud cloud,double radius,int clouds){
        if(level.getGameTime()%2!=0)return;
        var c=cloud.position;var random=level.random;
        int count=particlesPerCloud(radius,clouds),dark=Math.max(1,count/3);
        double spread=radius*.45,vertical=radius*.27;
        level.sendParticles(GAS,c.x,c.y,c.z,count-dark,spread,vertical,spread,0);
        level.sendParticles(GAS_DARK,c.x,c.y,c.z,dark,spread,vertical,spread,0);
        if(clouds<=8&&random.nextInt(3)==0)level.sendParticles(ParticleTypes.ITEM_SLIME,c.x,c.y,c.z,2,radius*.4,.3,radius*.4,.02);
    }

    /** No reload: after regenDelaySeconds without firing, one round per regenIntervalSeconds up to the magazine. */
    @SubscribeEvent public static void regenerate(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer p))return;
        var config=ExtraGadgetConfig.acidSprayer;long now=p.level().getGameTime();
        long since=now-LAST_USE.getOrDefault(p.getUUID(),Long.MIN_VALUE/2);
        long delay=ExtraGadgetConfig.ticks(config.regenDelaySeconds()),every=Math.max(1,ExtraGadgetConfig.ticks(config.regenIntervalSeconds()));
        if(since<delay||(since-delay)%every!=0)return;
        boolean changed=false;
        for(int i=0;i<p.getInventory().getContainerSize();i++){
            var stack=p.getInventory().getItem(i);
            if(!is(stack))continue;
            var tag=stack.getOrCreateTag();int ammo=tag.getInt(AMMO);
            if(ammo<config.magazine()){tag.putInt(AMMO,ammo+1);changed=true;}
        }
        if(changed)p.inventoryMenu.broadcastChanges();
    }
    /** TaCZ's reload (R) does nothing with this gun. */
    @SuppressWarnings("unchecked") public static void registerReload(){
        try{
            var type=(Class<net.minecraftforge.eventbus.api.Event>)Class.forName("com.tacz.guns.api.event.common.GunReloadEvent");
            var item=type.getMethod("getGunItemStack");
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,false,type,event->{
                try{if(item.invoke(event) instanceof ItemStack stack&&is(stack))event.setCanceled(true);}
                catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
            });
            var shootType=(Class<net.minecraftforge.eventbus.api.Event>)Class.forName("com.tacz.guns.api.event.common.GunShootEvent");
            var shootItem=shootType.getMethod("getGunItemStack");var shooter=shootType.getMethod("getShooter");
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,false,shootType,event->{
                try{if(shooter.invoke(event) instanceof Player&&shootItem.invoke(event) instanceof ItemStack stack&&is(stack))event.setCanceled(true);}
                catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
            });
        }catch(ClassNotFoundException absent){/* Optional TaCZ. */}
        catch(ReflectiveOperationException e){throw new IllegalStateException("TaCZ reload hook unavailable",e);}
    }
    @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event){LAST_USE.remove(event.getEntity().getUUID());LAST_CLOUD.remove(event.getEntity().getUUID());LAST_SOUND.remove(event.getEntity().getUUID());}
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event){CLOUDS.clear();LAST_USE.clear();LAST_CLOUD.clear();LAST_SOUND.clear();}
    private AcidSprayer(){}
}
