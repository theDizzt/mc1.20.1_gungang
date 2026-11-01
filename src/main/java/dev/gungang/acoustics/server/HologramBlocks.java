package dev.gungang.acoustics.server;

import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.*;

public final class HologramBlocks {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,"gungang");
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,"gungang");
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,"gungang");
    private static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS=DeferredRegister.create(ForgeRegistries.MENU_TYPES,"gungang");
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<HologramMenu>> MENU=MENUS.register("hologram_projector",()->new net.minecraft.world.inventory.MenuType<>(HologramMenu::new,net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    public static final RegistryObject<HologramBlock> BLOCK=BLOCKS.register("hologram_projector",HologramBlock::new);
    public static final RegistryObject<Item> ITEM=ITEMS.register("hologram_projector",()->new BlockItem(BLOCK.get(),new Item.Properties()));
    public static final RegistryObject<BlockEntityType<HologramEntity>> ENTITY=ENTITIES.register("hologram_projector",()->BlockEntityType.Builder.of(HologramEntity::new,BLOCK.get()).build(null));
    public static void register(net.minecraftforge.eventbus.api.IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);MENUS.register(bus);}
    private HologramBlocks(){}
}
