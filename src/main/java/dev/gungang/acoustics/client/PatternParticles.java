package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.GungangNetwork;
import dev.gungang.acoustics.server.ParticlePatterns;
import net.minecraft.client.Minecraft;

/** Bounded cosmetic work even when several area weapons emit in the same tick. */
public final class PatternParticles {
    private static java.lang.ref.WeakReference<net.minecraft.client.multiplayer.ClientLevel> lastLevel=new java.lang.ref.WeakReference<>(null);
    private static long tick=Long.MIN_VALUE;
    private static int remaining;
    public static void receive(GungangNetwork.ParticlePattern p){
        var mc=Minecraft.getInstance();var level=mc.level;
        if(level==null||mc.player==null||p.kind()<0||p.kind()>2||p.count()<1||p.count()>128
                ||!Float.isFinite(p.radius())||p.radius()<0||p.radius()>128||!Float.isFinite(p.scale())
                ||!Double.isFinite(p.x())||!Double.isFinite(p.y())||!Double.isFinite(p.z()))return;
        double range=32+p.radius();
        if(mc.player.distanceToSqr(p.x(),p.y(),p.z())>range*range)return;
        if(lastLevel.get()!=level||tick!=level.getGameTime()){lastLevel=new java.lang.ref.WeakReference<>(level);tick=level.getGameTime();remaining=512;}
        int count=Math.min(remaining,p.count());remaining-=count;
        for(int i=0;i<count;i++){
            double angle=p.kind()==ParticlePatterns.ORBITAL?i*Math.PI*2/count:level.random.nextDouble()*Math.PI*2;
            double r=p.kind()==ParticlePatterns.ORBITAL?p.radius():p.radius()*Math.sqrt(level.random.nextDouble());
            double y=p.y()+(p.kind()==ParticlePatterns.FALLOUT?level.random.nextDouble()*Math.min(2.5,p.radius()):0);
            y+=level.random.nextGaussian()*(p.kind()==ParticlePatterns.FALLOUT?.05:.08);
            double speed=p.kind()==ParticlePatterns.FIRE?.015:0;
            level.addParticle(ParticlePatterns.particle(p,i),p.x()+Math.cos(angle)*r,y,p.z()+Math.sin(angle)*r,
                    level.random.nextGaussian()*speed,level.random.nextGaussian()*speed,level.random.nextGaussian()*speed);
        }
    }
    private PatternParticles(){}
}
