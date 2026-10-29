package dev.gungang.acoustics.server;

import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.*;

public final class ShutterBlocks {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,"gungang");
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,"gungang");
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,"gungang");
    public static final RegistryObject<ShutterBlock> SHUTTER=BLOCKS.register("shutter",ShutterBlock::new);
    public static final RegistryObject<Item> ITEM=ITEMS.register("shutter",()->new BlockItem(SHUTTER.get(),new Item.Properties()));
    public static final RegistryObject<BlockEntityType<ShutterEntity>> ENTITY=ENTITIES.register("shutter",()->BlockEntityType.Builder.of(ShutterEntity::new,SHUTTER.get()).build(null));
    public static void register(net.minecraftforge.eventbus.api.IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);}
    private ShutterBlocks(){}
}
