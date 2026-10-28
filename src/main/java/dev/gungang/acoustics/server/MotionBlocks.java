package dev.gungang.acoustics.server;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.*;
import net.minecraftforge.eventbus.api.IEventBus;

public final class MotionBlocks {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,"gungang");
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,"gungang");
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,"gungang");
    public static final RegistryObject<ConveyorBlock> CONVEYOR=BLOCKS.register("conveyor_belt",ConveyorBlock::new);
    public static final RegistryObject<JumpPadBlock> BLUE=BLOCKS.register("blue_jump_pad",()->new JumpPadBlock(8));
    public static final RegistryObject<JumpPadBlock> LIME=BLOCKS.register("lime_jump_pad",()->new JumpPadBlock(16));
    public static final RegistryObject<Item> WRENCH=ITEMS.register("wrench",ConveyorWrench::new);
    public static final RegistryObject<BlockEntityType<ConveyorEntity>> CONVEYOR_ENTITY=ENTITIES.register("conveyor_belt",()->BlockEntityType.Builder.of(ConveyorEntity::new,CONVEYOR.get()).build(null));
    static {
        ITEMS.register("conveyor_belt",()->new BlockItem(CONVEYOR.get(),new Item.Properties()));
        ITEMS.register("blue_jump_pad",()->new BlockItem(BLUE.get(),new Item.Properties()));
        ITEMS.register("lime_jump_pad",()->new BlockItem(LIME.get(),new Item.Properties()));
    }
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);}
    private MotionBlocks(){}
}
