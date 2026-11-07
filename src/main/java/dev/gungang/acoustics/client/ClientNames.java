package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The name to show for a player on this client: the GunGang nickname from the player DB, which the server sends as the
 * Tab-list name (ProgressionService.tabName), else the account name. HUDs, the kill log and name tags all use it so the
 * nickname shows the same everywhere.
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class ClientNames {
    /** The nickname for this player id, or null when the player is not in the Tab list. */
    public static String of(UUID id){
        var connection=Minecraft.getInstance().getConnection();
        var info=id==null||connection==null?null:connection.getPlayerInfo(id);
        if(info==null)return null;
        var tab=info.getTabListDisplayName();
        String nickname=tab==null?"":tab.getString().trim();
        return nickname.isEmpty()?info.getProfile().getName():nickname;
    }
    /** Like {@link #of}, falling back to the entity's own name (bots, players not listed). */
    public static String of(Player player){var name=of(player.getUUID());return name!=null?name:player.getName().getString();}
    /** Name tags and anything else reading Player#getDisplayName on this client. */
    @SubscribeEvent public static void displayName(PlayerEvent.NameFormat event){
        if(!event.getEntity().level().isClientSide)return;
        var name=of(event.getEntity().getUUID());
        if(name!=null)event.setDisplayname(Component.literal(name));
    }
    /** The display name is cached per player; refresh it when the server sends a new Tab name (a nickname set or changed). */
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        var mc=Minecraft.getInstance();
        if(event.phase!=TickEvent.Phase.END||mc.level==null||mc.level.getGameTime()%20!=0)return;
        for(var player:mc.level.players()){
            var name=of(player.getUUID());
            if(name!=null&&!player.getDisplayName().getString().contains(name))player.refreshDisplayName();
        }
    }
    private ClientNames(){}
}
