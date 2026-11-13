package dev.gungang.acoustics.server;

import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 랜덤 무기 (a team deathmatch / free-for-all option). Nobody picks a loadout: right as the match begins, and
 * {@code choice} seconds before each change every {@code rotation} seconds of play, each team gets as many offers
 * (a primary and a secondary each) as it has members, players and bots alike; free-for-all shares one list among
 * everyone. Offers are taken first-come, a pick is final, and whoever has not picked when the choice closes gets
 * one of the offers left at random. The first choice arms a picker at once; later picks are issued at the change.
 */
public final class WeaponRotation {
    record Option(Loadouts.Weapon primary,Loadouts.Weapon secondary) { }
    /** What one viewer sees: the card list, its own pick and the offers others hold, and the times left. */
    public record View(boolean active,boolean open,boolean first,int seconds,int nextSeconds,List<Card> offers,int picked,List<Integer> taken) {
        public static View none(){return new View(false,false,false,0,-1,List.of(),-1,List.of());}
    }
    /** One offer as the client draws it: names, item SNBT for the icons ("" for command-only secondaries), category. */
    /** {@code primaryEn}/{@code secondaryEn}: the weapons' English names (blank: none). */
    public record Card(String primary,String secondary,String primaryItem,String secondaryItem,String category,String primaryEn,String secondaryEn) { }
    private static final class Pool {
        final List<Option> offers;final List<Card> cards;
        /** Offer index each member took; a pick is final. */
        final Map<UUID,Integer> claims=new HashMap<>();
        Pool(List<Option> offers){this.offers=offers;cards=offers.stream().map(WeaponRotation::card).toList();}
        boolean taken(int index){return claims.containsValue(index);}
        List<Integer> free(){var list=new ArrayList<Integer>();for(int i=0;i<offers.size();i++)if(!taken(i))list.add(i);return list;}
    }

    private static MinecraftServer server;
    private static Loadouts loadouts;
    private static boolean active,ffa,open,first;
    private static int rotationTicks,choiceTicks,limitTicks;
    /** Server tick the open choice closes; play tick of the next change (-1: none left before the time limit). */
    private static int closeAt,nextChange=-1,played;
    private static final Map<String,Pool> pools=new HashMap<>();
    /** Bots pick a moment after the choice opens, like a player reading the cards. */
    private static final Map<UUID,Integer> botPickAt=new HashMap<>();
    private static final Map<UUID,String> sent=new HashMap<>();
    private static final Random random=new Random();

    public static boolean active(){return active;}

    /** Called once the match is prepared and everyone is armed; does nothing unless the mode plays 랜덤 무기. */
    static void begin(MinecraftServer s,String mode,GameOptions options,int timeLimitSeconds){
        end();
        if(!options.randomWeapons(mode))return;
        try{loadouts=WeaponToggles.filter(Loadouts.load());}
        catch(Exception error){LogUtils.getLogger().error("[GunGang] 랜덤 무기 disabled: weapons could not be read",error);return;}
        server=s;active=true;ffa=mode.equals("FREE_FOR_ALL");
        var times=options.weaponRotation(mode);rotationTicks=times[0]*20;choiceTicks=times[1]*20;limitTicks=timeLimitSeconds*20;
        played=0;nextChange=rotationTicks<limitTicks||limitTicks<=0?rotationTicks:-1;
        openChoice(true,choiceTicks);
        say(Component.literal("랜덤 무기: "+times[0]/60+"분 "+(times[0]%60>0?times[0]%60+"초 ":"")+"마다 주무기·보조무기가 바뀝니다. 숫자 키로 먼저 고르세요.").withStyle(ChatFormatting.GOLD));
        LogUtils.getLogger().info("[GunGang] 랜덤 무기 on: every {}s, choice {}s, {} pools",times[0],times[1],pools.size());
    }
    static void end(){
        if(server!=null)for(var id:sent.keySet()){var p=server.getPlayerList().getPlayer(id);if(p!=null)GungangNetwork.send(p,new GungangNetwork.WeaponRoll(ServerFiles.GSON.toJson(View.none())));}
        active=open=first=false;pools.clear();botPickAt.clear();sent.clear();nextChange=-1;loadouts=null;
    }
    /** Every server tick of a prepared match; {@code playedTicks} counts only running time. */
    static void tick(boolean running,int playedTicks){
        if(!active||server==null)return;
        played=playedTicks;int now=server.getTickCount();
        if(open){
            for(var e:List.copyOf(botPickAt.entrySet()))if(now>=e.getValue()){botPickAt.remove(e.getKey());botPick(e.getKey());}
            if(now>=closeAt)close();
        }else if(running&&nextChange>0&&played>=nextChange-choiceTicks)openChoice(false,Math.max(1,nextChange-played));
        if(now%4==0)sync();
    }

