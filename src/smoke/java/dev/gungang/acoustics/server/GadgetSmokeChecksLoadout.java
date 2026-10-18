package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Exercises actual inventory mutations and the respawn event on a dedicated server. */
final class GadgetSmokeChecksLoadout {
    private static java.lang.reflect.Field field(String name)throws Exception{
        var field=LobbyManager.class.getDeclaredField(name);field.setAccessible(true);return field;
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    @SuppressWarnings("unchecked") static void run(MinecraftServer server)throws Exception{
        var player=FakePlayerFactory.getMinecraft(server.overworld());
        var grantFree=LobbyManager.class.getDeclaredMethod("grantFree",ServerPlayer.class,Loadouts.Weapon.class,int.class);grantFree.setAccessible(true);
        var grant=LobbyManager.class.getDeclaredMethod("grant",ServerPlayer.class,Loadouts.Weapon.class,int.class,boolean.class);grant.setAccessible(true);
        var giveSlot9=LobbyManager.class.getDeclaredMethod("giveSlot9",ServerPlayer.class);giveSlot9.setAccessible(true);
        var oldCatalog=field("loadouts").get(null);
        var primary=new Loadouts.Weapon("test_primary","{id:\"minecraft:bow\",Count:1b}","Primary","",null,"","test",150d,90d,0d);
        var secondary=new Loadouts.Weapon("test_secondary","{id:\"minecraft:iron_sword\",Count:1b}","Secondary","",null,"");
        var slot9=new Loadouts.Weapon("test_slot9","{id:\"minecraft:arrow\",Count:8b}","Slot9","",null,"");
        var choices=(Map<UUID,String[]>)field("FREE_SELECTION").get(null);var oldChoice=choices.get(player.getUUID());
        try{
            field("loadouts").set(null,new Loadouts(List.of(primary),List.of(secondary),slot9));
            for(boolean match:List.of(false,true))for(int replacement:List.of(0,40)){
                player.getInventory().clearContent();
                if(match){grant.invoke(null,player,primary,0,false);grant.invoke(null,player,secondary,40,false);}
                else{grantFree.invoke(null,player,primary,0);grantFree.invoke(null,player,secondary,40);}
                var main=player.getInventory().getItem(0);var off=player.getInventory().getItem(40);
                main.getOrCreateTag().putInt("AmmoLeft",7);off.getOrCreateTag().putInt("RuntimeState",123);
                player.getInventory().setItem(0,off);player.getInventory().setItem(40,main);
                if(match)grant.invoke(null,player,replacement==0?primary:secondary,replacement,false);
                else grantFree.invoke(null,player,replacement==0?primary:secondary,replacement);
                check(player.getInventory().getItem(0).is(Items.BOW),"Primary stays in primary slot after swapped-hand replacement");
                check(player.getInventory().getItem(40).is(Items.IRON_SWORD),"Secondary stays in offhand after swapped-hand replacement");
                check(replacement==0?player.getInventory().getItem(40).getTag().getInt("RuntimeState")==123:
                    player.getInventory().getItem(0).getTag().getInt("AmmoLeft")==7,"Unchanged weapon retains runtime NBT");
            }
            var exported=primary.stack();exported.getOrCreateTag().putInt("GunGangIssued",40);
            LoadoutSlots.mark(exported,"GunGangFreeSlot",0);
            check(!exported.getTag().contains("GunGangIssued"),"Exported old role cannot delete the other weapon");
            player.getInventory().clearContent();player.getInventory().setItem(8,exported);
            giveSlot9.invoke(null,player);
            check(player.getInventory().getItem(0)==exported,"Slot 9 refresh preserves a weapon moved into that slot");
            for(double maximum:List.of(150d,75d)){
                var weapon=new Loadouts.Weapon("health","{id:\"minecraft:bow\",Count:1b}","Health","",null,"","test",maximum,90d,0d);
                field("loadouts").set(null,new Loadouts(List.of(weapon),List.of(secondary),slot9));
                choices.put(player.getUUID(),new String[]{"health","test_secondary"});
                LoadoutAttributes.clear(player);player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);player.setHealth(100);
                LobbyManager.respawn(new PlayerEvent.PlayerRespawnEvent(player,false));
                check(player.getMaxHealth()==maximum&&player.getHealth()==maximum,"Respawn restores chosen primary health: "+maximum);
            }
            System.out.println("GUNGANG_LOADOUT_SMOKE_PASS");
        }finally{
            field("loadouts").set(null,oldCatalog);
            if(oldChoice==null)choices.remove(player.getUUID());else choices.put(player.getUUID(),oldChoice);
            LoadoutAttributes.clear(player);player.getInventory().clearContent();
        }
    }
    private GadgetSmokeChecksLoadout(){}
}
