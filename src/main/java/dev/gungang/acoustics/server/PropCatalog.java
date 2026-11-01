package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.registries.ForgeRegistries;

/** A type is consumed once, including every colour/painting variant of that type. */
public final class PropCatalog {
    private static final String EXTRA="spawner anvil gravel hay_block flower_pot soul_sand fire soul_fire nether_portal ice packed_ice bee_nest oak_sign infested_stone lodestone gungang:blue_jump_pad gungang:lime_jump_pad rail hopper minecraft:minecart gungang:conveyor_belt command_block dragon_egg gungang:mannequin redstone_lamp purpur_block clay netherrack soul_soil gungang:dried_ghast gungang:wildflowers pink_petals torchflower pitcher_plant sugar_cane beacon ochre_froglight verdant_froglight pearlescent_froglight minecraft:end_crystal target sculk_sensor gungang:hologram_projector gilded_blackstone sculk_shrieker polished_blackstone @shulker_box gungang:geyser gungang:hot_spring_nozzle gungang:bath_toad gungang:shutter sea_pickle lightning_rod bamboo_planks crimson_hyphae note_block jukebox bamboo_mosaic turtle_egg brewing_stand gungang:big_grass_block structure_void bell end_rod deepslate bubble_column water gungang:hot_spring_water lava gungang:acid dried_kelp_block lily_pad #coral #coral_block respawn_anchor scaffolding minecraft:armor_stand daylight_detector lectern end_portal_frame shroomlight nether_wart_block warped_wart_block tinted_glass gungang:eunbi_doll spore_blossom frogspawn gungang:prettiest_block crying_obsidian minecraft:tnt_minecart honey_block gungang:hanbi_doll";
    private static final Set<String> OBJECTS=Set.of("minecraft:painting","minecraft:minecart","minecraft:end_crystal","minecraft:armor_stand","minecraft:tnt_minecart");
    /** "#" types draw one of the five coral kinds at random (172 coral, 173 coral block). */
    private static final String[] CORALS="tube brain bubble fire horn".split(" ");
    private static final String TYPES="oak_planks oak_log oak_stairs oak_slab oak_fence oak_pressure_plate dark_oak_door cherry_planks bricks sand sandstone chiseled_sandstone mud_bricks spruce_door spruce_trapdoor tnt chain glowstone sea_lantern lantern soul_lantern lapis_block redstone_block iron_block coal_block gold_block diamond_block netherite_block crimson_planks warped_planks campfire soul_campfire cauldron composter gungang:puddle gungang:acid_puddle red_sand red_sandstone gungang:nozzle gungang:acid_nozzle gungang:war_table gungang:armory stone cobblestone mossy_stone_bricks @banner @bed @carpet @concrete barrier glass @stained_glass barrel chest gungang:gas_barrel gungang:uxo bamboo basalt crafting_table furnace gungang:small_health_pack gungang:large_health_pack copper_block oxidized_copper @wool @terracotta @glazed_terracotta smoker enchanting_table blast_furnace loom grindstone smithing_table magma_block tacz:target gungang:alpha_spawn_gate gungang:bravo_spawn_gate @candle cobweb obsidian jack_o_lantern melon beehive cactus pointed_dripstone rose_bush amethyst_cluster bookshelf flowering_azalea_leaves ancient_debris minecraft:painting decorated_pot moss_block dispenser slime_block stonecutter quartz_block quartz_stairs bedrock";
    /** Display numbers, one per TYPES entry in order. */
    private static final String NUMBERS="01 02 03 04 05 06 07 08 09 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31 32 33 34 35 36 37 38 39 40 41 42 43 44 45 46 47 48 49 50 51 52 53 54 55 56 57 58 59 60 61 62 63 64 65 66 67 68 69 70 71 72 73 74 75 76 77 78 79 80 81 82 83 84 85 86 87 88 89 90 91 92 93 94 95 96 97 98 99";
    private static final String[] COLORS="white orange magenta light_blue yellow lime pink gray light_gray cyan purple blue brown green red black".split(" ");
    private static String[] types(){return (TYPES+" "+EXTRA).split(" ");}
    private static String[] labels(){var numbers=new ArrayList<>(List.of(NUMBERS.split(" ")));for(int i=100;numbers.size()<types().length;i++)numbers.add(String.valueOf(i));return numbers.toArray(String[]::new);}
    public static List<String> available(){return Arrays.stream(types()).filter(t->t.startsWith("@")||t.startsWith("#")||OBJECTS.contains(t)||ForgeRegistries.BLOCKS.containsKey(id(t))).toList();}
    public static ResourceLocation id(String text){return new ResourceLocation(text.contains(":")?text:"minecraft:"+text);}
    public static String variant(String type,Random random){if(type.startsWith("#"))return "minecraft:"+CORALS[random.nextInt(CORALS.length)]+"_"+type.substring(1);return type.startsWith("@")?"minecraft:"+COLORS[random.nextInt(COLORS.length)]+"_"+type.substring(1):id(type).toString();}
    /** A colour variant drawn at random for an "@" type (wool, concrete, beds...), shown as "색상" by the name. */
    public static boolean colored(String block){
        String path=id(block).getPath();
        for(String type:types())if(type.startsWith("@"))for(String color:COLORS)if(path.equals(color+"_"+type.substring(1))&&id(block).getNamespace().equals("minecraft"))return true;
        for(String type:types())if(type.startsWith("#"))for(String kind:CORALS)if(path.equals(kind+"_"+type.substring(1))&&id(block).getNamespace().equals("minecraft"))return true;
        return false;
    }
    /** Two-digit display number for a drawn block or colour variant, or "??" for an unknown block. */
    public static String number(String block){
        String path=id(block).toString().startsWith("minecraft:")?id(block).getPath():id(block).toString();
        String[] types=types(),numbers=labels();
        for(int i=0;i<types.length;i++){
            String type=types[i];
            if(type.startsWith("@")){for(String color:COLORS)if(path.equals(color+"_"+type.substring(1)))return numbers[i];}
            else if(type.startsWith("#")){for(String kind:CORALS)if(path.equals(kind+"_"+type.substring(1)))return numbers[i];}
            else if(id(type).equals(id(block)))return numbers[i];
        }
        return "??";
    }
    /** Available catalog type for a display number ("01"; "1" also accepted), or null. */
    public static String type(String number){
        String[] types=types(),numbers=labels();String key=number.length()==1?"0"+number:number;
        for(int i=0;i<types.length;i++)if(numbers[i].equals(key))return available().contains(types[i])?types[i]:null;
        return null;
    }
    /** Display numbers of the available types, for command suggestions. */
    public static List<String> numbers(){return available().stream().map(t->number(t.startsWith("@")?"minecraft:white_"+t.substring(1):t)).toList();}
    public static BlockState state(String id){
        if(id.equals("minecraft:minecart")||id.equals("minecraft:tnt_minecart")||id.equals("minecraft:armor_stand"))return Blocks.RAIL.defaultBlockState();
        if(id.equals("minecraft:end_crystal"))return Blocks.GLASS.defaultBlockState();
        var block=ForgeRegistries.BLOCKS.getValue(id(id));
        var state=block==null?Blocks.AIR.defaultBlockState():block.defaultBlockState();
        if(state.hasProperty(BlockStateProperties.LIT))state=state.setValue(BlockStateProperties.LIT,true);
        // A coral disguise stands dry, without the water a placed coral would carry (no drips or underwater sounds).
        if(state.getBlock() instanceof BaseCoralPlantTypeBlock&&state.hasProperty(BlockStateProperties.WATERLOGGED))state=state.setValue(BlockStateProperties.WATERLOGGED,false);
        // A charged anchor glows and swirls; a lectern shows its book.
        if(state.getBlock() instanceof RespawnAnchorBlock)state=state.setValue(RespawnAnchorBlock.CHARGE,4);
        if(state.getBlock() instanceof LecternBlock)state=state.setValue(LecternBlock.HAS_BOOK,true);
        if(state.hasProperty(BlockStateProperties.FLOWER_AMOUNT))state=state.setValue(BlockStateProperties.FLOWER_AMOUNT,4);
        if(state.getBlock() instanceof BrewingStandBlock)for(var bottle:BrewingStandBlock.HAS_BOTTLE)state=state.setValue(bottle,true);
        // Bubble columns start rising (soul sand), not as a magma whirlpool.
        if(state.getBlock() instanceof BubbleColumnBlock)state=state.setValue(BubbleColumnBlock.DRAG_DOWN,false);
        return state;
    }
    public static net.minecraft.network.chat.Component name(String item){
        if(OBJECTS.contains(item))return new net.minecraft.world.item.ItemStack(ForgeRegistries.ITEMS.getValue(id(item))).getHoverName();
        var block=ForgeRegistries.BLOCKS.getValue(id(item));return block==null?net.minecraft.network.chat.Component.literal(item):block.getName();
    }
    public static net.minecraft.network.chat.Component name(String item,BlockState state){return item.equals("minecraft:infested_stone")||item.equals("minecraft:flower_pot")||item.equals("minecraft:anvil")?state.getBlock().getName():name(item);}
    private PropCatalog(){}
}
