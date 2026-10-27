package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.GungangNetwork;
import dev.gungang.acoustics.server.NozzleBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

/** One burst packet instead of one packet per particle; damage remains server-side. */
public final class NozzleParticles {
    public static void receive(GungangNetwork.NozzleBurst burst){
        var level=Minecraft.getInstance().level;if(level==null)return;
        var random=level.random;var pos=burst.pos();
        ParticleOptions particle=burst.acid()?NozzleBlock.ACID_SPRAY:ParticleTypes.SPLASH;
        double speed=burst.acid()?0:.015;
        for(int i=0;i<burst.count();i++)level.addParticle(particle,
                pos.getX()+.5+random.nextGaussian()*.06,
                pos.getY()+1+random.nextDouble()*burst.height()+random.nextGaussian()*.02,
                pos.getZ()+.5+random.nextGaussian()*.06,
                random.nextGaussian()*speed,random.nextGaussian()*speed,random.nextGaussian()*speed);
    }
    private NozzleParticles(){}
}
