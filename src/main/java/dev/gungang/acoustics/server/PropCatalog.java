package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.registries.ForgeRegistries;

/** A type is consumed once, including every colour/painting variant of that type. */
public final class PropCatalog {
    private static final String EXTRA="spawner anvil gravel hay_block flower_pot soul_sand fire soul_fire nether_portal ice packed_ice bee_nest oak_sign infested_stone lodestone gungang:blue_jump_pad gungang:lime_jump_pad rail hopper minecraft:minecart gungang:conveyor_belt command_block dragon_egg gungang:mannequin redstone_lamp purpur_block clay netherrack soul_soil gungang:dried_ghast gungang:wildflowers pink_petals torchflower pitcher_plant sugar_cane beacon ochre_froglight verdant_froglight pearlescent_froglight minecraft:end_crystal target sculk_sensor gungang:hologram_projector gilded_blackstone sculk_shrieker polished_blackstone @shulker_box gungang:geyser gungang:hot_spring_nozzle gungang:bath_toad gungang:shutter sea_pickle lightning_rod bamboo_mosaic_stairs crimson_hyphae note_block jukebox bamboo_mosaic turtle_egg brewing_stand gungang:big_grass_block structure_void bell end_rod deepslate bubble_column water gungang:hot_spring_water lava gungang:acid dried_kelp_block lily_pad #coral #coral_block respawn_anchor scaffolding minecraft:armor_stand daylight_detector lectern end_portal_frame shroomlight nether_wart_block warped_wart_block tinted_glass gungang:eunbi_doll spore_blossom frogspawn gungang:prettiest_block crying_obsidian minecraft:tnt_minecart honey_block gungang:hanbi_doll repeater ladder @candle_cake redstone_torch gungang:death_zone torch soul_torch conduit"
            +" blackstone oak_leaves warped_hyphae coal_ore deepslate_coal_ore polished_blackstone_bricks reinforced_deepslate sculk sculk_vein sponge"
            +" cobblestone_wall nether_bricks red_nether_bricks fletching_table cartography_table farmland oak_sapling prismarine piston sticky_piston red_mushroom"
            +" gungang:desk_clock gungang:money_cart mycelium gungang:aquarium bone_block quartz_pillar raw_iron_block raw_copper_block raw_gold_block"
            +" oak_hanging_sign gungang:chameleon_block gungang:capsule_machine smooth_stone gungang:error_block gungang:garden_fountain mossy_cobblestone"
            +" prismarine_bricks dark_prismarine gungang:nuke_warhead cocoa gungang:easel light"
            +" gungang:wall_clock gungang:command_flag_block gungang:dice gungang:chandelier gungang:slot_machine gungang:billiard_ball gungang:wishing_well gungang:embezzlement_statue gungang:christmas_tree gungang:sanga_doll"
            +" dropper gungang:unpleasant_photo gungang:combat_unit gungang:shadow";
    private static final Set<String> OBJECTS=Set.of("minecraft:painting","minecraft:minecart","minecraft:end_crystal","minecraft:armor_stand","minecraft:tnt_minecart");
    /** "#" types draw one of the five coral kinds at random (172 coral, 173 coral block). */
    private static final String[] CORALS="tube brain bubble fire horn".split(" ");
    private static final String TYPES="oak_planks oak_log oak_stairs oak_slab oak_fence oak_pressure_plate iron_door stripped_oak_log bricks sand sandstone stone_bricks mud_bricks oak_door oak_trapdoor tnt chain glowstone sea_lantern lantern soul_lantern lapis_block redstone_block iron_block coal_block gold_block diamond_block netherite_block emerald_block amethyst_block campfire soul_campfire cauldron composter gungang:puddle gungang:acid_puddle red_sand red_sandstone gungang:nozzle gungang:acid_nozzle gungang:war_table gungang:armory stone cobblestone stone_brick_slab @banner @bed @carpet @concrete barrier glass @stained_glass barrel chest gungang:gas_barrel gungang:uxo bamboo basalt crafting_table furnace gungang:small_health_pack gungang:large_health_pack copper_block oxidized_copper @wool @terracotta @glazed_terracotta smoker enchanting_table blast_furnace loom grindstone smithing_table magma_block tacz:target gungang:alpha_spawn_gate gungang:bravo_spawn_gate @candle cobweb obsidian jack_o_lantern melon beehive cactus pointed_dripstone rose_bush amethyst_cluster bookshelf flowering_azalea_leaves ancient_debris minecraft:painting decorated_pot moss_block dispenser slime_block stonecutter quartz_block quartz_stairs bedrock";
    /** Display numbers, one per TYPES entry in order. */
    private static final String NUMBERS="01 02 03 04 05 06 07 08 09 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31 32 33 34 35 36 37 38 39 40 41 42 43 44 45 46 47 48 49 50 51 52 53 54 55 56 57 58 59 60 61 62 63 64 65 66 67 68 69 70 71 72 73 74 75 76 77 78 79 80 81 82 83 84 85 86 87 88 89 90 91 92 93 94 95 96 97 98 99";
    private static final String[] WOODS="oak spruce birch jungle acacia dark_oak mangrove cherry bamboo crimson warped".split(" ");
    private static List<String> wood(String suffix){return Arrays.stream(WOODS).map(w->"minecraft:"+w+suffix).toList();}
    private static List<String> ids(String text){return Arrays.stream(text.split(" ")).map(t->"minecraft:"+t).toList();}
    private static List<String> join(List<String> a,List<String> b){var out=new ArrayList<>(a);out.addAll(b);return List.copyOf(out);}
    /**
     * Assorted types: the disguise keeps the type's own id (its number, exclusions and abilities stay put) and wears one of
     * these blocks at random, drawn when it is put on (PropAbilities.initialize). Tagged "종류" by the name.
     */
    private static final Map<String,List<String>> KINDS=Map.ofEntries(
            Map.entry("minecraft:oak_planks",wood("_planks")),
            Map.entry("minecraft:stone",ids("stone granite diorite andesite")),
            Map.entry("minecraft:smooth_stone",ids("smooth_stone polished_granite polished_diorite polished_andesite polished_deepslate smooth_basalt")),
            Map.entry("minecraft:oak_hanging_sign",wood("_hanging_sign")),
            Map.entry("minecraft:oak_log",ids("oak_log spruce_log birch_log jungle_log acacia_log dark_oak_log mangrove_log cherry_log crimson_stem warped_stem bamboo_block")),
            Map.entry("minecraft:oak_stairs",wood("_stairs")),
            Map.entry("minecraft:oak_slab",wood("_slab")),
            Map.entry("minecraft:oak_fence",join(wood("_fence"),ids("nether_brick_fence"))),
            Map.entry("minecraft:oak_pressure_plate",join(wood("_pressure_plate"),ids("stone_pressure_plate polished_blackstone_pressure_plate light_weighted_pressure_plate heavy_weighted_pressure_plate"))),
            Map.entry("minecraft:stripped_oak_log",ids("stripped_oak_log stripped_spruce_log stripped_birch_log stripped_jungle_log stripped_acacia_log stripped_dark_oak_log stripped_mangrove_log stripped_cherry_log stripped_crimson_stem stripped_warped_stem stripped_bamboo_block")),
            Map.entry("minecraft:sandstone",ids("sandstone smooth_sandstone chiseled_sandstone")),
            Map.entry("minecraft:stone_bricks",ids("stone_bricks mossy_stone_bricks cracked_stone_bricks chiseled_stone_bricks")),
            Map.entry("minecraft:oak_door",wood("_door")),
            Map.entry("minecraft:oak_trapdoor",wood("_trapdoor")),
            Map.entry("minecraft:red_sandstone",ids("red_sandstone smooth_red_sandstone chiseled_red_sandstone")),
            Map.entry("minecraft:oak_sign",wood("_sign")),
            Map.entry("minecraft:polished_blackstone",ids("polished_blackstone chiseled_polished_blackstone")),
            Map.entry("minecraft:oak_leaves",ids("oak_leaves spruce_leaves birch_leaves jungle_leaves acacia_leaves dark_oak_leaves mangrove_leaves cherry_leaves azalea_leaves")),
            Map.entry("minecraft:coal_ore",ids("coal_ore iron_ore copper_ore gold_ore redstone_ore emerald_ore lapis_ore diamond_ore")),
            Map.entry("minecraft:deepslate_coal_ore",ids("deepslate_coal_ore deepslate_iron_ore deepslate_copper_ore deepslate_gold_ore deepslate_redstone_ore deepslate_emerald_ore deepslate_lapis_ore deepslate_diamond_ore")),
            Map.entry("minecraft:polished_blackstone_bricks",ids("polished_blackstone_bricks cracked_polished_blackstone_bricks")),
            Map.entry("minecraft:cobblestone_wall",ids("cobblestone_wall mossy_cobblestone_wall stone_brick_wall mossy_stone_brick_wall granite_wall diorite_wall andesite_wall cobbled_deepslate_wall polished_deepslate_wall deepslate_brick_wall deepslate_tile_wall brick_wall mud_brick_wall sandstone_wall red_sandstone_wall prismarine_wall end_stone_brick_wall blackstone_wall polished_blackstone_wall polished_blackstone_brick_wall nether_brick_wall red_nether_brick_wall")),
            // Full-block variants only (no slabs, stairs or walls); red nether bricks have no variant, so 212 is the one block.
            Map.entry("minecraft:nether_bricks",ids("nether_bricks cracked_nether_bricks chiseled_nether_bricks")),
            Map.entry("minecraft:oak_sapling",ids("oak_sapling spruce_sapling birch_sapling jungle_sapling acacia_sapling dark_oak_sapling cherry_sapling mangrove_propagule azalea flowering_azalea")),
            Map.entry("minecraft:red_mushroom",ids("red_mushroom brown_mushroom crimson_fungus warped_fungus")));
    /** An assorted type (several looks, one drawn at random). */
    public static boolean assorted(String item){return KINDS.containsKey(item);}
    /** The blocks an assorted type can look like (the type alone otherwise). */
    public static List<String> kinds(String item){return KINDS.getOrDefault(item,List.of(item));}
    /** A random look for the disguise: one of an assorted type's blocks, else the type's own state. */
    public static BlockState randomState(String item,java.util.function.IntUnaryOperator random){var list=kinds(item);return state(list.get(random.applyAsInt(list.size())));}
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
    /** A block that stands for a catalog type: white for colour types, tube for corals. */
    public static String display(String type){return type.startsWith("@")?"minecraft:white_"+type.substring(1):type.startsWith("#")?"minecraft:"+CORALS[0]+"_"+type.substring(1):id(type).toString();}
    /** Display numbers of the available types, for command suggestions. */
    public static List<String> numbers(){return available().stream().map(t->number(t.startsWith("@")?"minecraft:white_"+t.substring(1):t)).toList();}
    public static BlockState state(String id){
        if(id.equals("minecraft:minecart")||id.equals("minecraft:tnt_minecart")||id.equals("minecraft:armor_stand"))return Blocks.RAIL.defaultBlockState();
        if(id.equals("minecraft:end_crystal"))return Blocks.GLASS.defaultBlockState();
        var block=ForgeRegistries.BLOCKS.getValue(id(id));
        var state=block==null?Blocks.AIR.defaultBlockState():block.defaultBlockState();
        if(state.hasProperty(BlockStateProperties.LIT))state=state.setValue(BlockStateProperties.LIT,true);
        // A coral disguise stands dry, without the water a placed coral would carry (no drips or underwater sounds).
        // Nothing is drawn waterlogged either (a conduit is by default): a disguise would carry a water cube around.
        if(state.hasProperty(BlockStateProperties.WATERLOGGED))state=state.setValue(BlockStateProperties.WATERLOGGED,false);
        // 194: the birthday cake starts with its candle out; E lights it (and the song plays while it burns).
        if(state.getBlock() instanceof CandleCakeBlock)state=state.setValue(BlockStateProperties.LIT,false);
        // A charged anchor glows and swirls; a lectern shows its book.
        if(state.getBlock() instanceof RespawnAnchorBlock)state=state.setValue(RespawnAnchorBlock.CHARGE,4);
        if(state.getBlock() instanceof LecternBlock)state=state.setValue(LecternBlock.HAS_BOOK,true);
        if(state.hasProperty(BlockStateProperties.FLOWER_AMOUNT))state=state.setValue(BlockStateProperties.FLOWER_AMOUNT,4);
        if(state.getBlock() instanceof BrewingStandBlock)for(var bottle:BrewingStandBlock.HAS_BOTTLE)state=state.setValue(bottle,true);
        // A sculk vein needs a face to show: it lies on the floor. Leaves never decay; farmland is wet.
        if(state.getBlock() instanceof MultifaceBlock)state=state.setValue(MultifaceBlock.getFaceProperty(net.minecraft.core.Direction.DOWN),true);
        if(state.hasProperty(BlockStateProperties.PERSISTENT))state=state.setValue(BlockStateProperties.PERSISTENT,true);
        if(state.getBlock() instanceof FarmBlock)state=state.setValue(FarmBlock.MOISTURE,7);
        // 240: ripe cocoa. 224: a furnished aquarium (PropAbilities picks its fish).
        if(state.getBlock() instanceof CocoaBlock)state=state.setValue(CocoaBlock.AGE,2);
        if(state.getBlock() instanceof ToyBlocks.Aquarium)state=state.setValue(ToyBlocks.Aquarium.SUBSTRATE,1).setValue(ToyBlocks.Aquarium.ROCK,true).setValue(ToyBlocks.Aquarium.PLANT,true).setValue(ToyBlocks.Aquarium.FISH,1);
        // A piston starts facing up; R turns it through all six directions.
        if(state.getBlock() instanceof net.minecraft.world.level.block.piston.PistonBaseBlock)state=state.setValue(net.minecraft.world.level.block.piston.PistonBaseBlock.FACING,net.minecraft.core.Direction.UP);
        // Bubble columns start rising (soul sand), not as a magma whirlpool.
        if(state.getBlock() instanceof BubbleColumnBlock)state=state.setValue(BubbleColumnBlock.DRAG_DOWN,false);
        return state;
    }
    public static net.minecraft.network.chat.Component name(String item){
        if(OBJECTS.contains(item))return new net.minecraft.world.item.ItemStack(ForgeRegistries.ITEMS.getValue(id(item))).getHoverName();
        var block=ForgeRegistries.BLOCKS.getValue(id(item));return block==null?net.minecraft.network.chat.Component.literal(item):block.getName();
    }
    public static net.minecraft.network.chat.Component name(String item,BlockState state){return assorted(item)&&!state.isAir()||item.equals("minecraft:infested_stone")||item.equals("minecraft:flower_pot")||item.equals("minecraft:anvil")?state.getBlock().getName():name(item);}
    /**
     * Chat lines for /gungang prop list: every available type as "[01] name", four to a line. Colour types show their
     * white (coral: tube) variant tagged "색상"; types with their own E effect are tagged "특수".
     */
    public static List<net.minecraft.network.chat.Component> listing(){
        var lines=new ArrayList<net.minecraft.network.chat.Component>();
        var types=available();
        lines.add(net.minecraft.network.chat.Component.literal("변신 블록 목록 ("+types.size()+"종) · ").withStyle(net.minecraft.ChatFormatting.GOLD)
                .append(net.minecraft.network.chat.Component.literal("특수").withStyle(net.minecraft.ChatFormatting.YELLOW))
                .append(net.minecraft.network.chat.Component.literal(" E 효과 · ").withStyle(net.minecraft.ChatFormatting.GRAY))
                .append(net.minecraft.network.chat.Component.literal("색상").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE))
                .append(net.minecraft.network.chat.Component.literal(" 무작위 색").withStyle(net.minecraft.ChatFormatting.GRAY)));
        net.minecraft.network.chat.MutableComponent line=null;int count=0;
        for(String type:types){
            String block=type.startsWith("@")?"minecraft:white_"+type.substring(1):type.startsWith("#")?"minecraft:"+CORALS[0]+"_"+type.substring(1):id(type).toString();
            var entry=net.minecraft.network.chat.Component.literal("["+number(block)+"] ").withStyle(net.minecraft.ChatFormatting.AQUA)
                    .append(name(block).copy().withStyle(net.minecraft.ChatFormatting.WHITE));
            if(type.startsWith("@")||type.startsWith("#"))entry.append(net.minecraft.network.chat.Component.literal(" 색상").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
            if(PropInteraction.special(block))entry.append(net.minecraft.network.chat.Component.literal(" 특수").withStyle(net.minecraft.ChatFormatting.YELLOW));
            if(line==null)line=net.minecraft.network.chat.Component.empty();else line.append(net.minecraft.network.chat.Component.literal("  ·  ").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            line.append(entry);
            if(++count%4==0){lines.add(line);line=null;}
        }
        if(line!=null)lines.add(line);
        return lines;
    }
    private PropCatalog(){}
}