    // ---- choices ----
    /** Pool key per member: the team, or one shared pool in free-for-all. */
    private static Map<UUID,String> members(){
        var map=new LinkedHashMap<UUID,String>();
        for(var p:server.getPlayerList().getPlayers()){String t=LobbyManager.team(p.getUUID());if(t.equals("ALPHA")||t.equals("BRAVO"))map.put(p.getUUID(),ffa?"ALL":t);}
        for(var a:BotManager.roster())if(a.team.equals("ALPHA")||a.team.equals("BRAVO"))map.put(a.id,ffa?"ALL":a.team);
        return map;
    }
    private static void openChoice(boolean initial,int ticks){
        pools.clear();botPickAt.clear();
        var counts=new HashMap<String,int[]>();
        for(var e:members().entrySet()){var c=counts.computeIfAbsent(e.getValue(),k->new int[2]);c[0]++;if(BotManager.agent(e.getKey())!=null)c[1]++;}
        counts.forEach((key,c)->pools.put(key,new Pool(offers(c[0],c[1]))));
        int now=server.getTickCount();
        for(var a:BotManager.roster())botPickAt.put(a.id,now+20+random.nextInt(Math.max(1,Math.min(ticks-10,100))));
        open=true;first=initial;closeAt=now+ticks;sync();
        if(!initial)say(Component.literal("랜덤 무기: "+(ticks+19)/20+"초 뒤 무기가 바뀝니다. 숫자 키로 다음 무기를 고르세요.").withStyle(ChatFormatting.YELLOW));
    }
    /**
     * {@code count} offers with different primaries and secondaries while the catalog has enough, and at least as many
     * primaries a bot can use as there are bots (when the catalog has them).
     */
    private static List<Option> offers(int count,int bots){
        count=Math.max(1,count);
        var primaries=cycle(loadouts.primary(),count);var secondaries=cycle(loadouts.secondary(),count);
        var usable=new ArrayList<>(loadouts.primary().stream().filter(BotManager::supportedPrimary).filter(w->!primaries.contains(w)).toList());
        Collections.shuffle(usable,random);
        int need=Math.min(bots,count);
        for(int i=0;i<primaries.size()&&primaries.stream().filter(BotManager::supportedPrimary).count()<need&&!usable.isEmpty();i++)
            if(!BotManager.supportedPrimary(primaries.get(i)))primaries.set(i,usable.remove(usable.size()-1));
        Collections.shuffle(primaries,random);
        var list=new ArrayList<Option>();for(int i=0;i<count;i++)list.add(new Option(primaries.get(i),secondaries.get(i)));
        return List.copyOf(list);
    }
    /** {@code count} entries, each catalog weapon once per round in random order. */
    private static List<Loadouts.Weapon> cycle(List<Loadouts.Weapon> catalog,int count){
        var result=new ArrayList<Loadouts.Weapon>();
        while(result.size()<count){var round=new ArrayList<>(catalog);Collections.shuffle(round,random);for(var w:round)if(result.size()<count)result.add(w);}
        return result;
    }
    private static Pool pool(UUID id){var key=members().get(id);return key==null?null:pools.get(key);}
    /** Number key {@code index} (0-based) from a player. */
    static void pick(ServerPlayer p,int index){
        if(!active||!open)return;
        var pool=pool(p.getUUID());if(pool==null||index<0||index>=pool.offers.size())return;
        if(pool.claims.containsKey(p.getUUID())){p.displayClientMessage(Component.literal("이미 무기를 골랐습니다"),true);return;}
        if(pool.taken(index)){p.displayClientMessage(Component.literal("다른 사람이 먼저 고른 무기입니다"),true);sync();return;}
        pool.claims.put(p.getUUID(),index);
        var o=pool.offers.get(index);
        if(first)apply(p.getUUID(),o);
        else p.displayClientMessage(Component.literal("다음 무기: "+o.primary().name()+" · "+o.secondary().name()),true);
        sync();
    }
    /** A bot takes a free offer whose primary it can use, at random. */
    private static void botPick(UUID id){
        var pool=pool(id);if(pool==null||pool.claims.containsKey(id))return;
        var free=pool.free().stream().filter(i->BotManager.supportedPrimary(pool.offers.get(i).primary())).toList();
        if(free.isEmpty())return;
        int index=free.get(random.nextInt(free.size()));pool.claims.put(id,index);
        if(first)apply(id,pool.offers.get(index));
    }
    private static void close(){
        var members=members();
        for(var e:members.entrySet()){
            var pool=pools.get(e.getValue());if(pool==null)continue;
            UUID id=e.getKey();boolean bot=BotManager.agent(id)!=null;
            Integer index=pool.claims.get(id);
            if(index!=null&&first)continue; // already armed with its pick
            if(index==null){
                var free=pool.free().stream().filter(i->!bot||BotManager.supportedPrimary(pool.offers.get(i).primary())).toList();
                if(!free.isEmpty()){index=free.get(random.nextInt(free.size()));pool.claims.put(id,index);}
            }
            apply(id,index!=null?pool.offers.get(index):offers(1,bot?1:0).get(0));
        }
        if(!first&&nextChange>0){nextChange+=rotationTicks;if(limitTicks>0&&nextChange>=limitTicks)nextChange=-1;}
        if(!first)say(Component.literal("랜덤 무기: 무기가 바뀌었습니다.").withStyle(ChatFormatting.GOLD));
        open=false;first=false;pools.clear();botPickAt.clear();sync();
    }
    private static void apply(UUID id,Option o){
        var agent=BotManager.agent(id);
        if(agent!=null){
            var primary=o.primary();
            if(!BotManager.supportedPrimary(primary)){
                var usable=loadouts.primary().stream().filter(BotManager::supportedPrimary).toList();
                if(usable.isEmpty())return;
                primary=usable.get(random.nextInt(usable.size()));
            }
            agent.rotate(primary,o.secondary());return;
        }
        var p=server.getPlayerList().getPlayer(id);if(p==null)return;
        LobbyManager.rotationLoadout(p,o.primary(),o.secondary());
        p.sendSystemMessage(Component.literal("랜덤 무기 · 주무기: "+o.primary().name()+" · 보조무기: "+o.secondary().name()).withStyle(ChatFormatting.AQUA));
    }
    /** A player joining the running match is armed at random (outside the offers) and joins the next choice. */
    static void joined(ServerPlayer p){if(active)apply(p.getUUID(),offers(1,0).get(0));}

