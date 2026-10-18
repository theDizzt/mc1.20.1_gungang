package dev.gungang.acoustics.server;
import java.util.*;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.*;
import net.minecraftforge.fluids.*;
import net.minecraftforge.eventbus.api.IEventBus;
public final class GimmickBlocks {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,"gungang");
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,"gungang");
    private static final DeferredRegister<BlockEntityType<?>> BES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,"gungang");
    private static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,"gungang");
    private static final DeferredRegister<Fluid> FLUIDS=DeferredRegister.create(ForgeRegistries.FLUIDS,"gungang");
    private static final DeferredRegister<FluidType> TYPES=DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES,"gungang");
    private static final DeferredRegister<net.minecraft.sounds.SoundEvent> SOUNDS=DeferredRegister.create(ForgeRegistries.SOUND_EVENTS,"gungang");
    public static final Map<String,RegistryObject<GimmickBlock>> GIMMICKS=new LinkedHashMap<>();
    public static final RegistryObject<Block> BOMB=BLOCKS.register("planted_bomb",()->new Block(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().strength(-1,3600000).noOcclusion()
            // Planted at the planter's feet: no collision or suffocation; the outline still takes the defuse aim.
            .noCollission().isSuffocating((s,l,pos)->false).isViewBlocking((s,l,pos)->false)));
    public static final RegistryObject<GimmickBlock> UXO=block("uxo"),GAS=block("gas_barrel"),NOZZLE=block("nozzle"),ACID_NOZZLE=block("acid_nozzle");
    static {for(String id:GimmickConfig.defaults().keySet())block(id);}
    public static final RegistryObject<PuddleBlock> PUDDLE=BLOCKS.register("puddle",()->new PuddleBlock(false));
    public static final RegistryObject<PuddleBlock> ACID_PUDDLE=BLOCKS.register("acid_puddle",()->new PuddleBlock(true));
    public static final RegistryObject<Item> PUDDLE_ITEM=ITEMS.register("puddle",()->new PuddleItem(false)),ACID_PUDDLE_ITEM=ITEMS.register("acid_puddle",()->new PuddleItem(true));
    public static final RegistryObject<BlockEntityType<GimmickEntity>> ENTITY=BES.register("gimmick",()->BlockEntityType.Builder.of(GimmickEntity::new,GIMMICKS.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));
    public static final RegistryObject<EntityType<PuddleOverlay>> PUDDLE_OVERLAY=ENTITIES.register("puddle",()->EntityType.Builder.<PuddleOverlay>of(PuddleOverlay::new,MobCategory.MISC).sized(1,.0625f).clientTrackingRange(8).updateInterval(20).build("gungang:puddle")),
            ACID_PUDDLE_OVERLAY=ENTITIES.register("acid_puddle",()->EntityType.Builder.<PuddleOverlay>of(PuddleOverlay::new,MobCategory.MISC).sized(1,.0625f).clientTrackingRange(8).updateInterval(20).build("gungang:acid_puddle"));
    public static final RegistryObject<net.minecraft.sounds.SoundEvent> PUDDLE_STEP=SOUNDS.register("puddle_step",()->net.minecraft.sounds.SoundEvent.createVariableRangeEvent(new net.minecraft.resources.ResourceLocation("gungang","puddle_step")));
    public static final RegistryObject<FluidType> ACID_TYPE=TYPES.register("acid",()->new FluidType(FluidType.Properties.create().canSwim(true).canDrown(true).canExtinguish(true).supportsBoating(true).lightLevel(12)
            .sound(net.minecraftforge.common.SoundActions.BUCKET_FILL,net.minecraft.sounds.SoundEvents.BUCKET_FILL)
            .sound(net.minecraftforge.common.SoundActions.BUCKET_EMPTY,net.minecraft.sounds.SoundEvents.BUCKET_EMPTY)){
        @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions> consumer){consumer.accept(new net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions(){
            public net.minecraft.resources.ResourceLocation getStillTexture(){return new net.minecraft.resources.ResourceLocation("gungang","block/acid_still");}
            public net.minecraft.resources.ResourceLocation getFlowingTexture(){return new net.minecraft.resources.ResourceLocation("gungang","block/acid_flow");}
            // Color is baked into both animated sprites; do not multiply it by a water tint.
            public int getTintColor(){return 0xFFFFFFFF;}
        });}
    });
    public static final RegistryObject<ForgeFlowingFluid.Source> ACID_SOURCE=FLUIDS.register("acid",()->new ForgeFlowingFluid.Source(acidProperties()));
    public static final RegistryObject<ForgeFlowingFluid.Flowing> ACID_FLOWING=FLUIDS.register("flowing_acid",()->new ForgeFlowingFluid.Flowing(acidProperties()));
    public static final RegistryObject<AcidBlock> ACID_BLOCK=BLOCKS.register("acid",AcidBlock::new);
    public static final RegistryObject<Item> ACID_BUCKET=ITEMS.register("acid_bucket",()->new BucketItem(ACID_SOURCE,new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
    private static ForgeFlowingFluid.Properties acidProperties(){return new ForgeFlowingFluid.Properties(ACID_TYPE,ACID_SOURCE,ACID_FLOWING).bucket(ACID_BUCKET).block(ACID_BLOCK);}
    private static RegistryObject<GimmickBlock> block(String id){var b=BLOCKS.register(id,()->id.endsWith("nozzle")?new NozzleBlock(id):new GimmickBlock(id));GIMMICKS.put(id,b);ITEMS.register(id,()->new BlockItem(b.get(),new Item.Properties()));return b;}
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);BES.register(bus);ENTITIES.register(bus);FLUIDS.register(bus);TYPES.register(bus);SOUNDS.register(bus);}
    private GimmickBlocks(){}
}
