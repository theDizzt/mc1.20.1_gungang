package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.*;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Lobby game options: the selected mode's own section and the common rules. Blank fields keep the map's value, shown
 * as the placeholder; seconds fields show their minutes and seconds beside the box.
 */
final class GameOptionsScreen extends ArenaScreen {
    private enum Kind { HEADER, SECONDS, DECIMAL_SECONDS, INTEGER, DECIMAL, TEXT, TOGGLE, CHOICE, WEAPONS }
    /** One line of the form: its value lives in {@link #values} under {@code key}. */
    private record Row(String key,String label,Kind kind,String placeholder,Supplier<Boolean> enabled,Function<String,String> note){
        Row(String key,String label,Kind kind,String placeholder){this(key,label,kind,placeholder,()->true,null);}
    }
    private static final int TOP=60,ROW=24,BOX=84,NOTE=118;
    private LobbySnapshot state;
    private final Map<String,String> values=new HashMap<>();
    private final List<Row> rows=new ArrayList<>();
    /** Visible boxes by key: their enabled state follows the other fields as they are typed. */
    private final Map<Row,EditBox> boxes=new HashMap<>();
    private String error="";
    private int scroll;
    GameOptionsScreen(LobbySnapshot state){
        super("게임 옵션");this.state=state;load(state.options().game());
    }
    void update(LobbySnapshot next){
        boolean oldEditable=editable();String oldMode=state.selectedMode(),oldMap=state.selectedMap();state=next;
        if(oldEditable!=editable()||!oldMode.equals(next.selectedMode())||!oldMap.equals(next.selectedMap()))rebuildWidgets();
    }
    void receiveError(String message){error=message==null||message.isBlank()?"게임 옵션을 적용하지 못했습니다.":message;}
    private boolean editable(){return state.administrator()&&state.phase().equals("LOBBY");}
    private String mode(){return state.selectedMode();}
    private GameOptions defaults(){return state.defaults();}

    // ---- Values ----------------------------------------------------------------------------------------------

