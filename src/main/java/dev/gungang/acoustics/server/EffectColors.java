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
            new Effect("rapier","28 레이피어","돌진 잔상·섬광",0xB3E6FF,-1),
            new Effect("las5","27 LAS-5","레이저 광선",0xFFDB1A,-1),
            new Effect("m95","34 M95","탄도선",0x85DBD9,-1),
            new Effect("shuriken","33 수리검","비행 궤적",0xB852FF,-1),
            new Effect("orbital_strike","87 궤도폭격","조준 링·광선",0xFF0DD9,-1),
            new Effect("jetpack","93 제트팩","비행 글로우",0xFF3B2E,-1),
            new Effect("turret","90 터렛","레이저 섬광 탄환",0xFF3B2E,-1))
            .stream().sorted(Comparator.comparingInt(e->Integer.parseInt(e.weapon().substring(0,e.weapon().indexOf(' ')))))
            .toList();
    /** Colours offered in the picker: a bright row, a pastel row, a deep row and neutrals/metals. */
    public static final int[] PALETTE={
            0xFF4D4D,0xFF9933,0xFFD84D,0xC6FF4D,0x4DFF6A,0x4DFFD2,0x4DE1FF,0x4D9BFF,
            0x6A4DFF,0xA64DFF,0xFF4DE1,0xFF8FB8,0xFFB3B3,0xFFD9A3,0xFFF3B0,0xD9FFB3,
            0xB3FFD9,0xB3E6FF,0xC9C2FF,0xF0C2FF,0xB31A1A,0xC25E00,0xA88A00,0x2E8B2E,
            0x007A7A,0x1F4FB3,0x5A1FB3,0x99006B,0xFFFFFF,0xC0C6CE,0xD4AF37,0x9AA3AD};
    public static Effect effect(String key){for(var e:EFFECTS)if(e.key().equals(key))return e;return null;}

    private static final Map<UUID,Map<String,int[]>> SERVER=new ConcurrentHashMap<>();
    private static volatile Map<UUID,Map<String,int[]>> client=Map.of();
    /** Client: the local player's saved palette (PlayerRecord.savedColors). */
    private static volatile List<Integer> saved=List.of();

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
    /** Adds ({@code keep}) or removes one "RRGGBB" colour of the player's saved palette. */
    static void savedColor(ServerPlayer p,String value,boolean keep)throws java.io.IOException{
        int rgb=hex(value==null?"":value);var record=ProgressionService.record(p);
        if(keep&&!record.savedColors().contains(rgb)&&record.savedColors().size()>=PlayerRecord.SAVED_COLORS)
            p.displayClientMessage(net.minecraft.network.chat.Component.literal("저장 색상이 가득 차 가장 오래된 색을 지웠습니다."),true);
        ProgressionService.store(keep?record.withSavedColor(rgb):record.withoutSavedColor(rgb));
        sync(p,table(p.server));
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
        var table=table(server);
        for(var p:server.getPlayerList().getPlayers())sync(p,table);
    }
    private static Map<String,Map<String,int[]>> table(net.minecraft.server.MinecraftServer server){
        var table=new HashMap<String,Map<String,int[]>>();
        for(var p:server.getPlayerList().getPlayers()){var map=SERVER.get(p.getUUID());if(map!=null&&!map.isEmpty())table.put(p.getUUID().toString(),map);}
        return table;
    }
    /** Everyone's colours plus the receiver's own saved palette: {"table": {...}, "saved": [rgb ...]}. */
    private static void sync(ServerPlayer p,Map<String,Map<String,int[]>> table){
        List<Integer> own;
        try{own=ProgressionService.record(p).savedColors();}catch(IllegalStateException unavailable){own=List.of();}
        var json=new com.google.gson.JsonObject();json.add("table",ServerFiles.GSON.toJsonTree(table));json.add("saved",ServerFiles.GSON.toJsonTree(own));
        GungangNetwork.send(p,new GungangNetwork.EffectColorSync(ServerFiles.GSON.toJson(json)));
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
        var root=com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        var tree=root.has("table")?root.getAsJsonObject("table"):new com.google.gson.JsonObject();
        var own=new ArrayList<Integer>();
        if(root.has("saved"))for(var c:root.getAsJsonArray("saved"))if(own.size()<PlayerRecord.SAVED_COLORS)own.add(c.getAsInt()&0xFFFFFF);
        saved=List.copyOf(own);
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
    /** Client: the local player's saved colours, oldest first. */
    public static List<Integer> saved(){return saved;}
    public static void clearClient(){client=Map.of();saved=List.of();}
    private EffectColors(){}
}
