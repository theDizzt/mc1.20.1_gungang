package dev.gungang.acoustics.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * 74 Hermes' Boots: a horizontal dash along the facing direction ({@link ExtraGadgetConfig.Hermes}), spending one of
 * the stored charges ({@link SecondaryRuntime#charges}). The motion is the rapier's fixed-step dash, started here and
 * replayed by the owner's client (the body is client-controlled); unlike the rapier it touches no one.
 */
public final class HermesBoots {
    static void dash(ServerPlayer p,ItemStack stack){
        if(p.isPassenger()||p.isSleeping()||RapierDash.active(p))return;
        var config=ExtraGadgetConfig.hermes;int ticks=config.dashTicks();
        RapierDash.start(p,p.getYRot(),config.dashDistance(),ticks);
        SecondaryRuntime.commit(p,stack);
        p.fallDistance=0;
        GungangNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->p),
                new GungangNetwork.HermesDash(p.getId(),p.getYRot(),(float)config.dashDistance(),ticks,(float)config.trailSeconds()));
        p.level().playSound(null,p.blockPosition(),SoundEvents.PHANTOM_FLAP,SoundSource.PLAYERS,.8f,1.6f);
        p.level().playSound(null,p.blockPosition(),SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.PLAYERS,.6f,1.8f);
    }
    private HermesBoots(){}
}
