package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.GungangNetwork;
import dev.gungang.acoustics.server.ShurikenEntity;
import dev.gungang.acoustics.server.TurretSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Server-confirmed impact: visual removal and audio are processed in the same client task. */
public final class ShurikenImpactFx {
    public static void receive(GungangNetwork.ShurikenImpact packet){
        var mc=Minecraft.getInstance();
        if(mc.level==null)return;
        if(mc.level.getEntity(packet.entity()) instanceof ShurikenEntity shuriken){
            shuriken.trail.clear();
            shuriken.discard();
        }
        // No distance-based audio delay; do not predict hits from client-side projectile collisions.
        mc.level.playLocalSound(packet.x(),packet.y(),packet.z(),packet.block()?SoundEvents.TRIDENT_HIT_GROUND:SoundEvents.TRIDENT_HIT,
                SoundSource.PLAYERS,.6f,packet.block()?1.5f:1.6f,false);
        if(packet.confirmed())mc.getSoundManager().play(SimpleSoundInstance.forUI(TurretSounds.SHURIKEN_HIT.get(),1,1));
    }
    private ShurikenImpactFx(){}
}