    private void load(GameOptions o){
        values.clear();
        var c=o.common();
        put("common.dupPrimary",c.duplicatePrimary());put("common.dupSecondary",c.duplicateSecondary());put("common.respawn",c.respawnSeconds());
        put("common.handicapSecondary",c.handicapSecondary());put("common.handicapHealth",c.handicapHealth());put("common.handicapLargeHealth",c.handicapLargeHealth());
        var cp=o.control();put("control.time",cp.timeSeconds());put("control.prep",cp.preparationSeconds());put("control.capture",cp.captureSeconds());put("control.score",cp.targetScore());
        var n=o.normandy();put("normandy.time",n.timeSeconds());put("normandy.prep",n.preparationSeconds());
        for(int i=0;i<n.captureSeconds().size();i++)put("normandy.capture."+i,n.captureSeconds().get(i));
        var p=o.propHunt();put("prop.time",p.timeSeconds());put("prop.prep",p.preparationSeconds());put("prop.changes",p.changes());
        values.put("prop.excluded",String.join(" ",p.excluded()));
        put("prop.choice",p.choice()==null?null:p.choice().name());put("prop.cling",p.cling());
        var e=o.escort();put("escort.time",e.timeSeconds());put("escort.prep",e.preparationSeconds());put("escort.overtime",e.overtimeSeconds());
        for(int i=0;i<e.bonusSeconds().size();i++)put("escort.bonus."+i,e.bonusSeconds().get(i));
        put("escort.speed",e.speed());put("escort.max",e.maxMultiplier());put("escort.reverse",e.reverseSpeed());put("escort.stop",e.stopSeconds());
        var b=o.bomb();put("bomb.time",b.timeSeconds());put("bomb.prep",b.preparationSeconds());put("bomb.rounds",b.rounds());put("bomb.swap",b.swapEveryRounds());
        put("bomb.plant",b.plantSeconds());put("bomb.fuse",b.fuseSeconds());put("bomb.defuse",b.defuseSeconds());
        var d=o.deathmatch();put("tdm.time",d.timeSeconds());put("tdm.prep",d.preparationSeconds());put("tdm.score",d.targetScore());
        put("tdm.weapons",d.weaponMode()==null?null:d.weaponMode().name());put("tdm.rotation",d.rotationSeconds());put("tdm.choice",d.choiceSeconds());
        var f=o.freeForAll();put("ffa.time",f.timeSeconds());put("ffa.prep",f.preparationSeconds());put("ffa.protection",f.protectionSeconds());put("ffa.score",f.targetScore());
        put("ffa.weapons",f.weaponMode()==null?null:f.weaponMode().name());put("ffa.rotation",f.rotationSeconds());put("ffa.choice",f.choiceSeconds());
    }
    private void put(String key,Object value){values.put(key,value==null?"":value instanceof Double v?number(v):value.toString());}
    private String value(String key){return values.getOrDefault(key,"");}
    private Integer integer(String key){String v=value(key).strip();return v.isEmpty()?null:Integer.valueOf(v);}
    private Double decimal(String key){String v=value(key).strip();return v.isEmpty()||v.equals(".")?null:Double.valueOf(v);}
    private Boolean bool(String key){String v=value(key);return v.isEmpty()?null:Boolean.valueOf(v);}
    private List<Integer> integers(String prefix,int count){
        var list=new ArrayList<Integer>();for(int i=0;i<count;i++)list.add(integer(prefix+i));
        while(!list.isEmpty()&&list.get(list.size()-1)==null)list.remove(list.size()-1);
        return list;
    }
    private GameOptions.WeaponMode weaponMode(String key){String v=value(key);return v.isEmpty()?null:GameOptions.WeaponMode.valueOf(v);}
    /** Whether the section under {@code prefix} ("tdm." / "ffa.") is set to 랜덤 무기. */
    private boolean randomWeapons(String prefix){return weaponMode(prefix+"weapons")==GameOptions.WeaponMode.RANDOM;}
    /** The edited sections over the saved options, so other modes' settings are kept. */
    private GameOptions collect(){
        var saved=state.options().game();
        var common=new GameOptions.Common(bool("common.dupPrimary"),bool("common.dupSecondary"),integer("common.respawn"),bool("common.handicapSecondary"),decimal("common.handicapHealth"),integer("common.handicapLargeHealth"));
        var control=new GameOptions.Control(integer("control.time"),integer("control.prep"),integer("control.capture"),integer("control.score"));
        var normandy=new GameOptions.Normandy(integer("normandy.time"),integer("normandy.prep"),integers("normandy.capture.",Math.max(points().size(),saved.normandy().captureSeconds().size())));
        String choice=value("prop.choice");
        var excluded=Arrays.stream(value("prop.excluded").split("[,\\s]+")).filter(s->!s.isBlank()).toList();
        var prop=new GameOptions.PropHunt(integer("prop.time"),integer("prop.prep"),excluded,integer("prop.changes"),choice.isEmpty()?null:GameOptions.Choice.valueOf(choice),bool("prop.cling"));
        var escort=new GameOptions.Escort(integer("escort.time"),integer("escort.prep"),integers("escort.bonus.",3),integer("escort.overtime"),decimal("escort.speed"),decimal("escort.max"),decimal("escort.reverse"),integer("escort.stop"));
        var bomb=new GameOptions.Bomb(integer("bomb.time"),integer("bomb.prep"),integer("bomb.rounds"),integer("bomb.swap"),decimal("bomb.plant"),decimal("bomb.fuse"),decimal("bomb.defuse"));
        var tdm=new GameOptions.Deathmatch(integer("tdm.time"),integer("tdm.prep"),integer("tdm.score"),weaponMode("tdm.weapons"),integer("tdm.rotation"),integer("tdm.choice"));
        var ffa=new GameOptions.FreeForAll(integer("ffa.time"),integer("ffa.prep"),integer("ffa.protection"),integer("ffa.score"),weaponMode("ffa.weapons"),integer("ffa.rotation"),integer("ffa.choice"));
        return new GameOptions(common,control,normandy,prop,escort,bomb,tdm,ffa);
    }
    /** Clears the selected mode's section and the common rules back to the map's values. */
    private void reset(){
        String prefix=switch(mode()){case "CONTROL"->"control.";case "NORMANDY"->"normandy.";case "PROP_HUNT"->"prop.";case "ESCORT"->"escort.";case "BOMB"->"bomb.";case "DEATHMATCH"->"tdm.";case "FREE_FOR_ALL"->"ffa.";default->"-";};
        values.replaceAll((k,v)->k.startsWith(prefix)||k.startsWith("common.")?"":v);error="";rebuildWidgets();
    }

