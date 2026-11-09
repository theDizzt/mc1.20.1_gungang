package dev.gungang.acoustics.server;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;

/** Coloured auras for running consumables: a slowly turning ring of motes rising around the body, with sparkles. */
public final class GadgetAura {
    private static final Vector3f HEAL=new Vector3f(.36f,.92f,.45f),HEAL_PALE=new Vector3f(.72f,1f,.7f);
    private static final Vector3f STIM=new Vector3f(1f,.52f,.1f),STIM_PALE=new Vector3f(1f,.78f,.35f);

    /** Recovery potion: green. */
    static void heal(LivingEntity body,int elapsed){ring(body,elapsed,HEAL,HEAL_PALE,ParticleTypes.HAPPY_VILLAGER);}
    /** Combat stimulant: orange. */
    static void stim(LivingEntity body,long tick){ring(body,tick,STIM,STIM_PALE,ParticleTypes.SMALL_FLAME);}

    private static void ring(LivingEntity body,long tick,Vector3f color,Vector3f pale,ParticleOptions sparkle){
        if(tick%2!=0||!(body.level() instanceof ServerLevel level))return;
        double radius=body.getBbWidth()*.5+.35,height=body.getBbHeight();
        for(int i=0;i<4;i++){
            double angle=tick*.21+i*Math.PI/2,lift=(tick*.07+i*.25)%1;
            double x=body.getX()+Math.cos(angle)*radius,z=body.getZ()+Math.sin(angle)*radius,y=body.getY()+.1+lift*height;
            level.sendParticles(new DustParticleOptions(i%2==0?color:pale,1.1f),x,y,z,1,0,.04,0,0);
        }
        if(tick%8==0)level.sendParticles(sparkle,body.getX(),body.getY()+height*.5,body.getZ(),3,radius*.6,height*.35,radius*.6,0);
    }
    private GadgetAura(){}
}
