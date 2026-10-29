package dev.gungang.acoustics.server;
import net.minecraft.core.particles.*;
import net.minecraftforge.registries.*;
import net.minecraftforge.eventbus.api.IEventBus;
public final class MotionParticles {
    private static final DeferredRegister<ParticleType<?>> TYPES=DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES,"gungang");
    public static final RegistryObject<SimpleParticleType> BLUE=TYPES.register("blue_hardlight",()->new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> LIME=TYPES.register("lime_hardlight",()->new SimpleParticleType(false));
    public static void register(IEventBus bus){TYPES.register(bus);}
    private MotionParticles(){}
}
