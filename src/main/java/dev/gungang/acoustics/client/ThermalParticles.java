package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.GungangNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.*;

/** One clipped stream packet instead of a packet per droplet; contact is checked on the server. */
public final class ThermalParticles {
    private static final DustParticleOptions WATER=new DustParticleOptions(new org.joml.Vector3f(.22f,.7f,1),.8f);
    public static void receive(GungangNetwork.ThermalBurst burst){
        var level=Minecraft.getInstance().level;if(level==null)return;
        var delta=burst.end().subtract(burst.start());double length=delta.length();if(length<.05||length>10)return;
        var motion=delta.normalize().scale(burst.scald()?.18:.08);
        int count=Math.min(30,Math.max(4,(int)(length/(burst.scald()?.5:.25))));
        for(int i=0;i<count;i++){
            var p=burst.start().add(delta.scale((i+level.random.nextDouble())/count));
            var particle=Minecraft.getInstance().particleEngine.createParticle(WATER,p.x+level.random.nextGaussian()*.05,p.y,p.z+level.random.nextGaussian()*.05,motion.x,motion.y,motion.z);
            if(particle!=null)particle.setParticleSpeed(motion.x,motion.y,motion.z);
        }
        if(level.random.nextInt(4)==0){var p=burst.end();level.addParticle(ParticleTypes.CLOUD,p.x,p.y+.1,p.z,0,.035,0);}
    }
    private ThermalParticles(){}
}
