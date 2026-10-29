package dev.gungang.acoustics.client;
import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.MotionParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class HardlightParticle extends TextureSheetParticle {
    private HardlightParticle(ClientLevel l,double x,double y,double z,double vy,SpriteSet sprites,boolean blue){
        super(l,x,y,z,0,vy,0);xd=zd=0;yd=vy;gravity=0;friction=1;hasPhysics=false;
        lifetime=22+random.nextInt(12);quadSize=.07f+random.nextFloat()*.07f;
        rCol=blue?.12f:.6f;gCol=blue?.8f:1;bCol=blue?1:.12f;pickSprite(sprites);
    }
    @Override public ParticleRenderType getRenderType(){return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;}
    @Override protected int getLightColor(float partial){return 0xF000F0;}
    @Override public void tick(){super.tick();alpha=Math.max(0,1-age/(float)lifetime);}
    @SubscribeEvent public static void register(RegisterParticleProvidersEvent e){
        e.registerSpriteSet(MotionParticles.BLUE.get(),s->(type,l,x,y,z,vx,vy,vz)->new HardlightParticle(l,x,y,z,vy,s,true));
        e.registerSpriteSet(MotionParticles.LIME.get(),s->(type,l,x,y,z,vx,vy,vz)->new HardlightParticle(l,x,y,z,vy,s,false));
    }
}
