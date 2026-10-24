package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.registries.ForgeRegistries;

/** A type is consumed once, including every colour/painting variant of that type. */
public final class PropCatalog {
    private static final String TYPES="oak_planks oak_log oak_stairs oak_slab oak_fence oak_pressure_plate dark_oak_door cherry_planks bricks sand sandstone chiseled_sandstone mud_bricks spruce_door spruce_trapdoor tnt chain glowstone sea_lantern lantern soul_lantern lapis_block redstone_block iron_block coal_block gold_block diamond_block netherite_block crimson_planks warped_planks campfire soul_campfire cauldron composter gungang:puddle gungang:acid_puddle red_sand red_sandstone gungang:nozzle gungang:acid_nozzle gungang:war_table gungang:armory stone cobblestone mossy_stone_bricks @banner @bed @carpet @concrete barrier glass @stained_glass barrel chest gungang:gas_barrel gungang:uxo bamboo basalt crafting_table furnace gungang:small_health_pack gungang:large_health_pack copper_block oxidized_copper @wool @terracotta @glazed_terracotta smoker enchanting_table blast_furnace loom grindstone smithing_table magma_block tacz:target gungang:alpha_spawn_gate gungang:bravo_spawn_gate @candle cobweb obsidian jack_o_lantern melon beehive cactus pointed_dripstone rose_bush amethyst_cluster bookshelf flowering_azalea_leaves ancient_debris minecraft:painting decorated_pot moss_block dispenser slime_block stonecutter quartz_block quartz_stairs bedrock";
    /** Display numbers, one per TYPES entry in order. */
    private static final String NUMBERS="01 02 03 04 05 06 07 08 09 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31 32 33 34 35 36 37 38 39 40 41 42 43 44 45 46 47 48 49 50 51 52 53 54 55 56 57 58 59 60 61 62 63 64 65 66 67 68 69 70 71 72 73 74 75 76 77 78 79 80 81 82 83 84 85 86 87 88 89 90 91 92 93 94 95 96 97 98 99";
    private static final String[] COLORS="white orange magenta light_blue yellow lime pink gray light_gray cyan purple blue brown green red black".split(" ");
    public static List<String> available(){return Arrays.stream(TYPES.split(" ")).filter(t->t.startsWith("@")||t.equals("minecraft:painting")||ForgeRegistries.BLOCKS.containsKey(id(t))).toList();}
    public static ResourceLocation id(String text){return new ResourceLocation(text.contains(":")?text:"minecraft:"+text);}
    public static String variant(String type,Random random){return type.startsWith("@")?"minecraft:"+COLORS[random.nextInt(COLORS.length)]+"_"+type.substring(1):id(type).toString();}
    /** Two-digit display number for a drawn block or colour variant, or "??" for an unknown block. */
    public static String number(String block){
        String path=id(block).toString().startsWith("minecraft:")?id(block).getPath():id(block).toString();
        String[] types=TYPES.split(" "),numbers=NUMBERS.split(" ");
        for(int i=0;i<types.length;i++){
            String type=types[i];
            if(type.startsWith("@")){for(String color:COLORS)if(path.equals(color+"_"+type.substring(1)))return numbers[i];}
            else if(id(type).equals(id(block)))return numbers[i];
        }
        return "??";
    }
    /** Available catalog type for a display number ("01"; "1" also accepted), or null. */
    public static String type(String number){
        String[] types=TYPES.split(" "),numbers=NUMBERS.split(" ");String key=number.length()==1?"0"+number:number;
        for(int i=0;i<types.length;i++)if(numbers[i].equals(key))return available().contains(types[i])?types[i]:null;
        return null;
    }
    /** Display numbers of the available types, for command suggestions. */
    public static List<String> numbers(){return available().stream().map(t->number(t.startsWith("@")?"minecraft:white_"+t.substring(1):t)).toList();}
    public static BlockState state(String id){
        var block=ForgeRegistries.BLOCKS.getValue(id(id));
        var state=block==null?Blocks.AIR.defaultBlockState():block.defaultBlockState();
        if(state.hasProperty(BlockStateProperties.LIT))state=state.setValue(BlockStateProperties.LIT,true);
        return state;
    }
    private PropCatalog(){}
}