    // ---- Rows ------------------------------------------------------------------------------------------------

    private List<String> points(){return state.points();}
    private static String text(Number value){return value==null?"":value instanceof Double d?number(d):value.toString();}
    private void buildRows(){
        rows.clear();var d=defaults();
        switch(mode()){
            case "CONTROL"->{
                var c=d.control();header("점령전 (쟁탈전)");
                time("control.time",c.timeSeconds());prep("control.prep",c.preparationSeconds());
                rows.add(new Row("control.capture","점령 속도 (완전 점령까지 걸리는 초)",Kind.SECONDS,text(c.captureSeconds())));
                rows.add(new Row("control.score","목표 점수",Kind.INTEGER,text(c.targetScore())));
            }
            case "NORMANDY"->{
                var n=d.normandy();header("이동 점령");
                time("normandy.time",n.timeSeconds());prep("normandy.prep",n.preparationSeconds());
                int count=Math.max(points().size(),n.captureSeconds().size());
                for(int i=0;i<count;i++){
                    String name=i<points().size()?points().get(i):Integer.toString(i+1);
                    rows.add(new Row("normandy.capture."+i,"점령 속도 · "+name+" 거점 (점령까지 초)",Kind.SECONDS,text(n.capture(i))));
                }
            }
            case "PROP_HUNT"->{
                var p=d.propHunt();header("숨바꼭질");
                rows.add(new Row("prop.time","게임 제한 시간 (찾는 시간, 초)",Kind.SECONDS,text(p.timeSeconds())));
                rows.add(new Row("prop.prep","게임 준비 시간 (숨는 시간, 초)",Kind.SECONDS,text(p.preparationSeconds())));
                rows.add(new Row("prop.excluded","제외 블록 (블록 번호, 띄어쓰기/쉼표 구분)",Kind.TEXT,"없음",()->true,this::excludedNote));
                rows.add(new Row("prop.changes","변신 횟수",Kind.INTEGER,text(p.changes()),()->hunt()>=120,s->changesNote()));
                rows.add(new Row("prop.choice","변신 시 블록 선택",Kind.CHOICE,"BASIC"));
                rows.add(new Row("prop.cling","Q키로 벽에 붙기",Kind.TOGGLE,"true"));
            }
            case "ESCORT"->{
                var e=d.escort();header("호위 임무");
                rows.add(new Row("escort.time","게임 제한 시간 (시작 시 주어지는 시간, 초)",Kind.SECONDS,text(e.timeSeconds())));
                prep("escort.prep",e.preparationSeconds());
                for(int i=0;i<3;i++)rows.add(new Row("escort.bonus."+i,"체크포인트 "+(i+1)+" 통과 후 추가 시간 (초)",Kind.SECONDS,text(e.bonus(i))));
                rows.add(new Row("escort.overtime","제한 시간 종료 후 추가 시간 (초)",Kind.SECONDS,text(e.overtimeSeconds())));
                rows.add(new Row("escort.speed","수레 속도 (블록/초)",Kind.DECIMAL,text(e.speed())));
                rows.add(new Row("escort.max","수레 최대 배속 (4명 이상일 때, 최소 1.00)",Kind.DECIMAL,text(e.maxMultiplier()),()->true,this::multiplierNote));
                rows.add(new Row("escort.reverse","수레 후진 속도 (블록/초)",Kind.DECIMAL,text(e.reverseSpeed())));
                rows.add(new Row("escort.stop","체크포인트 도달 시 정차 시간 (초)",Kind.SECONDS,text(e.stopSeconds())));
            }
            case "BOMB"->{
                var b=d.bomb();header("폭탄전 (실험 중)");
                rows.add(new Row("bomb.time","게임 제한 시간 (라운드별, 초)",Kind.SECONDS,text(b.timeSeconds())));
                prep("bomb.prep",b.preparationSeconds());
                rows.add(new Row("bomb.rounds","라운드 수 (최소 2)",Kind.INTEGER,text(b.rounds()),()->true,s->roundsNote()));
                rows.add(new Row("bomb.swap","공수교대 라운드 수 (최소 1)",Kind.INTEGER,text(b.swapEveryRounds()),()->true,s->swapNote()));
                rows.add(new Row("bomb.plant","폭탄 설치 시간 (초)",Kind.DECIMAL_SECONDS,text(b.plantSeconds())));
                rows.add(new Row("bomb.fuse","폭탄 폭파 시간 (초)",Kind.DECIMAL_SECONDS,text(b.fuseSeconds())));
                rows.add(new Row("bomb.defuse","폭탄 해체 시간 (초)",Kind.DECIMAL_SECONDS,text(b.defuseSeconds())));
            }
            case "DEATHMATCH"->{
                var t=d.deathmatch();header("팀 데스매치");
                weapons("tdm.");
                time("tdm.time",randomWeapons("tdm.")?Integer.valueOf(GameOptions.RANDOM_TIME_SECONDS):t.timeSeconds());prep("tdm.prep",t.preparationSeconds());
                rows.add(new Row("tdm.score","목표 점수 (킬)",Kind.INTEGER,text(t.targetScore())));
                rotation("tdm.");
            }
            case "FREE_FOR_ALL"->{
                var f=d.freeForAll();header("개인전");
                weapons("ffa.");
                time("ffa.time",randomWeapons("ffa.")?Integer.valueOf(GameOptions.RANDOM_TIME_SECONDS):f.timeSeconds());
                rows.add(new Row("ffa.prep","게임 준비 시간 (시작 무적, 제한 시간에 포함, 초)",Kind.SECONDS,text(f.preparationSeconds())));
                rows.add(new Row("ffa.protection","리스폰 후 무적 시간 (초)",Kind.SECONDS,text(f.protectionSeconds())));
                rows.add(new Row("ffa.score","목표 점수",Kind.INTEGER,text(f.targetScore())));
                rotation("ffa.");
            }
            default->{}
        }
        var c=d.common();boolean team=GameOptions.teamMode(mode());
        header("공통 설정");
        rows.add(new Row("common.handicapSecondary","핸디캡: 인원 많은 팀 보조무기 금지",Kind.TOGGLE,"false",()->team,s->team?"":"팀 모드 전용"));
        rows.add(new Row("common.handicapLargeHealth","핸디캡: 인원 많은 팀 체력 (50~100%)",Kind.INTEGER,text(c.handicapLargeHealth()),()->team,s->team?"%":"팀 모드 전용"));
        rows.add(new Row("common.handicapHealth","핸디캡: 인원 적은 팀 체력 배수 (1.00~5.00)",Kind.DECIMAL,text(c.handicapHealth()),()->team,s->team?"x 배":"팀 모드 전용"));
        rows.add(new Row("common.dupPrimary","같은 팀 주무기 중복",Kind.TOGGLE,"false"));
        rows.add(new Row("common.dupSecondary","같은 팀 보조무기 중복",Kind.TOGGLE,"true"));
        rows.add(new Row("common.respawn","리스폰 시간 (초)",Kind.SECONDS,text(c.respawnSeconds()),()->!mode().equals("PROP_HUNT"),s->mode().equals("PROP_HUNT")?"숨바꼭질 미사용":null));
    }
    /** 무기 방식: 클래식 (each picks a loadout), 랜덤 무기, or 근접전 (melee primaries only). */
    private void weapons(String prefix){rows.add(new Row(prefix+"weapons","무기 방식",Kind.WEAPONS,"CLASSIC"));}
    /** 랜덤 무기 timing, editable only while the section plays 랜덤 무기. */
    private void rotation(String prefix){
        Supplier<Boolean> on=()->randomWeapons(prefix);Function<String,String> off=s->randomWeapons(prefix)?null:"랜덤 무기 전용";
        rows.add(new Row(prefix+"rotation","랜덤 무기: 무기 교체 간격 (초)",Kind.SECONDS,Integer.toString(GameOptions.ROTATION_SECONDS),on,off));
        rows.add(new Row(prefix+"choice","랜덤 무기: 교체 전 선택 시간 (초)",Kind.SECONDS,Integer.toString(GameOptions.CHOICE_SECONDS),on,off));
    }
    private void header(String label){rows.add(new Row("",label,Kind.HEADER,""));}
    private void time(String key,Integer fallback){rows.add(new Row(key,"게임 제한 시간 (초)",Kind.SECONDS,text(fallback)));}
    private void prep(String key,Integer fallback){rows.add(new Row(key,"게임 준비 시간 (초)",Kind.SECONDS,text(fallback)));}

