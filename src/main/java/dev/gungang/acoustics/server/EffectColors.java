package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Per-player effect colours: each player picks up to two colours for a weapon effect in 무기 선택 > 프리셋 >
 * 이펙트 색상; two are blended (a gradient along trails, a slow pulse on beams). Choices live in the player's
 * record in player-db.json ({@link PlayerRecord#effectColors()}). Everyone sees an owner's colours, so the server
 * sends the table of online players to every client whenever it changes.
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class EffectColors {
    /** {@code first}/{@code second}: the default colours (second -1: a single colour). */
    public record Effect(String key,String weapon,String label,int first,int second) { }
    public static final List<Effect> EFFECTS=List.of(
            new Effect("hermes_boots","74 헤르메스의 부츠","돌진 글로우",0x6FE8FF,0xFFFFFF),
            new Effect("rapier","28 레이피어","돌진 잔상",0xB3E6FF,-1),
            new Effect("las5","27 LAS-5","레이저 광선",0xFFDB1A,-1),
            new Effect("m95","34 M95","탄도선",0x85DBD9,-1),
            new Effect("shuriken","33 수리검","비행 궤적",0xB852FF,-1),
            new Effect("orbital_strike","87 궤도폭격","조준 링·광선",0xFF0DD9,-1));
    /** Colours offered in the picker. */
    public static final int[] PALETTE={
            0xFFFFFF,0xFF4D4D,0xFF9933,0xFFD84D,0xC6FF4D,0x4DFF6A,0x4DFFD2,0x4DE1FF,
            0x4D9BFF,0x6A4DFF,0xA64DFF,0xFF4DE1,0xFF8FB8,0xB3E6FF,0xFFE8A3,0x9AA3AD};
    public static Effect effect(String key){for(var e:EFFECTS)if(e.key().equals(key))return e;return null;}

    private static final Map<UUID,Map<String,int[]>> SERVER=new ConcurrentHashMap<>();
    private static volatile Map<UUID,Map<String,int[]>> client=Map.of();

    /** The owner's colours for {@code key} as {first, second(-1: none)}; defaults when unset or unknown. */
    public static int[] colors(boolean clientSide,UUID owner,String key){
        var chosen=owner==null?null:(clientSide?client:SERVER).getOrDefault(owner,Map.of()).get(key);
        if(chosen!=null)return chosen;
        var e=effect(key);return e==null?new int[]{0xFFFFFF,-1}:new int[]{e.first(),e.second()};
    }
    public static int[] client(UUID owner,String key){return colors(true,owner,key);}
    public static int[] server(UUID owner,String key){return colors(false,owner,key);}
    /** The colour at {@code t} (0..1) between the two; a single colour everywhere when there is no second. */
    public static int at(int[] c,float t){
        if(c[1]<0)return c[0];
        t=Math.max(0,Math.min(1,t));
        int r=Math.round(((c[0]>>16)&255)*(1-t)+((c[1]>>16)&255)*t),g=Math.round(((c[0]>>8)&255)*(1-t)+((c[1]>>8)&255)*t),b=Math.round((c[0]&255)*(1-t)+(c[1]&255)*t);
        return r<<16|g<<8|b;
    }
    /** The two colours swinging back and forth over about a second. */
    public static int pulse(int[] c,double seconds){return at(c,(float)(.5+.5*Math.sin(seconds*Math.PI*1.6)));}
    public static float red(int rgb){return ((rgb>>16)&255)/255f;}
    public static float green(int rgb){return ((rgb>>8)&255)/255f;}
    public static float blue(int rgb){return (rgb&255)/255f;}
    public static org.joml.Vector3f vector(int rgb){return new org.joml.Vector3f(red(rgb),green(rgb),blue(rgb));}

    /** "RRGGBB" or "RRGGBB,RRGGBB"; empty restores the default. */
    static void choose(ServerPlayer p,String key,String value)throws java.io.IOException{
        var e=effect(key);if(e==null)throw new IllegalArgumentException("알 수 없는 이펙트입니다.");
        PlayerRecord.EffectColor color=null;
        if(value!=null&&!value.isBlank()){
            String[] parts=value.strip().split(",");
            if(parts.length>2)throw new IllegalArgumentException("색상은 최대 2개까지 고를 수 있습니다.");
            int first=hex(parts[0]),second=parts.length>1?hex(parts[1]):-1;
            if(second==first)second=-1;
            color=new PlayerRecord.EffectColor(key,first,second);
        }
        ProgressionService.store(ProgressionService.record(p).withEffectColor(key,color));
        load(p);broadcast(p.server);
    }
    private static int hex(String text){
        if(!text.strip().matches("[0-9A-Fa-f]{6}"))throw new IllegalArgumentException("색상은 RRGGBB 형식이어야 합니다.");
        return Integer.parseInt(text.strip(),16);
    }
    static void load(ServerPlayer p){
        try{
            var map=new HashMap<String,int[]>();
            for(var c:ProgressionService.record(p).effectColors())if(effect(c.key())!=null)map.put(c.key(),new int[]{c.first(),c.second()});
            SERVER.put(p.getUUID(),map);
        }catch(IllegalStateException unavailable){SERVER.remove(p.getUUID());}
    }
    static void broadcast(net.minecraft.server.MinecraftServer server){
        if(server==null)return;
        var table=new HashMap<String,Map<String,int[]>>();
        for(var p:server.getPlayerList().getPlayers()){var map=SERVER.get(p.getUUID());if(map!=null&&!map.isEmpty())table.put(p.getUUID().toString(),map);}
        String json=ServerFiles.GSON.toJson(table);
        for(var p:server.getPlayerList().getPlayers())GungangNetwork.send(p,new GungangNetwork.EffectColorSync(json));
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(event.getEntity() instanceof ServerPlayer p){load(p);broadcast(p.server);}
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){
        if(event.getEntity() instanceof ServerPlayer p&&SERVER.remove(p.getUUID())!=null)broadcast(p.server);
    }

    /** Client: the table the server sent. */
    public static void receive(String json){
        var parsed=new HashMap<UUID,Map<String,int[]>>();
        var tree=com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        for(var row:tree.entrySet()){
            var colors=new HashMap<String,int[]>();
            for(var entry:row.getValue().getAsJsonObject().entrySet()){
                var array=entry.getValue().getAsJsonArray();if(array.size()!=2||effect(entry.getKey())==null)continue;
                colors.put(entry.getKey(),new int[]{array.get(0).getAsInt()&0xFFFFFF,array.get(1).getAsInt()<0?-1:array.get(1).getAsInt()&0xFFFFFF});
            }
            parsed.put(UUID.fromString(row.getKey()),Map.copyOf(colors));
        }
        client=Map.copyOf(parsed);
    }
    /** Client: whether the owner picked their own colours for {@code key} (otherwise the defaults apply). */
    public static boolean customized(UUID owner,String key){return owner!=null&&client.getOrDefault(owner,Map.of()).containsKey(key);}
    public static void clearClient(){client=Map.of();}
    private EffectColors(){}
}
