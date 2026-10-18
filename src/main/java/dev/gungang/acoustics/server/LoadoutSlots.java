package dev.gungang.acoustics.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Keep logical weapon slots distinct from their current positions after an offhand swap. */
final class LoadoutSlots {
    static void mark(ItemStack stack,String tag,int slot) {
        var data=stack.getOrCreateTag();
        data.remove("GunGangIssued");
        data.remove("GunGangFreeSlot");
        data.putInt(tag,slot);
        // Primary and secondary weapons never wear out.
        if((slot==0||slot==40)&&stack.isDamageableItem())data.putBoolean("Unbreakable",true);
    }
    static void preserve(ServerPlayer player,ItemStack stack) {
        if(stack.isEmpty())return;
        var tag=stack.getTag();
        int home=tag==null?-1:tag.contains("GunGangIssued",3)?tag.getInt("GunGangIssued"):
                tag.contains("GunGangFreeSlot",3)?tag.getInt("GunGangFreeSlot"):-1;
        if((home==0||home==40||home==8)&&player.getInventory().getItem(home).isEmpty())
            player.getInventory().setItem(home,stack);
        else if(!player.getInventory().add(stack))player.drop(stack,false);
    }
    private LoadoutSlots(){}
}