    // ---- Notes beside the boxes -----------------------------------------------------------------------------

    /** Seek time the prop hunt plays with: the entered one, else the map's. */
    private int hunt(){
        try{var t=integer("prop.time");if(t!=null)return t;}catch(NumberFormatException ignored){}
        var d=defaults().propHunt().timeSeconds();return d==null?600:d;
    }
    private String changesNote(){
        int hunt=hunt();
        if(hunt<120)return "§c제한 시간 120초 이상 필요";
        Integer changes;try{changes=integer("prop.changes");}catch(NumberFormatException e){return "";}
        int max=(hunt-60)/60;
        if(changes==null)return "기본: 3분마다 · 최대 "+max+"회";
        if(changes==0)return "변신 없음";
        if(!GameOptions.PropHunt.allowed(hunt,changes))return "§c최대 "+max+"회 ("+(60+60*changes)+"초 필요)";
        return "대기 "+duration(GameOptions.PropHunt.interval(hunt,changes))+"마다";
    }
    private String excludedNote(String text){
        int count=0;for(var s:text.split("[,\\s]+"))if(!s.isBlank())count++;
        return count==0?"":count+"개 제외";
    }
    private String multiplierNote(String text){
        Double max;try{max=decimal("escort.max");}catch(NumberFormatException e){return "";}
        if(max==null)max=defaults().escort().maxMultiplier();
        if(max==null)return "";
        double step=(max-1)/3;
        return "2명 x"+number2(1+step)+" · 3명 x"+number2(1+2*step);
    }
    private int rounds(){try{var r=integer("bomb.rounds");if(r!=null)return r;}catch(NumberFormatException ignored){}var d=defaults().bomb().rounds();return d==null?5:d;}
    private String roundsNote(){return rounds()/2+1+"선승"+(rounds()%2==0?" · 동점 무승부":"");}
    private String swapNote(){
        Integer swap;try{swap=integer("bomb.swap");}catch(NumberFormatException e){return "";}
        if(swap==null)swap=defaults().bomb().swapEveryRounds();
        if(swap==null)return "";
        return swap>rounds()?"§c라운드 수 이하":swap+"라운드마다 교대";
    }

