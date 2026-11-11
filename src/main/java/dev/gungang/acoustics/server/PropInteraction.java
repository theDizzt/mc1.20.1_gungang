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
    private static final Set<String> SPECIAL=Set.of(PartyBlocks.FLAG_ID,"minecraft:anvil","minecraft:flower_pot","minecraft:spawner","minecraft:nether_portal","minecraft:lodestone",
            "gungang:blue_jump_pad","gungang:lime_jump_pad","gungang:conveyor_belt","minecraft:command_block","minecraft:oak_sign","minecraft:oak_hanging_sign","minecraft:infested_stone",
            "gungang:mannequin","minecraft:beacon","minecraft:end_crystal","minecraft:sculk_sensor","minecraft:sculk_shrieker","gungang:hologram_projector",
            "gungang:shutter","minecraft:sea_pickle","minecraft:turtle_egg","minecraft:note_block","minecraft:jukebox","minecraft:brewing_stand","minecraft:bell",
            "minecraft:bubble_column","minecraft:dispenser","minecraft:tnt","gungang:gas_barrel","gungang:small_health_pack","gungang:large_health_pack",
            "minecraft:respawn_anchor","minecraft:armor_stand","minecraft:daylight_detector","minecraft:lectern","minecraft:end_portal_frame","gungang:eunbi_doll",
            "gungang:prettiest_block","minecraft:crying_obsidian","minecraft:tnt_minecart","gungang:hanbi_doll","minecraft:conduit","gungang:nozzle",
            "minecraft:cartography_table","minecraft:farmland","minecraft:piston","minecraft:sticky_piston","gungang:desk_clock","gungang:money_cart",
            "gungang:chameleon_block","gungang:capsule_machine","gungang:garden_fountain","gungang:easel","minecraft:light",
            "gungang:dice","gungang:chandelier","gungang:slot_machine","gungang:billiard_ball","gungang:wishing_well","gungang:embezzlement_statue","gungang:christmas_tree","gungang:sanga_doll","minecraft:dropper");
    private static final Set<String> MENUS=Set.of("minecraft:crafting_table","minecraft:loom","minecraft:grindstone","minecraft:smithing_table","minecraft:enchanting_table",
            "minecraft:cauldron","gungang:war_table","gungang:armory");
    public static boolean special(String item){return SPECIAL.contains(item);}
    /** Disguises that can hurt or kill seekers, tagged "공격" in red in the block list and HUD. */
    private static final Set<String> ATTACK=Set.of("minecraft:tnt","gungang:gas_barrel","minecraft:dispenser","minecraft:cactus","minecraft:campfire",
            "minecraft:soul_campfire","minecraft:magma_block","minecraft:pointed_dripstone","minecraft:stonecutter","gungang:acid_puddle","gungang:acid_nozzle",
            "gungang:uxo","minecraft:spawner","minecraft:fire","minecraft:soul_fire","minecraft:bee_nest","minecraft:infested_stone","minecraft:minecart",
            "minecraft:end_crystal","minecraft:torchflower","gungang:geyser","minecraft:lava","gungang:acid","gungang:eunbi_doll","minecraft:tnt_minecart",
            "gungang:death_zone","minecraft:conduit","gungang:nuke_warhead","minecraft:lightning_rod","gungang:slot_machine","gungang:embezzlement_statue","minecraft:dropper","gungang:combat_unit");
    public static boolean attack(String item){return ATTACK.contains(item);}
    /** Disguises that put a status effect on seekers (poison, burning, slowness, glowing), tagged "디버프" in purple. */
    private static final Set<String> DEBUFF=Set.of("gungang:acid_puddle","gungang:acid_nozzle","gungang:acid","minecraft:spawner","minecraft:cobweb",
            "minecraft:fire","minecraft:soul_fire","minecraft:torchflower","gungang:geyser","minecraft:lava","minecraft:bell","minecraft:soul_sand","gungang:unpleasant_photo");
    public static boolean debuff(String item){return DEBUFF.contains(item);}
    /** Disguises that move faster or speed up hiders (minecarts, hanbi doll's cloak, beacon, brewing stand's swiftness), tagged "신속" in blue. */
    private static final Set<String> SWIFT=Set.of("minecraft:minecart","minecraft:tnt_minecart","gungang:hanbi_doll","minecraft:beacon","minecraft:brewing_stand","minecraft:sponge","gungang:money_cart","minecraft:light","gungang:billiard_ball","gungang:sanga_doll");
    public static boolean swift(String item){return SWIFT.contains(item);}
    /** Disguises seekers cannot see (barrier, structure void, death zone), tagged "은신" in black. */
    private static final Set<String> STEALTH=Set.of("minecraft:barrier","minecraft:structure_void","gungang:death_zone","minecraft:light","gungang:shadow");
    public static boolean stealth(String item){return STEALTH.contains(item);}
    /** A liquid disguise (water, lava, acid, hot spring water, bubble column): no block model, drawn as its fluid. */
    public static boolean liquid(net.minecraft.world.level.block.state.BlockState state){
        return state.getRenderShape()!=net.minecraft.world.level.block.RenderShape.MODEL&&!state.getFluidState().isEmpty();
    }
    /** A block that gives off light in some state of it (lit or unlit candles, lamps and campfires included), tagged "광원" in gold. */
    public static boolean glows(net.minecraft.world.level.block.state.BlockState state){
        return state.getBlock().getStateDefinition().getPossibleStates().stream().anyMatch(s->s.getLightEmission()>0);
    }
    /** The tags as words in their exact colours, each followed by a space. */
    public static net.minecraft.network.chat.MutableComponent tagComponent(String item){return tagComponent(item,true);}
    /** As above; without black tags ("은신") when {@code black} is false, for callers that draw those outlined themselves. */
    public static net.minecraft.network.chat.MutableComponent tagComponent(String item,boolean black){
        var text=net.minecraft.network.chat.Component.empty();
        for(var t:tags(item))if(black||(t.color()&0xFFFFFF)!=0)text.append(net.minecraft.network.chat.Component.literal(t.label()+" ").withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(t.color()&0xFFFFFF)));
        return text;
    }
    /** A category label shown by a disguise's name, with its colour as ARGB and as a chat colour code. */
    public record Tag(String label,int color,char code){}
    /** Every category of a disguise in display order: 공격, 디버프, 견고, 접착, 신속, 액체, 광원, 특수, 색상. */
    public static java.util.List<Tag> tags(String item,net.minecraft.world.level.block.state.BlockState state){
        var tags=new java.util.ArrayList<Tag>();
        if(attack(item))tags.add(new Tag("공격",0xFFFF5A5A,'c'));
        if(debuff(item))tags.add(new Tag("디버프",0xFFC77DFF,'5'));
        if(PropAbilities.maxHealth(item)>50)tags.add(new Tag("견고",0xFF9DFF5A,'a'));
        if(PropSticky.sticky(item))tags.add(new Tag("접착",0xFFFF8030,'6'));
        if(PropSkate.skater(item))tags.add(new Tag("벽 타기",0xFF40D0A0,'3'));
        if(swift(item))tags.add(new Tag("신속",0xFF4D8BFF,'9'));
        if(liquid(state))tags.add(new Tag("액체",0xFF6CD8FF,'b'));
        if(glows(state))tags.add(new Tag("광원",0xFFE6B422,'6'));
        if(tagged(item,state))tags.add(new Tag("특수",0xFFFFD84A,'e'));
        if(PropCatalog.colored(item))tags.add(new Tag("색상",0xFFFF8AD0,'d'));
        // "종류": one of several looks drawn at random (assorted types; 91 painting picks a random painting).
        if(PropCatalog.assorted(item)||item.equals("minecraft:painting"))tags.add(new Tag("종류",0xFF55D6D6,'3'));
        if(stealth(item))tags.add(new Tag("은신",0xFF000000,'0'));
        return tags;
    }
    public static java.util.List<Tag> tags(String item){return tags(item,PropCatalog.state(item));}
    /** The tags as chat-coloured words, each followed by a space. */
    public static String tagText(String item){var text=new StringBuilder();for(var t:tags(item))text.append('§').append(t.code()).append(t.label()).append(' ');return text.toString();}
    /** Special without an E action: 122 dragon egg teleports away when hit. */
    private static final Set<String> PASSIVE=Set.of("minecraft:dragon_egg");
    /** The "특수" tag: any disguise E does something for (its own ability or a state switch / menu), or with a passive ability. */
    public static boolean tagged(String item,net.minecraft.world.level.block.state.BlockState state){return PASSIVE.contains(item)||interactive(item,state);}
    public static boolean tagged(String item){return interactive(item,PropCatalog.state(item));}
    /**
     * Whether E does anything for this disguise: a special ability, a menu, or switching the block's own state (open,
     * powered, lit, filled, occupied, slab half). Turning the block is R's job, never E's: a stair's corner shape reads
     * as a turn, so E leaves stairs alone.
     */
    public static boolean interactive(String item,net.minecraft.world.level.block.state.BlockState state){
        if(special(item)||storage(item)||MENUS.contains(item))return true;
        for(net.minecraft.world.level.block.state.properties.Property<?> property:List.<net.minecraft.world.level.block.state.properties.Property<?>>of(BlockStateProperties.DELAY,BlockStateProperties.OPEN,BlockStateProperties.POWERED,BlockStateProperties.LIT,BlockStateProperties.LEVEL_COMPOSTER,
                BlockStateProperties.OCCUPIED,BlockStateProperties.SLAB_TYPE))if(state.hasProperty(property))return true;
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
            // 192 repeater: E steps the delay torch 1 → 4, then flips it powered / unpowered and starts over (8 looks).
            if(state.hasProperty(BlockStateProperties.DELAY)){int delay=state.getValue(BlockStateProperties.DELAY);
                state=delay<4?state.setValue(BlockStateProperties.DELAY,delay+1):state.setValue(BlockStateProperties.DELAY,1).cycle(BlockStateProperties.POWERED);}
            else if(state.hasProperty(BlockStateProperties.OPEN))state=state.cycle(BlockStateProperties.OPEN);
            else if(state.hasProperty(BlockStateProperties.POWERED))state=state.cycle(BlockStateProperties.POWERED);
            else if(state.hasProperty(BlockStateProperties.LIT))state=state.cycle(BlockStateProperties.LIT);
            else if(state.hasProperty(BlockStateProperties.LEVEL_COMPOSTER))state=state.cycle(BlockStateProperties.LEVEL_COMPOSTER);
            else if(state.hasProperty(BlockStateProperties.OCCUPIED))state=state.cycle(BlockStateProperties.OCCUPIED);
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
