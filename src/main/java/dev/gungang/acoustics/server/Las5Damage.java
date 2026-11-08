package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Freeze heat on the server when the shot is spawned, before weapon swaps or cooling. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class Las5Damage {
    public interface BulletAccess { ResourceLocation getGunId(); }
    public static final String SHOT_DAMAGE="GunGangLas5Damage";
    public static float damage(float heat,float maximum){
        float ratio=Float.isFinite(heat)&&Float.isFinite(maximum)&&maximum>0?heat/maximum:0;
        return 2*(1+Math.max(0,Math.min(1,ratio)));
    }
    private static boolean matches(ItemStack stack){return stack.hasTag()&&"zeta:las_5".equals(stack.getTag().getString("GunId"));}
    /** Beam reach in blocks. */
    private static final double BEAM_RANGE=96;
    /**
     * Every player near the shot sees its beam. A laser bullet is often gone before the entity tracker would send it,
     * so observers cannot rely on the bullet entity reaching their client; the server traces the hit and broadcasts it.
     */
    @SubscribeEvent public static void beam(EntityJoinLevelEvent event){
        if(event.getLevel().isClientSide||!(event.getEntity() instanceof Projectile bullet)
                ||!(bullet instanceof BulletAccess access)||!"zeta:las_5".equals(String.valueOf(access.getGunId())))return;
        var motion=bullet.getDeltaMovement();if(motion.lengthSqr()<1e-6)return;
        var owner=bullet.getOwner();var start=bullet.position();var end=start.add(motion.normalize().scale(BEAM_RANGE));
        var block=bullet.level().clip(new net.minecraft.world.level.ClipContext(start,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,bullet));
        if(block.getType()!=net.minecraft.world.phys.HitResult.Type.MISS)end=block.getLocation();
        var hit=net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(bullet.level(),bullet,start,end,bullet.getBoundingBox().expandTowards(end.subtract(start)).inflate(1),
                e->e!=owner&&e.isPickable()&&!e.isSpectator(),.3f);
        if(hit!=null)end=hit.getLocation();
        var packet=new GungangNetwork.Las5Shot(owner==null?-1:owner.getId(),start.x,start.y,start.z,end.x,end.y,end.z);
        var target=owner!=null?net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->owner)
                :net.minecraftforge.network.PacketDistributor.NEAR.with(()->new net.minecraftforge.network.PacketDistributor.TargetPoint(start.x,start.y,start.z,BEAM_RANGE,bullet.level().dimension()));
        GungangNetwork.CHANNEL.send(target,packet);
    }
    @SubscribeEvent public static void shot(EntityJoinLevelEvent event){
        if(event.getLevel().isClientSide||!(event.getEntity() instanceof Projectile bullet)
                ||!(bullet instanceof BulletAccess access)||!"zeta:las_5".equals(String.valueOf(access.getGunId()))
                ||bullet.getPersistentData().contains(SHOT_DAMAGE)||!(bullet.getOwner() instanceof LivingEntity owner))return;
        ItemStack stack=owner.getMainHandItem();if(!matches(stack))stack=owner.getOffhandItem();
        if(!matches(stack))return;
        float max=100;
        try {
            var index=(java.util.Optional<?>)Class.forName("com.tacz.guns.api.TimelessAPI")
                    .getMethod("getCommonGunIndex",ResourceLocation.class).invoke(null,access.getGunId());
            if(index.isPresent()){
                Object data=index.get().getClass().getMethod("getGunData").invoke(index.get());
                Object heat=data.getClass().getMethod("getHeatData").invoke(data);
                max=((Number)heat.getClass().getMethod("getHeatMax").invoke(heat)).floatValue();
            }
        }catch(ReflectiveOperationException|NullPointerException unsupported){
            // The shipped LAS-5 heat gauge is 0..100; older TaCZ APIs use that baseline.
        }
        bullet.getPersistentData().putFloat(SHOT_DAMAGE,damage(stack.getTag().getFloat("HeatAmount"),max));
    }
    public static float baseDamage(Projectile bullet,float original){
        return bullet.getPersistentData().contains(SHOT_DAMAGE)?bullet.getPersistentData().getFloat(SHOT_DAMAGE):original;
    }
    private Las5Damage(){}
}