    /** Minutes and seconds ("3분 30초", "45초", "1시간 5분"). */
    static String duration(double seconds){
        if(seconds<60)return number(seconds)+"초";
        long whole=(long)Math.floor(seconds);double fraction=seconds-whole;
        long h=whole/3600,m=whole%3600/60;double s=whole%60+fraction;
        var b=new StringBuilder();
        if(h>0)b.append(h).append("시간 ");
        if(m>0)b.append(m).append("분 ");
        if(s>0)b.append(number(s)).append("초");
        return b.toString().strip();
    }
    private static String number(double value){return value==Math.rint(value)?Long.toString((long)value):String.format(Locale.ROOT,"%.2f",value).replaceAll("0+$","").replaceAll("\\.$","");}
    private static String number2(double value){return String.format(Locale.ROOT,"%.2f",value);}
    private String note(Row row){
        String v=value(row.key());
        String extra=row.note()==null?null:row.note().apply(v);
        if(row.kind()==Kind.SECONDS||row.kind()==Kind.DECIMAL_SECONDS){
            String shown=v.isEmpty()||v.equals(".")?row.placeholder():v;
            String time="";
            try{if(!shown.isEmpty())time="= "+duration(Double.parseDouble(shown))+(v.isEmpty()?" (기본)":"");}catch(NumberFormatException ignored){}
            return extra==null||extra.isEmpty()?time:extra;
        }
        return extra==null?"":extra;
    }

    // ---- Layout ----------------------------------------------------------------------------------------------

