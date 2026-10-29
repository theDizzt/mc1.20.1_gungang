package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingSwapItemsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A broken shield (until repaired) and a thrown hand axe (until its cooldown ends) cannot be held:
 * they stay in the off hand, and one already in hand is put back with the primary in hotbar slot 1.
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class SecondaryLock {
    /** Reads only synced item tags, so the client can check it too. */
    public static boolean locked(Player p,ItemStack stack) {
        if(!SecondaryRuntime.secondary(stack))return false;
        if(HandAxeItem.is(stack))return SecondaryRuntime.remaining(p,stack)>0;
        if(CombatGadgets.is(stack,"shield")) {
            var tag=stack.getTag();
            return tag.contains("GunGangShieldHealth")&&tag.getFloat("GunGangShieldHealth")<=0&&SecondaryRuntime.remaining(p,stack)>0;
        }
        return false;
    }
    public static String reason(ItemStack stack) {return HandAxeItem.is(stack)?"투척용 손도끼를 다시 들 수 있을 때까지 기다려야 합니다.":"부서진 방패는 수리될 때까지 들 수 없습니다.";}
    @SubscribeEvent public static void swap(LivingSwapItemsEvent.Hands event) {
        if(!(event.getEntity() instanceof ServerPlayer p)||!locked(p,event.getItemSwappedToMainHand()))return;
        event.setCanceled(true);
        p.displayClientMessage(Component.literal(reason(event.getItemSwappedToMainHand())),true);
        // The client (and the offhand macro) predicted the swap; send the real hands back.
        p.inventoryMenu.sendAllDataToRemote();
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer p)||!locked(p,p.getMainHandItem()))return;
        holster(p);
    }
    static void holster(ServerPlayer p) {
        var inventory=p.getInventory();
        if(p.isUsingItem())p.stopUsingItem();
        int held=inventory.selected;
        var secondary=inventory.getItem(held);
        inventory.setItem(held,inventory.offhand.get(0));
        inventory.offhand.set(0,secondary);
        // Bring the primary home to hotbar slot 1 and select it.
        for(int i=1;i<inventory.items.size();i++) {
            var tag=inventory.items.get(i).getTag();
            if(tag!=null&&(tag.contains("GunGangIssued",3)&&tag.getInt("GunGangIssued")==0||tag.contains("GunGangFreeSlot",3)&&tag.getInt("GunGangFreeSlot")==0)) {
                var first=inventory.items.get(0);inventory.items.set(0,inventory.items.get(i));inventory.items.set(i,first);break;
            }
        }
        inventory.selected=0;
        p.connection.send(new ClientboundSetCarriedItemPacket(0));
        p.inventoryMenu.sendAllDataToRemote();
        p.displayClientMessage(Component.literal(reason(secondary)),true);
    }
    private SecondaryLock() { }
}
