package dev.gungang.acoustics.server;

import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

/** Heal sound cues: the recovery potion's start / loop / end, and the one-shot start for packs and defibrillators. */
public final class HealCue {
    /** heal_start is 0.7 s; the 2 s loop starts after it and repeats while at least 1.5 s of healing remain. */
    private static final int LOOP_AT=14,LOOP=40,LOOP_MIN_LEFT=30;

    /** One tick of an over-time heal: {@code elapsed} ticks already run, {@code left} ticks to go (this one included). */
    public static void potion(LivingEntity body,int elapsed,int left){
        if(!(body.level() instanceof ServerLevel level))return;
        if(elapsed==0){play(level,body,TurretSounds.HEAL_START.get());mark(body,left+GHOST_TICKS);}
        else if(elapsed>=LOOP_AT&&(elapsed-LOOP_AT)%LOOP==0&&left>=LOOP_MIN_LEFT)play(level,body,TurretSounds.HEAL_LOOP.get());
        if(left==1)play(level,body,TurretSounds.HEAL_END.get());
        GadgetAura.heal(body,elapsed);
    }
    /** An over-time heal cut short (death, reset): silence its loop for listeners nearby. */
    public static void interrupted(LivingEntity body){
        if(!(body.level() instanceof ServerLevel level))return;
        var packet=new ClientboundStopSoundPacket(TurretSounds.HEAL_LOOP.getId(),SoundSource.PLAYERS);
        for(var player:level.players())if(player.distanceToSqr(body)<32*32)player.connection.send(packet);
        mark(body,0);
    }
    /** Instant heals (health packs, defibrillator): heard only by the player who receives them. */
    public static void received(LivingEntity body){
        if(body instanceof ServerPlayer player)player.playNotifySound(TurretSounds.HEAL_START.get(),SoundSource.PLAYERS,1,1);
    }
    /** Long enough for the bar's 0.6 s heal fill to finish behind the green ghost. */
    static final int GHOST_TICKS=20;
    /**
     * Heal pack or recovery potion healing {@code body} for {@code ticks}: health bars draw the restored share green.
     * Sent at once, ahead of the health sync, so the ghost appears with the heal. Other heals (regeneration) send nothing.
     */
    public static void mark(LivingEntity body,int ticks){
        if(!(body.level() instanceof ServerLevel level))return;
        var packet=new GungangNetwork.HealMark(body.getId(),ticks);
        for(var player:level.players())if(GungangNetwork.installed(player))GungangNetwork.send(player,packet);
    }
    /** Combat stimulant injected: heard around the user, on the player volume slider like the heal cues. */
    public static void stim(LivingEntity body){
        if(body.level() instanceof ServerLevel level)play(level,body,TurretSounds.STIM_START.get());
    }
    private static void play(ServerLevel level,LivingEntity body,net.minecraft.sounds.SoundEvent sound){
        level.playSound(null,body,sound,SoundSource.PLAYERS,.8f,1);
    }
    private HealCue(){}
}
