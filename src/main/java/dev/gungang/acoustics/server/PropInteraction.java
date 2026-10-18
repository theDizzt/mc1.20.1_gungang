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
    private static final Map<UUID,SimpleContainerData> FURNACES=new HashMap<>();
    public static void clear(){CONTENTS.clear();FURNACES.clear();}
    public static void remove(PropEntity prop){CONTENTS.remove(prop.getUUID());FURNACES.remove(prop.getUUID());}
    private static SimpleContainerData data(PropEntity prop){return FURNACES.computeIfAbsent(prop.getUUID(),u->new SimpleContainerData(4));}
    public static void tick(PropEntity prop){
        var inv=CONTENTS.get(prop.getUUID());var data=FURNACES.get(prop.getUUID());if(inv==null||data==null)return;
        net.minecraft.world.item.crafting.RecipeType<? extends net.minecraft.world.item.crafting.AbstractCookingRecipe> type=switch(prop.item()){
            case "minecraft:smoker"->net.minecraft.world.item.crafting.RecipeType.SMOKING;
            case "minecraft:blast_furnace"->net.minecraft.world.item.crafting.RecipeType.BLASTING;
            default->net.minecraft.world.item.crafting.RecipeType.SMELTING;
        };
        var recipe=prop.level().getRecipeManager().getRecipeFor(type,inv,prop.level()).orElse(null);
        if(data.get(0)>0)data.set(0,data.get(0)-1);
        var output=recipe==null?net.minecraft.world.item.ItemStack.EMPTY:recipe.getResultItem(prop.level().registryAccess());
        var current=inv.getItem(2);
        boolean can=recipe!=null&&!output.isEmpty()&&(current.isEmpty()||net.minecraft.world.item.ItemStack.isSameItemSameTags(current,output)&&current.getCount()+output.getCount()<=current.getMaxStackSize());
        if(can&&data.get(0)==0){
            var fuel=inv.getItem(1);int burn=net.minecraftforge.common.ForgeHooks.getBurnTime(fuel,type);
            if(burn>0){data.set(0,burn);data.set(1,burn);var remainder=fuel.getCraftingRemainingItem();fuel.shrink(1);if(fuel.isEmpty())inv.setItem(1,remainder);}
        }
        if(can&&data.get(0)>0){
            data.set(3,recipe.getCookingTime());data.set(2,data.get(2)+1);
            if(data.get(2)>=data.get(3)){if(current.isEmpty())inv.setItem(2,output.copy());else current.grow(output.getCount());inv.getItem(0).shrink(1);data.set(2,0);}
        }else data.set(2,0);
        if(prop.state().hasProperty(BlockStateProperties.LIT))prop.state(prop.state().setValue(BlockStateProperties.LIT,data.get(0)>0));
    }
    public static void use(ServerPlayer p,PropEntity prop){
        var state=prop.state();String id=prop.item();
        prop.toggle();
        if(state.hasProperty(BlockStateProperties.OPEN))state=state.cycle(BlockStateProperties.OPEN);
        else if(state.hasProperty(BlockStateProperties.POWERED))state=state.cycle(BlockStateProperties.POWERED);
        else if(state.hasProperty(BlockStateProperties.LIT))state=state.cycle(BlockStateProperties.LIT);
        else if(state.hasProperty(BlockStateProperties.LEVEL_COMPOSTER))state=state.cycle(BlockStateProperties.LEVEL_COMPOSTER);
        else if(state.hasProperty(BlockStateProperties.OCCUPIED))state=state.cycle(BlockStateProperties.OCCUPIED);
        if(id.equals("minecraft:cauldron"))state=state.is(net.minecraft.world.level.block.Blocks.CAULDRON)?net.minecraft.world.level.block.Blocks.WATER_CAULDRON.defaultBlockState():state.hasProperty(BlockStateProperties.LEVEL_CAULDRON)&&state.getValue(BlockStateProperties.LEVEL_CAULDRON)<3?state.cycle(BlockStateProperties.LEVEL_CAULDRON):net.minecraft.world.level.block.Blocks.CAULDRON.defaultBlockState();
        prop.state(state);
        p.level().playSound(null,prop.blockPosition(),state.getSoundType().getPlaceSound(),net.minecraft.sounds.SoundSource.BLOCKS,.5f,1);
        MenuConstructor menu=switch(id){
            case "minecraft:crafting_table"->(n,inv,player)->new CraftingMenu(n,inv,ContainerLevelAccess.NULL);
            case "minecraft:loom"->(n,inv,player)->new LoomMenu(n,inv,ContainerLevelAccess.NULL);
            case "minecraft:grindstone"->(n,inv,player)->new GrindstoneMenu(n,inv,ContainerLevelAccess.NULL);
            case "minecraft:smithing_table"->(n,inv,player)->new SmithingMenu(n,inv,ContainerLevelAccess.NULL);
            case "minecraft:enchanting_table"->(n,inv,player)->new EnchantmentMenu(n,inv,ContainerLevelAccess.create(p.level(),prop.blockPosition())){
                @Override public boolean stillValid(net.minecraft.world.entity.player.Player viewer){return !prop.isRemoved()&&viewer.distanceTo(prop)<8;}
            };
            case "minecraft:chest","minecraft:barrel"->(n,inv,player)->ChestMenu.threeRows(n,inv,CONTENTS.computeIfAbsent(prop.getUUID(),u->new SimpleContainer(27)));
            case "minecraft:furnace"->(n,inv,player)->new FurnaceMenu(n,inv,CONTENTS.computeIfAbsent(prop.getUUID(),u->new SimpleContainer(3)),data(prop));
            case "minecraft:smoker"->(n,inv,player)->new SmokerMenu(n,inv,CONTENTS.computeIfAbsent(prop.getUUID(),u->new SimpleContainer(3)),data(prop));
            case "minecraft:blast_furnace"->(n,inv,player)->new BlastFurnaceMenu(n,inv,CONTENTS.computeIfAbsent(prop.getUUID(),u->new SimpleContainer(3)),data(prop));
            default->null;
        };
        if(menu!=null)p.openMenu(new SimpleMenuProvider(menu,Component.translatable(prop.state().getBlock().getDescriptionId())));
        if(id.equals("gungang:war_table")||id.equals("gungang:armory"))p.displayClientMessage(Component.literal("숨바꼭질 경기 중에는 로비·무기 변경을 사용할 수 없습니다."),true);
    }
    private PropInteraction(){}
}
