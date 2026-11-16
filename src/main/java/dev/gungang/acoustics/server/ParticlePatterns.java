package dev.gungang.acoustics.server;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;

/** One packet per viewer for a whole ring or cloud, with vanilla particle fallback. */
public final class ParticlePatterns {
    public static final int ORBITAL=0,FIRE=1,FALLOUT=2;
    public static ParticleOptions particle(GungangNetwork.ParticlePattern p,int index){
        if(p.kind()==FIRE)return ParticleTypes.FLAME;
        int rgb=(index&1)==0?p.first():p.second();
        return new DustParticleOptions(new org.joml.Vector3f((rgb>>16&255)/255f,(rgb>>8&255)/255f,(rgb&255)/255f),p.scale());
    }
    public static void emit(ServerLevel level,int kind,double x,double y,double z,double radius,int count,int first,int second,float scale){
        var packet=new GungangNetwork.ParticlePattern(kind,x,y,z,(float)radius,count,first,second,scale);
        for(var viewer:level.players()){
            double range=32+radius;
            if(viewer.distanceToSqr(x,y,z)>range*range)continue;
            if(GungangNetwork.installed(viewer)){GungangNetwork.send(viewer,packet);continue;}
            // Keep effects visible to clients without the optional mod channel.
            for(int i=0;i<count;i++){
                double angle=kind==ORBITAL?i*Math.PI*2/count:level.random.nextDouble()*Math.PI*2;
                double r=kind==ORBITAL?radius:radius*Math.sqrt(level.random.nextDouble());
                double py=y+(kind==FALLOUT?level.random.nextDouble()*Math.min(2.5,radius):0);
                level.sendParticles(viewer,particle(packet,i),false,x+Math.cos(angle)*r,py,z+Math.sin(angle)*r,
                        1,0,kind==FALLOUT?.05:.08,0,kind==FIRE?.015:0);
            }
        }
    }
    private ParticlePatterns(){}
}
