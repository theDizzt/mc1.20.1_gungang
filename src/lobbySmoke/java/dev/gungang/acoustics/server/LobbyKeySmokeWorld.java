package dev.gungang.acoustics.server;
import dev.gungang.acoustics.GungangAcoustics;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class LobbyKeySmokeWorld {
    private static net.minecraft.server.level.ServerPlayer pending;
    private static int delay;
    @SubscribeEvent public static void join(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event){
        if(!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer p))return;
        p.server.getPlayerList().op(p.getGameProfile());
        pending=p;delay=60;
    }
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ServerTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END||pending==null||--delay>0)return;
        pending.server.getCommands().performPrefixedCommand(pending.server.createCommandSourceStack(),"gungang lobby open");
        try{stability(pending);System.out.println("GUNGANG_LOBBY_STABILITY_PASS same_map same_mode same_team duplicate_ready stale_mode stale_ready changed_selection");}
        catch(Exception|AssertionError failure){failure.printStackTrace();System.out.println("GUNGANG_LOBBY_STABILITY_FAIL");}
        pending=null;
    }
    private static java.lang.reflect.Field field(Class<?> type,String name)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    private static Object get(String name)throws Exception{return field(LobbyManager.class,name).get(null);}
    private static void set(String name,Object value)throws Exception{field(LobbyManager.class,name).set(null,value);}
    private static void call(String name,Class<?>[] types,Object... args)throws Exception{var m=LobbyManager.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(null,args);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void action(net.minecraft.server.level.ServerPlayer p,String name,String value,String target)throws Exception{
        ((java.util.Map<?,?>)get("LAST_ACTION")).clear();LobbyManager.action(p,new GungangNetwork.Action(name,value,target));
    }
    @SuppressWarnings("unchecked") private static void stability(net.minecraft.server.level.ServerPlayer p)throws Exception{
        var original=(ServerFiles.Loaded)get("files");var selected=get("selected");var mode=get("selectedMode");
        var entries=(java.util.Map<java.util.UUID,Object>)get("PLAYERS");var entry=entries.get(p.getUUID());
        var ready=field(entry.getClass(),"ready");var team=field(entry.getClass(),"team");var originalTeam=team.get(entry);
        var blocker=java.util.UUID.randomUUID();var constructor=entry.getClass().getDeclaredConstructor(net.minecraft.server.level.ServerPlayer.class);constructor.setAccessible(true);
        entries.put(blocker,constructor.newInstance(p)); // Keep allReady false while checking duplicate readiness.
        try{
            var a=ServerFiles.GSON.fromJson("{\"id\":\"smoke-a\",\"name\":\"A\",\"enabled\":true,\"alphaSpawn\":{},\"bravoSpawn\":{}}",ArenaMap.class);
            var b=ServerFiles.GSON.fromJson("{\"id\":\"smoke-b\",\"name\":\"B\",\"enabled\":true,\"alphaSpawn\":{},\"bravoSpawn\":{}}",ArenaMap.class);
            set("files",new ServerFiles.Loaded(original.acoustics(),java.util.List.of(a,b),original.options()));
            set("selected",a.id());set("selectedMode","DEATHMATCH");team.set(entry,"ALPHA");ready.setBoolean(entry,true);
            call("select",new Class<?>[]{String.class},a.id());
            check(ready.getBoolean(entry)&&get("selectedMode").equals("DEATHMATCH"),"Same map reset readiness/mode");
            call("selectMode",new Class<?>[]{String.class},"DEATHMATCH");check(ready.getBoolean(entry),"Same mode reset readiness");
            call("setTeam",new Class<?>[]{net.minecraft.server.level.ServerPlayer.class,String.class},p,"ALPHA");check(ready.getBoolean(entry),"Same team reset readiness");
            action(p,"ready","true",a.id()+":DEATHMATCH");action(p,"ready","true",a.id()+":DEATHMATCH");check(ready.getBoolean(entry),"Duplicate ready toggled off");
            action(p,"mode","PROP_HUNT",b.id());check(get("selectedMode").equals("DEATHMATCH")&&ready.getBoolean(entry),"Stale mode changed current selection");
            action(p,"ready","false",b.id()+":DEATHMATCH");check(ready.getBoolean(entry),"Stale ready changed state");
            call("selectMode",new Class<?>[]{String.class},"PROP_HUNT");check(!ready.getBoolean(entry),"Changed mode retained ready");
            ready.setBoolean(entry,true);call("select",new Class<?>[]{String.class},b.id());
            check(!ready.getBoolean(entry)&&get("selected").equals(b.id())&&get("selectedMode").equals("PROP_HUNT"),"Map change failed to preserve supported mode/reset readiness");
        }finally{
            entries.remove(blocker);team.set(entry,originalTeam);ready.setBoolean(entry,false);set("files",original);set("selected",selected);set("selectedMode",mode);
            ((java.util.Map<?,?>)get("LAST_ACTION")).clear();LobbyManager.reopenLobby(p);
        }
    }
    @SubscribeEvent public static void leave(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event){
        if(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer p)p.server.halt(false);
    }
}
