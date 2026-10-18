package dev.gungang.acoustics.server;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Predicate;

public final class GungangCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "gungang");
    public static final RegistryObject<CreativeModeTab> BLOCKS = TABS.register("blocks", () ->
            CreativeModeTab.builder().title(Component.translatable("itemGroup.gungang.blocks"))
                    .icon(() -> new ItemStack(StationBlocks.WAR_TABLE.get()))
                    .displayItems((parameters, output) -> gungangItems(item -> item instanceof BlockItem).forEach(output::accept))
                    .build());
    public static final RegistryObject<CreativeModeTab> ITEMS = TABS.register("items", () ->
            CreativeModeTab.builder().title(Component.translatable("itemGroup.gungang.items"))
                    .icon(() -> new ItemStack(GadgetItems.SHIELD.get()))
                    .withTabsBefore(BLOCKS.getId())
                    .displayItems((parameters, output) -> gungangItems(item -> !(item instanceof BlockItem)).forEach(output::accept))
                    .build());

    /** Every item registered under the gungang namespace, sorted by id, so new registrations show up automatically. */
    private static java.util.stream.Stream<Item> gungangItems(Predicate<Item> filter) {
        return ForgeRegistries.ITEMS.getEntries().stream()
                .filter(entry -> entry.getKey().location().getNamespace().equals("gungang"))
                .sorted(java.util.Comparator.comparing(entry -> entry.getKey().location().getPath()))
                .map(java.util.Map.Entry::getValue)
                .filter(filter);
    }

    public static void register(IEventBus bus) { TABS.register(bus); }
    private GungangCreativeTabs() {}
}
