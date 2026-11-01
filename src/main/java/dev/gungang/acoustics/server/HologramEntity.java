package dev.gungang.acoustics.server;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Stores one real stack, including custom item/model NBT; never stores a spawned item entity. */
public final class HologramEntity extends BlockEntity implements net.minecraft.world.Container,net.minecraft.world.MenuProvider {
    private ItemStack displayed=ItemStack.EMPTY;
    private int size=100,rotation=2;
    public int size(){return size;}public int rotation(){return rotation;}
    public void settings(int size,int rotation){this.size=Math.max(25,Math.min(300,size));this.rotation=Math.max(0,Math.min(4,rotation));setChanged();}
    @Override public void setChanged(){super.setChanged();if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}
    @Override public int getContainerSize(){return 1;}
    @Override public boolean isEmpty(){return displayed.isEmpty();}
    @Override public ItemStack getItem(int slot){return slot==0?displayed:ItemStack.EMPTY;}
    @Override public int getMaxStackSize(){return 1;}
    @Override public ItemStack removeItem(int slot,int count){if(slot!=0)return ItemStack.EMPTY;var result=displayed.split(count);if(!result.isEmpty())setChanged();return result;}
    @Override public ItemStack removeItemNoUpdate(int slot){if(slot!=0)return ItemStack.EMPTY;var result=displayed;displayed=ItemStack.EMPTY;return result;}
    @Override public void setItem(int slot,ItemStack stack){if(slot==0)replace(stack);}
    @Override public void clearContent(){replace(ItemStack.EMPTY);}
    @Override public boolean stillValid(net.minecraft.world.entity.player.Player p){return level!=null&&!isRemoved()&&level.getBlockEntity(worldPosition)==this&&p.distanceToSqr(worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5)<=64;}
    @Override public net.minecraft.network.chat.Component getDisplayName(){return net.minecraft.network.chat.Component.translatable("block.gungang.hologram_projector");}
    @Override public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id,net.minecraft.world.entity.player.Inventory inventory,net.minecraft.world.entity.player.Player p){return new HologramMenu(id,inventory,this);}
    public HologramEntity(BlockPos pos,BlockState state){super(HologramBlocks.ENTITY.get(),pos,state);}
    public ItemStack displayed(){return displayed;}
    public ItemStack replace(ItemStack incoming){
        var previous=displayed;displayed=incoming.copy();if(!displayed.isEmpty())displayed.setCount(1);
        setChanged();return previous;
    }
    @Override protected void saveAdditional(CompoundTag tag){super.saveAdditional(tag);if(!displayed.isEmpty())tag.put("Displayed",displayed.save(new CompoundTag()));tag.putInt("Size",size);tag.putInt("Rotation",rotation);}
    @Override public void load(CompoundTag tag){super.load(tag);displayed=tag.contains("Displayed")?ItemStack.of(tag.getCompound("Displayed")):ItemStack.EMPTY;if(!displayed.isEmpty())displayed.setCount(1);size=tag.contains("Size")?Math.max(25,Math.min(300,tag.getInt("Size"))):100;rotation=tag.contains("Rotation")?Math.max(0,Math.min(4,tag.getInt("Rotation"))):2;}
    @Override public CompoundTag getUpdateTag(){return saveWithoutMetadata();}
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket(){return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);}
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(){return new net.minecraft.world.phys.AABB(worldPosition).inflate(3,4,3);}
}
