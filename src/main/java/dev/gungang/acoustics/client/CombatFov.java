package dev.gungang.acoustics.client;

import java.util.UUID;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Vanilla zooms the view with movement speed. The half-second melee hit slow (rapier, katana, karambit) and the twin
 * blades' guard change speed in steps, so each hit made the victim's view jump in and out (a rapier landing several
 * hits a second felt like a stutter). Their share is taken back out of the field of view; the slow itself remains.
 */
@Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID,value=Dist.CLIENT)
public final class CombatFov {
    private static final UUID[] STEADY={dev.gungang.acoustics.server.MeleeHitSlow.MODIFIER,dev.gungang.acoustics.server.TwinBlades.GUARD_SPEED};
    @SubscribeEvent(priority=EventPriority.LOW) public static void fov(ComputeFovModifierEvent event){
        var player=event.getPlayer();var speed=player.getAttribute(Attributes.MOVEMENT_SPEED);
        double walking=player.getAbilities().getWalkingSpeed();
        if(speed==null||walking<=0)return;
        double factor=1;
        for(var id:STEADY){var modifier=speed.getModifier(id);if(modifier!=null&&modifier.getAmount()>-1)factor*=1+modifier.getAmount();}
        if(factor==1)return;
        double value=speed.getValue(),without=value/factor;
        double with=(value/walking+1)/2,steady=(without/walking+1)/2;
        if(with<=1e-6)return;
        event.setNewFovModifier((float)(event.getNewFovModifier()*steady/with));
    }
    private CombatFov(){}
}
