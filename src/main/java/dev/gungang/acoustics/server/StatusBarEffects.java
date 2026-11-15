package dev.gungang.acoustics.server;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/** Only these five ailments have a health-bar design. */
public final class StatusBarEffects {
    public static final int BURN=1,POISON=2,BLEED=4,EXPOSURE=8,SHOCK=16;
    static int of(LivingEntity body){
        if(!body.isAlive())return 0;
        int mask=0;
        if(body.isOnFire()&&!body.fireImmune())mask|=BURN;
        if(body.hasEffect(MobEffects.POISON)||body.hasEffect(GimmickBlocks.ACID_POISON.get()))mask|=POISON;
        if(body.hasEffect(GimmickBlocks.BLEEDING.get()))mask|=BLEED;
        if(Fallout.exposed(body))mask|=EXPOSURE;
        if(ArcShock.shocked(body))mask|=SHOCK;
        return mask;
    }
    private StatusBarEffects(){}
}
