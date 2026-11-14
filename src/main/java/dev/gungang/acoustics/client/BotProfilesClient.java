package dev.gungang.acoustics.client;

import com.google.gson.reflect.TypeToken;
import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.BotManager.ProfileView;
import java.util.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The server's bot profiles: their 4-digit tags for names on this client, and the lobby's profile picker. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class BotProfilesClient {
    private static List<ProfileView> profiles=List.of();
    private static Map<String,String> tags=Map.of();
    public static void receive(String json){
        List<ProfileView> parsed=new com.google.gson.Gson().fromJson(json,new TypeToken<List<ProfileView>>(){}.getType());
        profiles=parsed==null?List.of():List.copyOf(parsed);
        var next=new HashMap<String,String>();for(var p:profiles)if(p.tag()!=null&&!p.tag().isEmpty())next.put(p.id(),p.tag());
        tags=Map.copyOf(next);
    }
    static List<ProfileView> profiles(){return profiles;}
    /** A bot's tag ("" for anyone else or before the profiles arrive). */
    static String tag(Object id){return id==null?"":tags.getOrDefault(id.toString(),"");}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){profiles=List.of();tags=Map.of();}
    private BotProfilesClient(){}
}
