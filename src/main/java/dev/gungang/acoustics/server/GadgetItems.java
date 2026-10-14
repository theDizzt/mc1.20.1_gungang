package dev.gungang.acoustics.server;
import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.*;
import net.minecraftforge.registries.*;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;

public final class GadgetItems {
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,"gungang");
    private static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,GungangAcoustics.ID);
    public static final RegistryObject<Item> BOMB=ITEMS.register("bomb",()->new BombItem(false));
    public static final RegistryObject<Item> DEFUSER=ITEMS.register("defuser",()->new BombItem(true));
    public static final RegistryObject<Item> LANDMINE=ITEMS.register("landmine",()->new GadgetItem("landmine"));
    public static final RegistryObject<Item> RECOVERY=ITEMS.register("recovery_potion",()->new GadgetItem("recovery_potion"));
    public static final RegistryObject<Item> ADRENALINE=ITEMS.register("adrenaline",()->new GadgetItem("adrenaline"));
    public static final RegistryObject<Item> BARRIER=ITEMS.register("barrier",()->new GadgetItem("barrier"));
    public static final RegistryObject<Item> SCANNER=ITEMS.register("infrared_scanner",()->new GadgetItem("infrared_scanner"));
    public static final RegistryObject<Item> SHIELD=ITEMS.register("shield",()->new GadgetItem("shield"));
    public static final RegistryObject<Item> MOLOTOV=ITEMS.register("molotov",()->new GadgetItem("molotov"));
    public static final RegistryObject<Item> ORBITAL_STRIKE=ITEMS.register("orbital_strike",()->new GadgetItem("orbital_strike"));
    public static final RegistryObject<Item> LIFE_SUPPORT=ITEMS.register("life_support",()->new GadgetItem("life_support"));
    public static final RegistryObject<Item> JETPACK=ITEMS.register("jetpack",()->new GadgetItem("jetpack"));
    public static final RegistryObject<EntityType<MolotovEntity>> MOLOTOV_PROJECTILE=ENTITIES.register("molotov",()->EntityType.Builder.<MolotovEntity>of(MolotovEntity::new,MobCategory.MISC).sized(.25f,.25f).clientTrackingRange(10).updateInterval(1).build("gungang_acoustics:molotov"));
    public static final RegistryObject<EntityType<OrdnanceEntity>> ORDNANCE=ENTITIES.register("ordnance",()->EntityType.Builder.<OrdnanceEntity>of(OrdnanceEntity::new,MobCategory.MISC).sized(.1f,.1f).clientTrackingRange(16).updateInterval(1).build("gungang_acoustics:ordnance"));
    public static final RegistryObject<EntityType<DeployableEntity>> DEPLOYABLE=ENTITIES.register("deployable",()->EntityType.Builder.<DeployableEntity>of(DeployableEntity::new,MobCategory.MISC).sized(0.7f,0.15f).clientTrackingRange(10).updateInterval(1).build("gungang_acoustics:deployable"));
    public static void register(IEventBus bus) { ITEMS.register(bus);ENTITIES.register(bus);bus.addListener(GadgetItems::attributes); }
    private static void attributes(EntityAttributeCreationEvent e) { e.put(DEPLOYABLE.get(),LivingEntity.createLivingAttributes().add(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH,200).build()); }
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=GungangAcoustics.ID,bus=net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.FORGE)
    public static final class LegacyItems {
        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void remap(MissingMappingsEvent event) {
            for(var mapping:event.getMappings(ForgeRegistries.Keys.ITEMS,GungangAcoustics.ID)) {
                Item item=switch(mapping.getKey().getPath()) {
                    case "landmine" -> LANDMINE.get(); case "recovery_potion" -> RECOVERY.get();
                    case "adrenaline" -> ADRENALINE.get(); case "barrier" -> BARRIER.get(); default -> null;
                };
                if(item!=null)mapping.remap(item);
            }
        }
    }
    private GadgetItems() { }
}
