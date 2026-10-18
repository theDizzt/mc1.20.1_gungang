package dev.gungang.acoustics.server;

import java.util.ArrayList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Legacy bomb/defuser items are no longer part of the mode: never picked up, removed whenever a match boundary clears them. */
@Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID)
public final class BombDrops {
    static final String TAG="GunGangBombDrop";
    private static boolean objective(ItemEntity item){return item.getItem().getItem() instanceof BombItem;}
    @SubscribeEvent public static void pickup(net.minecraftforge.event.entity.player.EntityItemPickupEvent event){
        var item=event.getItem();if(!objective(item)&&!item.getPersistentData().contains(TAG))return;
        event.setCanceled(true);
    }
    @SubscribeEvent public static void join(net.minecraftforge.event.entity.EntityJoinLevelEvent event){
        if(event.getLevel().isClientSide||!(event.getEntity() instanceof ItemEntity item)||!item.getPersistentData().contains(TAG))return;
        event.setCanceled(true);item.discard();
    }
    public static void clear(MinecraftServer server){
        if(server==null)return;
        for(var level:server.getAllLevels()){
            var items=new ArrayList<ItemEntity>();
            for(var e:level.getAllEntities())if(e instanceof ItemEntity item&&(objective(item)||item.getPersistentData().contains(TAG)))items.add(item);
            items.forEach(ItemEntity::discard);
        }
    }
    private BombDrops(){}
}