    // ---- view ----
    private static Card card(Option o){return new Card(o.primary().name(),o.secondary().name(),item(o.primary()),item(o.secondary()),o.primary().category(),o.primary().nameEn(),o.secondary().nameEn());}
    private static String item(Loadouts.Weapon w){
        try{var stack=w.stack();return stack.isEmpty()?"":stack.save(new net.minecraft.nbt.CompoundTag()).toString();}catch(RuntimeException unreadable){return "";}
    }
    private static View view(UUID id){
        int next=nextChange<0?-1:Math.max(0,(nextChange-played+19)/20);
        var pool=open?pool(id):null;
        if(pool==null)return new View(true,false,false,0,next,List.of(),-1,List.of());
        int picked=pool.claims.getOrDefault(id,-1);
        var taken=new ArrayList<Integer>();for(var e:pool.claims.entrySet())if(!e.getKey().equals(id))taken.add(e.getValue());
        return new View(true,true,first,Math.max(0,(closeAt-server.getTickCount()+19)/20),next,pool.cards,picked,taken);
    }
    private static void sync(){
        for(var p:server.getPlayerList().getPlayers()){
            String team=LobbyManager.team(p.getUUID());
            String json=ServerFiles.GSON.toJson(team.equals("ALPHA")||team.equals("BRAVO")?view(p.getUUID()):View.none());
            if(json.equals(sent.get(p.getUUID())))continue;
            sent.put(p.getUUID(),json);GungangNetwork.send(p,new GungangNetwork.WeaponRoll(json));
        }
    }
    private static void say(Component message){
        for(var p:server.getPlayerList().getPlayers()){String t=LobbyManager.team(p.getUUID());if(t.equals("ALPHA")||t.equals("BRAVO"))p.sendSystemMessage(message);}
    }
    private WeaponRotation(){}
}
