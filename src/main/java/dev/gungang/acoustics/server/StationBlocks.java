package dev.gungang.acoustics.server;

import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.*;
import net.minecraftforge.eventbus.api.IEventBus;

public final class StationBlocks {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,"gungang");
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,"gungang");
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,"gungang");
    public static final RegistryObject<StationBlock> WAR_TABLE=station("war_table",StationBlock.Kind.WAR_TABLE);
    public static final RegistryObject<StationBlock> ARMORY=station("armory",StationBlock.Kind.ARMORY);
    public static final RegistryObject<StationBlock> ALPHA=station("alpha_team",StationBlock.Kind.ALPHA);
    public static final RegistryObject<StationBlock> BRAVO=station("bravo_team",StationBlock.Kind.BRAVO);
    public static final RegistryObject<StationBlock> SMALL=station("small_health_pack",StationBlock.Kind.SMALL);
    public static final RegistryObject<StationBlock> LARGE=station("large_health_pack",StationBlock.Kind.LARGE);
    public static final RegistryObject<BlockEntityType<StationEntity>> ENTITY=ENTITIES.register("station",()->BlockEntityType.Builder.of(StationEntity::new,WAR_TABLE.get(),ARMORY.get(),ALPHA.get(),BRAVO.get(),SMALL.get(),LARGE.get()).build(null));
    private static RegistryObject<StationBlock> station(String id,StationBlock.Kind kind) {
        var block=BLOCKS.register(id,()->kind==StationBlock.Kind.WAR_TABLE||kind==StationBlock.Kind.ARMORY?new FurnitureBlock(kind):new StationBlock(kind));
        ITEMS.register(id,()->new BlockItem(block.get(),new Item.Properties()));return block;
    }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);
        bus.addListener(StationBlocks::creative);
    }
    private static void creative(net.minecraftforge.event.BuildCreativeModeTabContentsEvent e) {
        if(e.getTabKey().equals(GungangCreativeTabs.BLOCKS.getKey()))
            for(var block:java.util.List.of(WAR_TABLE,ARMORY,ALPHA,BRAVO,SMALL,LARGE))e.accept(block.get());
    }
}
