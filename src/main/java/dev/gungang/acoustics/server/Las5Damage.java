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
