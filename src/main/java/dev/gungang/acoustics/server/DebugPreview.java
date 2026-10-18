package dev.gungang.acoustics.server;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.gungang.acoustics.GungangAcoustics;
import java.util.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/** /gungang debug: sends fake match snapshots to the requesting player only, so HUDs and the TAB scoreboard can be checked without a match. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class DebugPreview {
    private static final List<String> MODES=List.of("control","deathmatch","bomb","escort","normandy","ffa","prophunt","prophunt_hider");
    private record Preview(String mode,int seconds,int players,int startTick) { }
    private static final Map<UUID,Preview> ACTIVE=new HashMap<>();

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        var debug=Commands.literal("debug").requires(s->s.hasPermission(2));
        var hud=Commands.literal("hud");
        for(String mode:MODES)hud.then(Commands.literal(mode).executes(c->hud(c.getSource(),mode,90,8))
                .then(Commands.argument("seconds",IntegerArgumentType.integer(0,3600)).executes(c->hud(c.getSource(),mode,IntegerArgumentType.getInteger(c,"seconds"),8))
                        .then(Commands.argument("players",IntegerArgumentType.integer(1,32)).executes(c->hud(c.getSource(),mode,IntegerArgumentType.getInteger(c,"seconds"),IntegerArgumentType.getInteger(c,"players"))))));
        debug.then(hud);
        debug.then(Commands.literal("scoreboard").executes(c->hud(c.getSource(),"control",90,16))
                .then(Commands.argument("players",IntegerArgumentType.integer(1,32)).executes(c->hud(c.getSource(),"control",90,IntegerArgumentType.getInteger(c,"players")))));
        var result=Commands.literal("result");
        for(String winner:List.of("ALPHA","BRAVO","DRAW"))result.then(Commands.literal(winner.toLowerCase(Locale.ROOT)).executes(c->result(c.getSource(),winner)));
        debug.then(result);
        debug.then(Commands.literal("killfeed").executes(c->killfeed(c.getSource(),5))
                .then(Commands.argument("count",IntegerArgumentType.integer(1,8)).executes(c->killfeed(c.getSource(),IntegerArgumentType.getInteger(c,"count")))));
        debug.then(Commands.literal("prop").then(Commands.argument("id",com.mojang.brigadier.arguments.StringArgumentType.word())
                .suggests((c,b)->{b.suggest("random");b.suggest("tagger");b.suggest("off");PropCatalog.numbers().forEach(b::suggest);return b.buildFuture();})
                .executes(c->prop(c.getSource(),com.mojang.brigadier.arguments.StringArgumentType.getString(c,"id")))));
        debug.then(Commands.literal("off").executes(c->off(c.getSource())));
        root.then(debug);
    }
    private static boolean refuseDuringMatch(CommandSourceStack source) {
        if(!MatchManager.active())return false;
        source.sendFailure(Component.literal("경기 준비·진행 중에는 디버그 미리보기를 사용할 수 없습니다."));return true;
    }
    private static int hud(CommandSourceStack source,String mode,int seconds,int players) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var p=source.getPlayerOrException();
        if(refuseDuringMatch(source))return 0;
        var preview=new Preview(mode,seconds,players,p.server.getTickCount());
        ACTIVE.put(p.getUUID(),preview);send(p,preview);
        p.sendSystemMessage(Component.literal("[디버그] "+mode+" HUD 미리보기 · "+seconds+"초 · "+players+"명. TAB으로 점수판, /gungang debug off로 종료"));
        return 1;
    }
    private static int result(CommandSourceStack source,String winner) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var p=source.getPlayerOrException();
        if(refuseDuringMatch(source))return 0;
        ACTIVE.remove(p.getUUID());
        var rows=rows(p,10);
        int alpha=winner.equals("ALPHA")?100:winner.equals("BRAVO")?62:100,bravo=winner.equals("BRAVO")?100:winner.equals("ALPHA")?71:100;
        GungangNetwork.send(p,new GungangNetwork.Match(ServerFiles.GSON.toJson(new MatchSnapshot(false,true,"디버그 맵","FINISHED",612,false,winner,alpha,bravo,rows,
                new MatchSnapshot.Capture("A",0,false,0,100),LobbyManager.options(),List.of(),612,"clear",""))));
        return 1;
    }
    private static int killfeed(CommandSourceStack source,int count) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var p=source.getPlayerOrException();var random=new Random();
        String[] names={"Alpha_1","Bravo_2","Alpha_3","Bravo_4","Alpha_5","Bravo_6"};
        for(int i=0;i<count;i++) {
            String killer=i==0?p.getGameProfile().getName():names[random.nextInt(names.length)],victim=names[(i*2+1)%names.length];
            // The client kill log reads vanilla death.attack.* messages; this one goes to the requester only.
            p.sendSystemMessage(Component.translatable("death.attack.player",victim,killer));
        }
        return 1;
    }
    /** Real prop hunt controls and health without a match: a block number or random disguises, tagger gives the seeker shotgun, off restores. */
    private static int prop(CommandSourceStack source,String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var p=source.getPlayerOrException();
        if(refuseDuringMatch(source))return 0;
        String value=id.toLowerCase(Locale.ROOT);var random=new Random();
        try{
            switch(value){
                case "off" -> {PropHuntMatch.test(p,null,"");p.sendSystemMessage(Component.literal("[디버그] 변신 테스트를 종료하고 인벤토리·게임 모드·체력을 되돌렸습니다."));}
                case "tagger" -> {ACTIVE.remove(p.getUUID());PropHuntMatch.test(p,"BRAVO","");p.sendSystemMessage(Component.literal("[디버그] 술래 테스트 · 체력 100 · 산탄총으로 변신한 테스트 참가자를 쏠 수 있습니다. /gungang debug prop off로 종료"));}
                default -> {
                    var types=PropCatalog.available();
                    String type=value.equals("random")?types.get(random.nextInt(types.size())):PropCatalog.type(value);
                    if(type==null){source.sendFailure(Component.literal("블록 번호(01~99), random, tagger, off 중 하나를 입력하세요."));return 0;}
                    ACTIVE.remove(p.getUUID());PropHuntMatch.test(p,"ALPHA",PropCatalog.variant(type,random));
                    p.sendSystemMessage(Component.literal("[디버그] 변신 테스트 · 숨는 팀과 같은 조작·체력 50. 다시 입력하면 다른 블록으로 바뀝니다. /gungang debug prop off로 종료"));
                }
            }
        }catch(RuntimeException failure){source.sendFailure(Component.literal(String.valueOf(failure.getMessage())));return 0;}
        return 1;
    }
    private static int off(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var p=source.getPlayerOrException();
        ACTIVE.remove(p.getUUID());PropHuntMatch.test(p,null,"");
        if(!MatchManager.active())clear(p);
        p.sendSystemMessage(Component.literal("[디버그] 미리보기를 종료했습니다."));
        return 1;
    }
    private static void clear(ServerPlayer p) {
        GungangNetwork.send(p,new GungangNetwork.Match(ServerFiles.GSON.toJson(new MatchSnapshot(false,false,"","LOBBY",0,false,"",0,0,List.of()))));
        GungangNetwork.send(p,new GungangNetwork.PropHunt(ServerFiles.GSON.toJson(new PropHuntSnapshot(false,"",0,0,List.of()))));
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||ACTIVE.isEmpty())return;
        var server=ServerLifecycleHooks.getCurrentServer();if(server==null)return;
        // A real match owns the HUD; its snapshots replace the preview.
        if(MatchManager.active()){ACTIVE.clear();return;}
        if(server.getTickCount()%4!=0)return;
        ACTIVE.entrySet().removeIf(entry->{
            var p=server.getPlayerList().getPlayer(entry.getKey());
            if(p==null)return true;
            send(p,entry.getValue());return false;
        });
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {ACTIVE.remove(event.getEntity().getUUID());}

    private static void send(ServerPlayer p,Preview preview) {
        int elapsed=(p.server.getTickCount()-preview.startTick())/20,seconds=Math.max(0,preview.seconds()-elapsed);
        if(preview.mode().startsWith("prophunt")){propHunt(p,preview,elapsed);return;}
        float wave=(float)Math.sin(elapsed/3.0);
        MatchSnapshot.Capture point;BombMatch.View bomb=null;EscortMatch.View escort=null;
        int alpha,bravo;String phase="RUNNING";boolean countdown=true;
        switch(preview.mode()) {
            case "deathmatch" -> {point=new MatchSnapshot.Capture("",0,false,0,50,"DEATHMATCH",0);alpha=Math.min(50,18+elapsed/4);bravo=Math.min(50,15+elapsed/5);}
            case "bomb" -> {
                point=new MatchSnapshot.Capture("",0,false,0,4,"BOMB",3);alpha=2;bravo=1;phase="RUNNING";
                bomb=new BombMatch.View(3,7,"ALPHA","RUNNING",new BombRules.Point(0,0,0),new BombRules.Point(0,0,0),"",0,"");
            }
            case "escort" -> {
                point=new MatchSnapshot.Capture("호위",0,false,0,2,"ESCORT",1);alpha=1;bravo=0;
                double distance=Math.min(100,40+elapsed*.5);
                escort=new EscortMatch.View(distance,100,List.of(33d,66d),distance>=66?2:1,elapsed%10<6?"FORWARD":"CONTESTED",1.5,0,0,0,0);
            }
            case "ffa" -> {point=new MatchSnapshot.Capture("",0,false,0,16,"FREE_FOR_ALL",0);alpha=0;bravo=0;}
            case "normandy" -> {point=new MatchSnapshot.Capture("B 거점",-Math.min(100,elapsed*4%100),wave>0,-1,4,"NORMANDY",1);alpha=0;bravo=0;}
            default -> {
                point=new MatchSnapshot.Capture("A 거점",Math.max(-100,Math.min(100,wave*100)),Math.abs(wave)<.3f,wave>.5f?1:wave<-.5f?-1:0,100);
                alpha=Math.min(100,40+elapsed/2);bravo=Math.min(100,35+elapsed/3);countdown=preview.seconds()>0;
            }
        }
        var rows=preview.mode().equals("ffa")?ffaRows(p,preview.players(),elapsed):rows(p,preview.players());
        var snapshot=new MatchSnapshot(true,false,"디버그 맵",phase,seconds,countdown,"",alpha,bravo,rows,point,LobbyManager.options(),List.of(),
                elapsed,"clear","",bomb,"day",escort);
        GungangNetwork.send(p,new GungangNetwork.Match(ServerFiles.GSON.toJson(snapshot)));
    }
    private static final List<String> PROPS=List.of("minecraft:barrel","minecraft:oak_log","minecraft:flower_pot","minecraft:crafting_table",
            "minecraft:hay_block","minecraft:bookshelf","minecraft:lantern","minecraft:cauldron");
    private static final int PROP_HIDE=10,PROP_CHANGE=20,PROP_FIND=8,PROP_REVIEW=30;
    /**
     * Prop hunt: 10s hiding, then one hider is found every 8s and disguises rotate every 20s; when the hunt time runs out it
     * switches to the review phase. The requester is a seeker, or an unfound hider in prophunt_hider (which turns on hider controls).
     */
    private static void propHunt(ServerPlayer p,Preview preview,int elapsed) {
        boolean asHider=preview.mode().equals("prophunt_hider");
        int total=Math.max(2,preview.players()),seekerCount=Math.max(1,total/3),hiderCount=total-seekerCount;
        int hunt=Math.max(0,elapsed-PROP_HIDE),left=preview.seconds()-hunt;
        String phase=elapsed<PROP_HIDE?"HIDING":left>0?"HUNTING":"REVIEW";
        int seconds=switch(phase){case "HIDING"->PROP_HIDE-elapsed;case "HUNTING"->left;default->Math.max(0,PROP_REVIEW+left);};
        int change=phase.equals("REVIEW")?0:phase.equals("HIDING")?PROP_HIDE-elapsed+PROP_CHANGE:PROP_CHANGE-hunt%PROP_CHANGE;
        // Hider 0 is the requester in hider mode and is never found, so the controls stay testable.
        int found=Math.min(hiderCount-(asHider?1:0),hunt/PROP_FIND);
        var rows=new ArrayList<PropHuntSnapshot.Row>();var matchRows=new ArrayList<MatchSnapshot.Row>();
        int[] finds=new int[seekerCount];for(int i=0;i<found;i++)finds[i%seekerCount]++;
        for(int i=0;i<total;i++) {
            boolean hider=i<hiderCount;int index=hider?i:i-hiderCount;
            boolean me=asHider?hider&&index==0:!hider&&index==0;
            String id=me?p.getUUID().toString():UUID.nameUUIDFromBytes(("gungang-debug-prop-"+i).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            String name=me?p.getGameProfile().getName():(hider?"Hider_":"Seeker_")+i;
            int order=asHider?index-1:index;
            boolean isFound=hider&&order>=0&&order<found;
            int hidden=hider?(isFound?(order+1)*PROP_FIND:Math.min(hunt,preview.seconds())):0;
            boolean visible=asHider||isFound||phase.equals("REVIEW");
            String block=hider&&visible?PROPS.get((i+(elapsed<PROP_HIDE?0:hunt/PROP_CHANGE+1))%PROPS.size()):"";
            rows.add(new PropHuntSnapshot.Row(id,name,hider?"ALPHA":"BRAVO",block,0,-1,isFound,false,hidden,hider?0:finds[index]));
            matchRows.add(new MatchSnapshot.Row(id,name,hider?"ALPHA":"BRAVO",hider?0:finds[index],0,0,0,hidden));
        }
        var point=new MatchSnapshot.Capture("숨바꼭질",0,false,0,0,"PROP_HUNT",0);
        GungangNetwork.send(p,new GungangNetwork.Match(ServerFiles.GSON.toJson(new MatchSnapshot(true,false,"디버그 맵","RUNNING",seconds,true,"",0,0,matchRows,point,LobbyManager.options(),List.of(),
                elapsed,"clear","",null,"day",null))));
        GungangNetwork.send(p,new GungangNetwork.PropHunt(ServerFiles.GSON.toJson(new PropHuntSnapshot(true,phase,seconds,change,rows))));
    }
    private static final int[] FFA_COLORS={0xFF5555,0x55AAFF,0x55FF55,0xFFAA00,0xAA55FF,0x55FFFF,0xFF55FF,0xFFFF55};
    /** Free-for-all rows ranked by score; the requester climbs over time so rank chips and the lead tag change. */
    private static List<MatchSnapshot.Row> ffaRows(ServerPlayer p,int players,int elapsed) {
        var rows=new ArrayList<MatchSnapshot.Row>();
        for(int i=0;i<players;i++) {
            boolean me=i==0;
            String id=me?p.getUUID().toString():UUID.nameUUIDFromBytes(("gungang-debug-"+i).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            int score=me?Math.min(16,3+elapsed/6):Math.max(0,12-i*2+(i==1?elapsed/12:0));
            rows.add(new MatchSnapshot.Row(id,me?p.getGameProfile().getName():"Player_"+i,id,score,(i*3)%7,i%3,score*95.5+i*12,600,score,FFA_COLORS[i%FFA_COLORS.length],0,0));
        }
        rows.sort(Comparator.comparingInt(MatchSnapshot.Row::score).reversed());
        return rows;
    }
    /** The requester first (so their own row is highlighted), then alternating fake ALPHA/BRAVO players with stable stats. */
    private static List<MatchSnapshot.Row> rows(ServerPlayer p,int players) {
        var rows=new ArrayList<MatchSnapshot.Row>();
        rows.add(new MatchSnapshot.Row(p.getUUID().toString(),p.getGameProfile().getName(),"ALPHA",12,4,3,1840.5,600));
        for(int i=1;i<players;i++) {
            String team=i%2==0?"ALPHA":"BRAVO";
            var id=UUID.nameUUIDFromBytes(("gungang-debug-"+i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            rows.add(new MatchSnapshot.Row(id.toString(),(team.equals("ALPHA")?"Alpha_":"Bravo_")+i,team,(i*7)%15,(i*5)%9,i%4,(i*317)%2000+120.5,600-i*10));
        }
        return rows;
    }
    private DebugPreview() { }
}
