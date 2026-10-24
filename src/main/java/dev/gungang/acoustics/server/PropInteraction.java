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
    private static boolean storage(String id){return id.equals("minecraft:chest")||id.equals("minecraft:barrel");}
    private static void open(PropEntity prop,boolean open){
        prop.open(open);
        if(prop.state().hasProperty(BlockStateProperties.OPEN))prop.state(prop.state().setValue(BlockStateProperties.OPEN,open));
    }
    public static void use(ServerPlayer p,PropEntity prop){
        var state=prop.state();String id=prop.item();
        if(storage(id))open(prop,true);
        else{
            prop.toggle();
            // Furnace, smoker and blast furnace have no menu here: E puts the fire out and lights it again.
            if(state.hasProperty(BlockStateProperties.OPEN))state=state.cycle(BlockStateProperties.OPEN);
            else if(state.hasProperty(BlockStateProperties.POWERED))state=state.cycle(BlockStateProperties.POWERED);
            else if(state.hasProperty(BlockStateProperties.LIT))state=state.cycle(BlockStateProperties.LIT);
            else if(state.hasProperty(BlockStateProperties.LEVEL_COMPOSTER))state=state.cycle(BlockStateProperties.LEVEL_COMPOSTER);
            else if(state.hasProperty(BlockStateProperties.OCCUPIED))state=state.cycle(BlockStateProperties.OCCUPIED);
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
            case "minecraft:chest","minecraft:barrel"->(n,inv,player)->{
                var contents=CONTENTS.computeIfAbsent(prop.getUUID(),u->new SimpleContainer(27));
                return new ChestMenu(MenuType.GENERIC_9x3,n,inv,contents,3){
                    @Override public void removed(net.minecraft.world.entity.player.Player viewer){
                        super.removed(viewer);
                        if(viewer.level().isClientSide||prop.isRemoved())return;
                        boolean watched=viewer.getServer().getPlayerList().getPlayers().stream().anyMatch(o->o!=viewer&&o.containerMenu instanceof ChestMenu c&&c.getContainer()==contents);
                        if(!watched){open(prop,false);viewer.level().playSound(null,prop.blockPosition(),id.equals("minecraft:barrel")?net.minecraft.sounds.SoundEvents.BARREL_CLOSE:net.minecraft.sounds.SoundEvents.CHEST_CLOSE,net.minecraft.sounds.SoundSource.BLOCKS,.5f,1);}
                    }
                };
            };
            default->null;
        };
        if(menu!=null)p.openMenu(new SimpleMenuProvider(menu,Component.translatable(prop.state().getBlock().getDescriptionId())));
        if(id.equals("gungang:war_table")||id.equals("gungang:armory"))p.displayClientMessage(Component.literal("숨바꼭질 경기 중에는 로비·무기 변경을 사용할 수 없습니다."),true);
    }
    private PropInteraction(){}
}
