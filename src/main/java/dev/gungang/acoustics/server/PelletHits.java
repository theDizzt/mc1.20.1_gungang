package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Keeps multi-pellet TaCZ guns (shotguns) from multiplying the per-hit work:
 * <ul>
 * <li>TaCZ hurts its target twice per bullet, the armor-ignoring share second. When that share is 0 the second hurt
 * deals nothing but still runs every damage listener, knockback and packet; it is skipped.</li>
 * <li>TaCZ's hit message goes to the shooter and the one hit, not the whole dimension ({@code TaczHurtMessageMixin}).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class PelletHits {
    /** The bullet and target of the last damaging TaCZ hurt; the paired armor-ignoring hurt follows immediately. */
    private static Entity lastBullet;
    private static LivingEntity lastTarget;

    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void attack(LivingAttackEvent event){
        var target=event.getEntity();
        if(target.level().isClientSide)return;
        var bullet=event.getSource().getDirectEntity();
        if(!(bullet instanceof Las5Damage.BulletAccess))return;
        if(event.getAmount()>0){lastBullet=bullet;lastTarget=target;return;}
        if(bullet==lastBullet&&target==lastTarget)event.setCanceled(true);
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase==TickEvent.Phase.END){lastBullet=null;lastTarget=null;}
    }

    private static java.lang.reflect.Field channel;
    private static java.lang.reflect.Method dimension;
    private static boolean channelMissing;
    /** TaCZ's {@code NetworkHandler.sendToDimension}, narrowed to the shooter and the one hit for its hit message. */
    public static void sendHitMessage(Object message,Entity hurt,Entity shooter){
        try{
            if(channelMissing||!message.getClass().getName().equals("com.tacz.guns.network.message.event.ServerMessageGunHurt")){dimension(message,hurt);return;}
            if(channel==null)channel=Class.forName("com.tacz.guns.network.NetworkHandler").getField("CHANNEL");
            var simple=(net.minecraftforge.network.simple.SimpleChannel)channel.get(null);
            if(shooter instanceof ServerPlayer p)simple.send(PacketDistributor.PLAYER.with(()->p),message);
            if(hurt instanceof ServerPlayer p&&p!=shooter)simple.send(PacketDistributor.PLAYER.with(()->p),message);
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){
            if(!channelMissing){channelMissing=true;com.mojang.logging.LogUtils.getLogger().warn("[GunGang] TaCZ hit message narrowing unavailable; sending it dimension-wide",unavailable);}
            try{dimension(message,hurt);}catch(ReflectiveOperationException ignored){}
        }
    }
    private static void dimension(Object message,Entity entity) throws ReflectiveOperationException{
        if(dimension==null)dimension=Class.forName("com.tacz.guns.network.NetworkHandler").getMethod("sendToDimension",Object.class,Entity.class);
        dimension.invoke(null,message,entity);
    }
    private PelletHits(){}
}
