package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Interactive state belongs to the prop; disguises never overwrite the map's blocks. */
public final class PropInteraction {
    private static final Map<UUID,SimpleContainer> CONTENTS=new HashMap<>();
    public static void clear(){CONTENTS.clear();}
    public static void remove(PropEntity prop){CONTENTS.remove(prop.getUUID());}
    /** Chests and barrels open with their menu and shut again when the last viewer closes it. */
    private static boolean storage(String id){return id.equals("minecraft:chest")||id.equals("minecraft:barrel")||id.endsWith("shulker_box");}
    /** A shutter disguise rolls up or down as one tile; PropRenderer animates it. */
    static void shutter(PropEntity prop){
        prop.toggle();
        prop.level().playSound(null,prop.blockPosition(),prop.open()?net.minecraft.sounds.SoundEvents.IRON_DOOR_OPEN:net.minecraft.sounds.SoundEvents.IRON_DOOR_CLOSE,net.minecraft.sounds.SoundSource.BLOCKS,.7f,.75f);
    }
    /** A bell disguise swings (PropEffects watches the ring count in MODE) and rings. */
    static void ring(PropEntity prop){
        prop.mode(prop.mode()+1);
        prop.level().playSound(null,prop.blockPosition(),net.minecraft.sounds.SoundEvents.BELL_BLOCK,net.minecraft.sounds.SoundSource.BLOCKS,2,1);
    }
    private static void open(PropEntity prop,boolean open){
        prop.open(open);
        if(prop.state().hasProperty(BlockStateProperties.OPEN))prop.state(prop.state().setValue(BlockStateProperties.OPEN,open));
    }
    /** Disguises whose E does something of its own (an ability, arrows, a blast, sinking), shown as "특수" by the name. */
    private static final Set<String> SPECIAL=Set.of("minecraft:anvil","minecraft:flower_pot","minecraft:spawner","minecraft:nether_portal","minecraft:lodestone",
            "gungang:blue_jump_pad","gungang:lime_jump_pad","gungang:conveyor_belt","minecraft:command_block","minecraft:oak_sign","minecraft:infested_stone",
            "gungang:mannequin","minecraft:beacon","minecraft:end_crystal","minecraft:sculk_sensor","minecraft:sculk_shrieker","gungang:hologram_projector",
            "gungang:shutter","minecraft:sea_pickle","minecraft:turtle_egg","minecraft:note_block","minecraft:jukebox","minecraft:brewing_stand","minecraft:bell",
            "minecraft:bubble_column","minecraft:dispenser","minecraft:tnt","gungang:gas_barrel","gungang:small_health_pack","gungang:large_health_pack",
            "minecraft:respawn_anchor","minecraft:armor_stand","minecraft:daylight_detector","minecraft:lectern","minecraft:end_portal_frame","gungang:eunbi_doll",
            "gungang:prettiest_block","minecraft:crying_obsidian","minecraft:tnt_minecart","gungang:hanbi_doll");
    private static final Set<String> MENUS=Set.of("minecraft:crafting_table","minecraft:loom","minecraft:grindstone","minecraft:smithing_table","minecraft:enchanting_table",
            "minecraft:cauldron","gungang:war_table","gungang:armory");
    public static boolean special(String item){return SPECIAL.contains(item);}
    /**
     * Whether E does anything for this disguise: a special ability, a menu, or switching the block's own state (open,
     * powered, lit, filled, occupied, stair corner, slab half). Turning the block is R's job, never E's.
     */
    public static boolean interactive(String item,net.minecraft.world.level.block.state.BlockState state){
        if(special(item)||storage(item)||MENUS.contains(item))return true;
        for(net.minecraft.world.level.block.state.properties.Property<?> property:List.<net.minecraft.world.level.block.state.properties.Property<?>>of(BlockStateProperties.OPEN,BlockStateProperties.POWERED,BlockStateProperties.LIT,BlockStateProperties.LEVEL_COMPOSTER,
                BlockStateProperties.OCCUPIED,BlockStateProperties.STAIRS_SHAPE,BlockStateProperties.SLAB_TYPE))if(state.hasProperty(property))return true;
        return false;
    }
    public static void use(net.minecraft.world.entity.LivingEntity p,PropEntity prop){
        var state=prop.state();String id=prop.item();
        // The projected weapon is for show only: nobody can open the projector or take it out.
        if(id.equals("gungang:hologram_projector"))return;
        // Anyone may read a lectern's book (the client opens it); only its owner writes it (E).
        if(id.equals("minecraft:lectern"))return;
        if(id.equals("gungang:shutter")){shutter(prop);return;}
        if(id.equals("minecraft:bell")){ring(prop);return;}
        if(!interactive(id,state))return;
        if(storage(id))open(prop,true);
        else{
            prop.toggle();
            // Furnace, smoker and blast furnace have no menu here: E puts the fire out and lights it again.
            if(state.hasProperty(BlockStateProperties.OPEN))state=state.cycle(BlockStateProperties.OPEN);
            else if(state.hasProperty(BlockStateProperties.POWERED))state=state.cycle(BlockStateProperties.POWERED);
            else if(state.hasProperty(BlockStateProperties.LIT))state=state.cycle(BlockStateProperties.LIT);
            else if(state.hasProperty(BlockStateProperties.LEVEL_COMPOSTER))state=state.cycle(BlockStateProperties.LEVEL_COMPOSTER);
            else if(state.hasProperty(BlockStateProperties.OCCUPIED))state=state.cycle(BlockStateProperties.OCCUPIED);
            else if(state.hasProperty(BlockStateProperties.STAIRS_SHAPE))state=state.cycle(BlockStateProperties.STAIRS_SHAPE);
            else if(state.hasProperty(BlockStateProperties.SLAB_TYPE))state=state.cycle(BlockStateProperties.SLAB_TYPE);
            if(id.equals("minecraft:cauldron"))state=state.is(net.minecraft.world.level.block.Blocks.CAULDRON)?net.minecraft.world.level.block.Blocks.WATER_CAULDRON.defaultBlockState():state.hasProperty(BlockStateProperties.LEVEL_CAULDRON)&&state.getValue(BlockStateProperties.LEVEL_CAULDRON)<3?state.cycle(BlockStateProperties.LEVEL_CAULDRON):net.minecraft.world.level.block.Blocks.CAULDRON.defaultBlockState();
            prop.state(state);
        }
        p.level().playSound(null,prop.blockPosition(),state.getSoundType().getPlaceSound(),net.minecraft.sounds.SoundSource.BLOCKS,.5f,1);
        MenuConstructor menu=switch(id){
            case "minecraft:crafting_table"->(n,inv,player)->new CraftingMenu(n,inv,ContainerLevelAccess.NULL);
            case "minecraft:loom"->(n,inv,player)->new LoomMenu(n,inv,ContainerLevelAccess.NULL);
            case "minecraft:grindstone"->(n,inv,player)->new GrindstoneMenu(n,inv,ContainerLevelAccess.NULL);
            case "minecraft:smithing_table"->(n,inv,player)->new SmithingMenu(n,inv,ContainerLevelAccess.NULL);
            case "minecraft:enchanting_table"->(n,inv,player)->new EnchantmentMenu(n,inv,ContainerLevelAccess.create(p.level(),prop.blockPosition())){
                @Override public boolean stillValid(net.minecraft.world.entity.player.Player viewer){return !prop.isRemoved()&&viewer.distanceTo(prop)<8;}
            };
            case "minecraft:chest","minecraft:barrel","minecraft:white_shulker_box","minecraft:orange_shulker_box","minecraft:magenta_shulker_box","minecraft:light_blue_shulker_box",
                    "minecraft:yellow_shulker_box","minecraft:lime_shulker_box","minecraft:pink_shulker_box","minecraft:gray_shulker_box","minecraft:light_gray_shulker_box","minecraft:cyan_shulker_box",
                    "minecraft:purple_shulker_box","minecraft:blue_shulker_box","minecraft:brown_shulker_box","minecraft:green_shulker_box","minecraft:red_shulker_box","minecraft:black_shulker_box"->(n,inv,player)->{
                var contents=CONTENTS.computeIfAbsent(prop.getUUID(),u->new SimpleContainer(27));
                return new ChestMenu(MenuType.GENERIC_9x3,n,inv,contents,3){
                    @Override public void removed(net.minecraft.world.entity.player.Player viewer){
                        super.removed(viewer);
                        if(viewer.level().isClientSide||prop.isRemoved())return;
                        boolean watched=viewer.getServer().getPlayerList().getPlayers().stream().anyMatch(o->o!=viewer&&o.containerMenu instanceof ChestMenu c&&c.getContainer()==contents);
                        if(!watched){open(prop,false);viewer.level().playSound(null,prop.blockPosition(),id.equals("minecraft:barrel")?net.minecraft.sounds.SoundEvents.BARREL_CLOSE:id.endsWith("shulker_box")?net.minecraft.sounds.SoundEvents.SHULKER_BOX_CLOSE:net.minecraft.sounds.SoundEvents.CHEST_CLOSE,net.minecraft.sounds.SoundSource.BLOCKS,.5f,1);}
                    }
                };
            };
            default->null;
        };
        if(menu!=null&&p instanceof ServerPlayer viewer)viewer.openMenu(new SimpleMenuProvider(menu,Component.translatable(prop.state().getBlock().getDescriptionId())));
        if(id.equals("gungang:war_table")||id.equals("gungang:armory"))PropHuntMatch.say(p,Component.literal("숨바꼭질 경기 중에는 로비·무기 변경을 사용할 수 없습니다."),true);
    }
    private PropInteraction(){}
}
