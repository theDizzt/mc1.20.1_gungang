package dev.gungang.acoustics.server;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;

final class HologramChecks {
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
    static void run(ServerLevel level){
        var pos=new BlockPos(12,240,12);var block=HologramBlocks.BLOCK.get();level.setBlockAndUpdate(pos,block.defaultBlockState());
        var be=(HologramEntity)level.getBlockEntity(pos);
        var p=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"HologramCheck"));p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.setPos(Vec3.atCenterOf(pos));var menu=new HologramMenu(1,p.getInventory(),be);
        var item=new ItemStack(Items.DIAMOND_SWORD,3);item.getOrCreateTag().putInt("CustomModelData",123);item.setHoverName(net.minecraft.network.chat.Component.literal("Test hologram"));
        p.setItemInHand(InteractionHand.MAIN_HAND,item);var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);
        menu.quickMoveStack(p,28);
        check(item.getCount()==2&&be.displayed().getCount()==1,"Insert consumes exactly one item");
        check(be.displayed().getTag().getInt("CustomModelData")==123,"Custom model NBT retained");
        check(menu.size()==100&&menu.rotation()==2,"Default display settings");
        menu.clickMenuButton(p,1);check(be.size()==125,"Size button updates block");
        for(int i=0;i<20;i++)menu.clickMenuButton(p,1);check(be.size()==300,"Size has an upper bound");
        for(int i=0;i<3;i++)menu.clickMenuButton(p,2);check(be.rotation()==0,"Rotation can be turned off");
        var saved=be.saveWithoutMetadata();var restored=new HologramEntity(pos,block.defaultBlockState());restored.load(saved);
        check(ItemStack.isSameItemSameTags(restored.displayed(),be.displayed()),"Storage survives save/reload");
        check(restored.size()==300&&restored.rotation()==0,"Display settings survive reload");
        var client=new HologramEntity(pos,block.defaultBlockState());client.load(be.getUpdateTag());check(ItemStack.isSameItemSameTags(client.displayed(),be.displayed()),"Client update preserves display");
        menu.quickMoveStack(p,0);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.EMERALD));
        menu.quickMoveStack(p,28);
        check(be.displayed().is(Items.EMERALD)&&p.getInventory().countItem(Items.DIAMOND_SWORD)==1,"Replacement returns previous item");
        p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        menu.quickMoveStack(p,0);
        check(be.displayed().isEmpty()&&p.getInventory().countItem(Items.EMERALD)==1,"Empty hand retrieves stored item");
        p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.DIAMOND));
        block.use(block.defaultBlockState(),level,pos,p,InteractionHand.OFF_HAND,hit);check(be.displayed().isEmpty(),"Offhand cannot double insert");
        p.setPos(100,240,100);check(!menu.clickMenuButton(p,0)&&!menu.stillValid(p),"Distant players cannot change settings");
        be.replace(new ItemStack(Items.AMETHYST_SHARD));level.destroyBlock(pos,false);
        check(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(pos).inflate(1)).stream().filter(e->e.getItem().is(Items.AMETHYST_SHARD)).mapToInt(e->e.getItem().getCount()).sum()==1,"Breaking returns the stored item exactly once");
        System.out.println("GUNGANG_HOLOGRAM_PASS menu shift_transfer size rotation bounds distance persistence sync break_drop");
    }
}
