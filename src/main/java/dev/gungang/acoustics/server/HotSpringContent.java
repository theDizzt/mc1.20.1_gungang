package dev.gungang.acoustics.server;

import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.*;
import net.minecraftforge.registries.*;

public final class HotSpringContent {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,"gungang");
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,"gungang");
    private static final DeferredRegister<Fluid> FLUIDS=DeferredRegister.create(ForgeRegistries.FLUIDS,"gungang");
    private static final DeferredRegister<FluidType> TYPES=DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES,"gungang");
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,"gungang");
    private static final DeferredRegister<net.minecraft.world.effect.MobEffect> EFFECTS=DeferredRegister.create(ForgeRegistries.MOB_EFFECTS,"gungang");
    public static final RegistryObject<HotSpringEffect> HEALING=EFFECTS.register("hot_spring_healing",HotSpringEffect::new);
    public static final RegistryObject<FluidType> TYPE=TYPES.register("hot_spring_water",()->new FluidType(FluidType.Properties.create()
            .canConvertToSource(true).canSwim(true).canDrown(true).canExtinguish(false).supportsBoating(true)
            .sound(net.minecraftforge.common.SoundActions.BUCKET_FILL,net.minecraft.sounds.SoundEvents.BUCKET_FILL)
            .sound(net.minecraftforge.common.SoundActions.BUCKET_EMPTY,net.minecraft.sounds.SoundEvents.BUCKET_EMPTY)){
        @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions> consumer){
            consumer.accept(new net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions(){
                public net.minecraft.resources.ResourceLocation getStillTexture(){return new net.minecraft.resources.ResourceLocation("gungang","block/hot_spring_still");}
                public net.minecraft.resources.ResourceLocation getFlowingTexture(){return new net.minecraft.resources.ResourceLocation("gungang","block/hot_spring_flow");}
                public int getTintColor(){return 0xC0FFFFFF;}
            });
        }
    });
    public static final RegistryObject<ForgeFlowingFluid.Source> SOURCE=FLUIDS.register("hot_spring_water",()->new ForgeFlowingFluid.Source(properties()));
    public static final RegistryObject<ForgeFlowingFluid.Flowing> FLOWING=FLUIDS.register("flowing_hot_spring_water",()->new ForgeFlowingFluid.Flowing(properties()));
    public static final RegistryObject<HotSpringBlock> WATER=BLOCKS.register("hot_spring_water",HotSpringBlock::new);
    public static final RegistryObject<Item> BUCKET=ITEMS.register("hot_spring_bucket",()->new BucketItem(SOURCE,new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
    public static final RegistryObject<ThermalEmitterBlock> NOZZLE=block("hot_spring_nozzle"),TOAD=block("bath_toad"),GEYSER=block("geyser");
    public static final RegistryObject<BlockEntityType<ThermalEmitterEntity>> ENTITY=ENTITIES.register("thermal_emitter",()->BlockEntityType.Builder.of(ThermalEmitterEntity::new,NOZZLE.get(),TOAD.get(),GEYSER.get()).build(null));
    private static ForgeFlowingFluid.Properties properties(){return new ForgeFlowingFluid.Properties(TYPE,SOURCE,FLOWING).bucket(BUCKET).block(WATER);}
    private static RegistryObject<ThermalEmitterBlock> block(String id){var block=BLOCKS.register(id,()->new ThermalEmitterBlock(id));ITEMS.register(id,()->new BlockItem(block.get(),new Item.Properties()));return block;}
    public static void register(net.minecraftforge.eventbus.api.IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);FLUIDS.register(bus);TYPES.register(bus);ENTITIES.register(bus);EFFECTS.register(bus);}
    private HotSpringContent(){}
}
