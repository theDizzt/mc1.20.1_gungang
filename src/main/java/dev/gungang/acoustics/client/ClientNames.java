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
        // The Tab name carries the 4-digit tag (PlayerTag); plain-text uses get the nickname alone.
        String nickname=tab==null?"":dev.gungang.acoustics.server.PlayerTag.strip(tab.getString().trim()).trim();
        return nickname.isEmpty()?info.getProfile().getName():nickname;
    }
    /** The nickname followed by its small 4-digit tag, for chat-style text and GUIs. */
    public static Component tagged(UUID id,String name){return dev.gungang.acoustics.server.PlayerTag.named(name,tag(id));}
    /** The player's 4-digit tag from the player DB, which the server appends to the Tab name; "" when none. */
    public static String tag(UUID id){
        var connection=Minecraft.getInstance().getConnection();
        var info=id==null||connection==null?null:connection.getPlayerInfo(id);
        var tab=info==null?null:info.getTabListDisplayName();
        return tab==null?"":dev.gungang.acoustics.server.PlayerTag.suffix(tab.getString().trim());
    }
    /**
     * {@code name} with the 4-digit tag when {@code id} is a player listed on this client (bots and unknown ids get
     * the bare name): lobby list and scoreboards, which only carry the id as text.
     */
    public static Component tagged(String id,String name){
        try{var uuid=UUID.fromString(id);var connection=Minecraft.getInstance().getConnection();
            if(connection!=null&&connection.getPlayerInfo(uuid)!=null)return tagged(uuid,name);}
        catch(IllegalArgumentException notPlayer){}
        return Component.literal(name);
    }
    /** Like {@link #of}, falling back to the entity's own name (bots, players not listed). */
    public static String of(Player player){var name=of(player.getUUID());return name!=null?name:player.getName().getString();}
    /** Name tags and anything else reading Player#getDisplayName on this client. */
    @SubscribeEvent public static void displayName(PlayerEvent.NameFormat event){
        if(!event.getEntity().level().isClientSide)return;
        var name=of(event.getEntity().getUUID());
        if(name!=null)event.setDisplayname(tagged(event.getEntity().getUUID(),name));
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
