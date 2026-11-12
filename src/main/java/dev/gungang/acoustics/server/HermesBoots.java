package dev.gungang.acoustics.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * 74 Hermes' Boots: a horizontal dash in one of eight directions around the view, picked by double-tapping a movement
 * key (W/A/S/D, with a held one for the diagonals: {@code HermesInput} on the client), spending one of
 * the stored charges ({@link SecondaryRuntime#charges}). The motion is the rapier's fixed-step dash, started here and
 * replayed by the owner's client (the body is client-controlled); unlike the rapier it touches no one.
 */
public final class HermesBoots {
    /** Offset 360 is reserved for the G-key dash, including the view pitch. */
    /** Turns from the view a dash may take: forward 0, right 90, back 180, left -90 and the diagonals between. */
    static boolean direction(int offset){return offset%45==0&&offset>=-135&&offset<=180;}
    /** The double-tap request ({@code offset} degrees from the view yaw), checked like any other secondary use. */
    static void request(ServerPlayer p,int offset){
        if(offset!=360&&!direction(offset))return;
        var stack=CombatGadgets.is(p.getOffhandItem(),"hermes_boots")?p.getOffhandItem():CombatGadgets.is(p.getMainHandItem(),"hermes_boots")?p.getMainHandItem():ItemStack.EMPTY;
        if(stack.isEmpty())return;
        GadgetManager.dash(p,stack,offset);
    }
    static void dash(ServerPlayer p,ItemStack stack,int offset){
        if(p.isPassenger()||p.isSleeping()||RapierDash.active(p))return;
        var config=ExtraGadgetConfig.hermes;int ticks=config.dashTicks();
        float yaw=net.minecraft.util.Mth.wrapDegrees(p.getYRot()+(offset==360?0:offset));
        float pitch=offset==360?p.getXRot():0;
        RapierDash.start(p,yaw,pitch,config.dashDistance(),ticks);
        SecondaryRuntime.commit(p,stack);
        p.fallDistance=0;
        GungangNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->p),
                new GungangNetwork.HermesDash(p.getId(),yaw,pitch,(float)config.dashDistance(),ticks,(float)config.trailSeconds()));
        p.level().playSound(null,p.blockPosition(),SoundEvents.PHANTOM_FLAP,SoundSource.PLAYERS,.8f,1.6f);
        p.level().playSound(null,p.blockPosition(),SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.PLAYERS,.6f,1.8f);
    }
    private HermesBoots(){}
}