    private int bottom(){return height-36;}
    private int viewBottom(){return bottom()-16;}
    private int contentHeight(){return rows.size()*ROW;}
    private int maxScroll(){return Math.max(0,contentHeight()-(viewBottom()-TOP));}
    private int x(){return left()+12;}
    private int w(){return body()-24;}
    private int boxX(){return x()+w()-BOX-NOTE-6;}
    @Override protected void init(){
        buildRows();boxes.clear();scroll=Math.max(0,Math.min(scroll,maxScroll()));
        for(int i=0;i<rows.size();i++){
            var row=rows.get(i);int y=TOP+i*ROW-scroll;
            if(row.kind()==Kind.HEADER||y<TOP||y+20>viewBottom())continue;
            boolean enabled=editable()&&row.enabled().get();
            switch(row.kind()){
                case TOGGLE->{
                    String v=value(row.key());boolean on=Boolean.parseBoolean(v.isEmpty()?row.placeholder():v);
                    String label=(on?"켜기":"끄기")+(v.isEmpty()?" (기본)":"");
                    button(label,boxX(),y,BOX,18,enabled,on?ACCENT:MUTED,()->{boolean cur=Boolean.parseBoolean(value(row.key()).isEmpty()?row.placeholder():value(row.key()));values.put(row.key(),Boolean.toString(!cur));rebuildWidgets();});
                }
                case WEAPONS->{
                    String v=value(row.key());var current=GameOptions.WeaponMode.valueOf(v.isEmpty()?row.placeholder():v);
                    String label=switch(current){case CLASSIC->"클래식 (직접 선택)";case RANDOM->"랜덤 무기 (주기마다 교체)";case MELEE->"근접전 (근접 주무기·중복 허용)";}+(v.isEmpty()?" · 기본":"");
                    button(label,boxX(),y,BOX+NOTE+6,18,enabled,ACCENT,()->{var next=GameOptions.WeaponMode.values()[(current.ordinal()+1)%GameOptions.WeaponMode.values().length];values.put(row.key(),next.name());rebuildWidgets();});
                }
                case CHOICE->{
                    String v=value(row.key());var current=GameOptions.Choice.valueOf(v.isEmpty()?row.placeholder():v);
                    String label=switch(current){case OFF->"끄기 (무작위)";case BASIC->"기본 (3개 중 선택)";case FIRST_COME->"선착순";}+(v.isEmpty()?" · 기본":"");
                    button(label,boxX(),y,BOX+NOTE+6,18,enabled,ACCENT,()->{var next=GameOptions.Choice.values()[(current.ordinal()+1)%GameOptions.Choice.values().length];values.put(row.key(),next.name());rebuildWidgets();});
                }
                default->{
                    int boxW=row.kind()==Kind.TEXT?BOX+NOTE-48:BOX;
                    var box=new TacticalEditBox(font,boxX(),y,boxW,18,Component.literal(row.label()));
                    box.setMaxLength(row.kind()==Kind.TEXT?400:8);
                    box.setFilter(switch(row.kind()){
                        case SECONDS,INTEGER->s->s.matches("[0-9]*");
                        case DECIMAL,DECIMAL_SECONDS->s->s.matches("[0-9]*\\.?[0-9]{0,2}");
                        default->s->s.matches("[0-9,\\s]*");
                    });
                    box.setValue(value(row.key()));
                    if(!row.placeholder().isEmpty())box.setHint(Component.literal(row.placeholder()).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
                    box.setEditable(enabled);box.active=enabled;
                    box.setResponder(s->{values.put(row.key(),s);error="";});
                    addRenderableWidget(box);boxes.put(row,box);
                }
            }
        }
        int bottom=bottom(),x=x(),w=w(),third=(w-8)/3;
        button("기본값",x,bottom,third,20,editable(),MUTED,this::reset);
        button("적용",x+third+4,bottom,third,20,editable(),ACCENT,this::save);
        button("돌아가기",x+(third+4)*2,bottom,w-(third+4)*2,20,true,MUTED,this::onClose);
    }
    @Override public boolean mouseScrolled(double mx,double my,double delta){
        int next=Math.max(0,Math.min(maxScroll(),scroll-(int)Math.signum(delta)*ROW));
        if(next!=scroll){scroll=next;rebuildWidgets();}
        return true;
    }
    private void save(){
        if(!editable()){receiveError("관리자만 로비 대기 중 게임 옵션을 변경할 수 있습니다.");return;}
        try{
            var game=collect();
            if(mode().equals("PROP_HUNT")&&game.propHunt().changes()!=null&&!GameOptions.PropHunt.allowed(hunt(),game.propHunt().changes()))
                throw new IllegalArgumentException(hunt()<120?"변신 횟수는 제한 시간 120초 이상일 때만 설정할 수 있습니다.":"변신 "+game.propHunt().changes()+"회에는 제한 시간 "+(60+60*game.propHunt().changes())+"초 이상이 필요합니다.");
            if(mode().equals("BOMB")){
                Integer swap=game.bomb().swapEveryRounds()!=null?game.bomb().swapEveryRounds():defaults().bomb().swapEveryRounds();
                if(swap!=null&&swap>rounds())throw new IllegalArgumentException("공수교대 라운드 수는 라운드 수("+rounds()+") 이하여야 합니다.");
            }
            if(!LobbyClient.connected()){receiveError("서버 연결이 끊겨 게임 옵션을 적용할 수 없습니다.");return;}
            LobbyClient.action("gameOptions",ServerFiles.GSON.toJson(game),"");onClose();
        }catch(NumberFormatException e){receiveError("숫자 형식이 올바르지 않은 칸이 있습니다.");}
        catch(IllegalArgumentException e){receiveError(e.getMessage());}
        catch(RuntimeException failure){
            com.mojang.logging.LogUtils.getLogger().error("Could not submit lobby game options",failure);
            receiveError("게임 옵션을 적용하지 못했습니다. 입력값은 유지됩니다. 로그를 확인해 주세요.");
        }
    }
    @Override public void onClose(){
        if(!LobbyClient.connected()){minecraft.setScreen(null);return;}
        try{LobbyClient.action("lobby","","");minecraft.setScreen(new LobbyScreen(state));}
        catch(RuntimeException failure){
            com.mojang.logging.LogUtils.getLogger().warn("Could not return to lobby",failure);
            receiveError("로비로 돌아가지 못했습니다. 서버 연결을 확인해 주세요.");
        }
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        shell(g,LobbyScreen.modeName(mode())+" · 빈칸은 맵 기본값(회색 글씨) · 변경하면 참가자의 준비가 해제됩니다.");
        int x=x(),w=w();
        for(var e:boxes.entrySet()){boolean on=editable()&&e.getKey().enabled().get();e.getValue().setEditable(on);e.getValue().active=on;}
        g.enableScissor(x-2,TOP-2,x+w+2,viewBottom());
        for(int i=0;i<rows.size();i++){
            var row=rows.get(i);int y=TOP+i*ROW-scroll;
            if(y+ROW<TOP||y>viewBottom())continue;
            if(row.kind()==Kind.HEADER){
                g.fill(x,y+3,x+2,y+15,ACCENT);
                g.drawString(font,row.label(),x+7,y+5,ACCENT,false);
                g.fill(x+12+font.width(row.label()),y+9,x+w,y+10,TacticalStyle.HAIRLINE);
                continue;
            }
            boolean enabled=row.enabled().get();
            g.drawString(font,font.plainSubstrByWidth(row.label(),boxX()-x-12),x+7,y+5,enabled?INK:MUTED,false);
            if(row.kind()!=Kind.CHOICE&&row.kind()!=Kind.WEAPONS){
                String note=note(row);boolean warn=note.startsWith("§c");if(warn)note=note.substring(2);
                int noteX=row.kind()==Kind.TEXT?boxX()+BOX+NOTE-42:boxX()+BOX+6;
                g.drawString(font,font.plainSubstrByWidth(note,x+w-noteX),noteX,y+5,warn?0xFFFF6666:MUTED,false);
            }
        }
        g.disableScissor();
        if(maxScroll()>0){
            int track=viewBottom()-TOP,thumb=Math.max(16,track*track/contentHeight()),pos=TOP+(track-thumb)*scroll/maxScroll();
            g.fill(x+w+3,TOP,x+w+5,viewBottom(),0x30FFFFFF);g.fill(x+w+3,pos,x+w+5,pos+thumb,ACCENT);
        }
        if(!error.isEmpty())g.drawString(font,font.plainSubstrByWidth(error,w),x,viewBottom()+4,0xFFFF6666,false);
        else if(maxScroll()>0)g.drawString(font,"휠로 스크롤",x,viewBottom()+4,DIM,false);
        super.render(g,mx,my,partial);
    }
}
