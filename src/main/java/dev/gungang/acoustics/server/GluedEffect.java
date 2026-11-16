package dev.gungang.acoustics.server;

import java.util.UUID;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Standing in a 37 glue gun pool ({@link GluePoolEntity}): slowPercent slower and no jumping. Effects reach the affected
 * player's own client, which is where a player's jump happens, so the jump is stopped on both sides.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID)
public final class GluedEffect extends MobEffect {
    private static final UUID SLOW=UUID.fromString("0c3f5a8e-7b2d-4e61-9a4f-2d8b6e1c7f30");
    public GluedEffect(){
        super(MobEffectCategory.HARMFUL,0xFFFFFF);
        addAttributeModifier(Attributes.MOVEMENT_SPEED,SLOW.toString(),-.2,AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
    /** The slow follows the live setting (attribute modifiers run on the server only). */
    @Override public double getAttributeModifierValue(int amplifier,AttributeModifier modifier){
        return -ExtraGadgetConfig.glueGun.slowPercent()/100;
    }
    /** After every other jump handler (the 39 toe's higher jump included): glued bodies stay on the ground. */
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void jump(net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent event){
        var entity=event.getEntity();
        if(!entity.hasEffect(GadgetItems.GLUED.get()))return;
        var v=entity.getDeltaMovement();double x=v.x,z=v.z;
        if(entity.isSprinting()){
            // Undo the sprint-jump push as well, or hopping would still outrun the slow.
            float yaw=entity.getYRot()*((float)Math.PI/180F);
            x+=net.minecraft.util.Mth.sin(yaw)*.2;z-=net.minecraft.util.Mth.cos(yaw)*.2;
        }
        entity.setDeltaMovement(x,Math.min(0,v.y),z);
    }
}
