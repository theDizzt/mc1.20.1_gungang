package dev.gungang.acoustics.server;

import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class HologramMenu extends AbstractContainerMenu {
    private final Container container;
    private final ContainerData data;
    public HologramMenu(int id,Inventory inventory){this(id,inventory,new SimpleContainer(1),new SimpleContainerData(2));}
    public HologramMenu(int id,Inventory inventory,HologramEntity entity){this(id,inventory,entity,new ContainerData(){
        public int get(int index){return index==0?entity.size():entity.rotation();}
        public void set(int index,int value){entity.settings(index==0?value:entity.size(),index==1?value:entity.rotation());}
        public int getCount(){return 2;}
    });}
    private HologramMenu(int id,Inventory inventory,Container container,ContainerData data){
        super(HologramBlocks.MENU.get(),id);this.container=container;this.data=data;
        addSlot(new Slot(container,0,80,30){@Override public int getMaxStackSize(){return 1;}});
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,col+row*9+9,8+col*18,115+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,8+col*18,173));
        addDataSlots(data);
    }
    public int size(){return data.get(0);}public int rotation(){return data.get(1);}
    @Override public boolean stillValid(Player p){return container.stillValid(p);}
    @Override public boolean clickMenuButton(Player p,int button){
        if(!(container instanceof HologramEntity entity)||!stillValid(p)||p.isSpectator())return false;
        switch(button){case 0->entity.settings(size()-25,rotation());case 1->entity.settings(size()+25,rotation());case 2->entity.settings(size(),(rotation()+1)%5);default->{return false;}}
        broadcastChanges();return true;
    }
    @Override public ItemStack quickMoveStack(Player p,int index){
        if(index<0||index>=slots.size()||!stillValid(p))return ItemStack.EMPTY;
        var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
        var stack=slot.getItem();var copy=stack.copy();
        if(index==0){if(!moveItemStackTo(stack,1,37,true))return ItemStack.EMPTY;}
        else if(!moveItemStackTo(stack,0,1,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();slot.onTake(p,stack);return copy;
    }
}
