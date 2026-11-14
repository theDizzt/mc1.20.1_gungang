package dev.gungang.acoustics.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import dev.gungang.acoustics.server.GungangNetwork;
import dev.gungang.acoustics.server.MatchHistory;

/**
 * Every saved match result, numbered from #1 in start-time order (server: {@link MatchHistory}). The list is on the
 * left, newest first; the selected match's scoreboard is on the right and is fetched when first selected.
 */
public final class MatchHistoryScreen extends ArenaScreen {
    @Override Component screenText(Component text){return Component.literal(text.getString()).withStyle(s->s.withFont(PlayerStatsScreen.STATS_FONT));}
    private Component text(String value){return screenText(Component.literal(value));}
    private static final int ROW=26,WIN=0xFF70B58E,LOSS=0xFFCC5544;
    private final Screen parent;
    private final String target,targetName;
    private List<MatchHistory.Summary> matches;
    private final Map<Integer,MatchHistory.Match> details=new HashMap<>();
    private boolean mineOnly;
    private int selected,listScroll,detailScroll,detailContent,waited;
    private int left,top,panelWidth,bottom,listWidth;

    private MatchHistoryScreen(Screen parent,String target,String targetName){
        super("GUNGANG / MATCHES");this.parent=parent;this.target=target;this.targetName=targetName;
    }
    static void open(Screen parent,String target,String targetName){
        var screen=new MatchHistoryScreen(parent,target,targetName);
        Minecraft.getInstance().setScreen(screen);
        screen.request("");
    }
    /** A list or detail packet; ignored unless this screen is still open. */
    public static void receive(String json){
        if(!(Minecraft.getInstance().screen instanceof MatchHistoryScreen screen))return;
        var view=new com.google.gson.Gson().fromJson(json,MatchHistory.View.class);
        if(view==null||!Objects.equals(view.target(),screen.target))return;
        if(view.match()!=null){screen.details.put(view.match().number(),view.match());return;}
        if(view.matches()==null)return;
        screen.matches=new ArrayList<>(view.matches());
        screen.matches.sort(Comparator.comparingInt(MatchHistory.Summary::number).reversed());
        var shown=screen.shown();
        if(screen.selected==0&&!shown.isEmpty())screen.select(shown.get(0).number());
    }
    private void request(String value){
        waited=0;GungangNetwork.CHANNEL.sendToServer(new GungangNetwork.Action("matchHistory",value,target));
    }
    private void select(int number){
        selected=number;detailScroll=0;
        if(!details.containsKey(number))request(Integer.toString(number));
    }
    private List<MatchHistory.Summary> shown(){
        if(matches==null)return List.of();
        return mineOnly?matches.stream().filter(m->!m.team().isEmpty()).toList():matches;
    }
    @Override protected void init(){
        left=left()+12;top=78;panelWidth=body()-24;bottom=height-56;
        listWidth=Math.max(170,Math.min(250,panelWidth*2/5));
        tabButton("전체 경기",left,58,72,16,!mineOnly,()->{mineOnly=false;listScroll=0;rebuildWidgets();});
        tabButton(targetName+" 참여",left+76,58,Math.max(72,font.width(text(targetName+" 참여"))+18),16,mineOnly,()->{mineOnly=true;listScroll=0;rebuildWidgets();});
        closeButton(left+panelWidth-80,height-48,80,22);
        button("← 전적",left+panelWidth-80-6-80,height-48,80,22,true,MUTED,()->minecraft.setScreen(parent));
    }
    @Override public void tick(){
        // The server drops actions sent too close together; ask again if an answer never came.
        if(++waited<12)return;
        if(matches==null)request("");
        else if(selected>0&&!details.containsKey(selected))request(Integer.toString(selected));
        else waited=0;
    }
    @Override public void onClose(){minecraft.setScreen(parent);}

    private int listTop(){return top+18;}
    private int listRows(){return Math.max(1,(bottom-6-listTop())/ROW);}
    private int maxListScroll(){return Math.max(0,shown().size()-listRows());}
    private int detailLeft(){return left+listWidth+8;}
    private int detailBodyTop(){return top+64;}
    private int maxDetailScroll(){return Math.max(0,detailContent-(bottom-6-detailBodyTop()));}
    @Override public boolean mouseScrolled(double x,double y,double delta){
        if(x<detailLeft())listScroll=Math.max(0,Math.min(maxListScroll(),listScroll-(int)Math.signum(delta)*3));
        else detailScroll=Math.max(0,Math.min(maxDetailScroll(),detailScroll-(int)(delta*14)));
        return true;
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(super.mouseClicked(x,y,button))return true;
        if(button!=0||x<left+4||x>=left+listWidth-6||y<listTop())return false;
        int index=(int)(y-listTop())/ROW;
        if(index>=listRows())return false;
        var rows=shown();index+=listScroll;
        if(index<rows.size()){select(rows.get(index).number());return true;}
        return false;
    }

    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick){
        int total=matches==null?0:matches.size(),mine=matches==null?0:(int)matches.stream().filter(m->!m.team().isEmpty()).count();
        shell(g,matches==null?"전체 경기 기록 · 불러오는 중...":"전체 경기 기록 · 총 "+total+"경기 · "+targetName+" 참여 "+mine+"경기");
        list(g,mouseX,mouseY);
        detail(g);
        super.render(g,mouseX,mouseY,partialTick);
    }

    private void list(GuiGraphics g,int mouseX,int mouseY){
        int x1=left,x2=left+listWidth;
        card(g,x1,top,x2,bottom);
        cardHeader(g,x1,top,x2,"경기 목록",ACCENT,matches==null?"":shown().size()+"경기");
        var rows=shown();
        if(matches==null||rows.isEmpty()){
            g.drawString(font,text(matches==null?"불러오는 중...":"표시할 경기가 없습니다."),x1+8,listTop()+8,MUTED,false);return;
        }
        listScroll=Math.min(listScroll,maxListScroll());
        for(int i=0;i<listRows()&&i+listScroll<rows.size();i++){
            var m=rows.get(i+listScroll);
            int y=listTop()+i*ROW,rx1=x1+4,rx2=x2-7;
            boolean on=m.number()==selected,hover=mouseX>=rx1&&mouseX<rx2&&mouseY>=y&&mouseY<y+ROW-2;
            g.fill(rx1,y,rx2,y+ROW-2,on?blend(TacticalStyle.TRACK,ACCENT,.16f):hover?blend(TacticalStyle.TRACK,INK,.05f):TacticalStyle.TRACK);
            if(on)TacticalStyle.frame(g,rx1,y,rx2-rx1,ROW-2,blend(PANEL,ACCENT,.7f));
            if(!m.team().isEmpty())g.fill(rx1,y,rx1+2,y+ROW-2,teamColor(m.team()));
            var number=text("#"+m.number());
            g.drawString(font,number,rx1+6,y+4,on?ACCENT:INK,false);
            var date=text(m.started());
            g.drawString(font,date,rx2-5-font.width(date),y+4,TacticalStyle.DIM,false);
            var score=text(m.mode().equals("FREE_FOR_ALL")?"":m.alpha()+" : "+m.bravo());
            int scoreX=rx2-5-font.width(score);
            String result=result(m.team(),m.winner());
            int chip=0;
            if(!result.isEmpty()){var r=text(result);chip=font.width(r)+4;g.drawString(font,r,scoreX-chip,y+14,resultColor(result),false);}
            g.drawString(font,score,scoreX,y+14,MUTED,false);
            Marquee.draw(g,font,text(m.map()+" · "+mode(m.mode())),rx1+6,y+14,Math.max(1,scoreX-chip-rx1-12),on?INK:MUTED,false);
        }
        if(maxListScroll()>0){
            int view=listRows()*ROW,thumb=Math.max(10,view*listRows()/rows.size());
            int y=listTop()+(view-thumb)*listScroll/maxListScroll();
            g.fill(x2-5,y,x2-3,y+thumb,ACCENT);
        }
    }

    private void detail(GuiGraphics g){
        int x1=detailLeft(),x2=left+panelWidth;
        card(g,x1,top,x2,bottom);
        TacticalStyle.band(g,x1,top,x2,3,ACCENT,PANEL);
        var m=details.get(selected);
        if(m==null){
            g.drawString(font,text(selected==0?"경기를 선택하세요.":"불러오는 중..."),x1+10,top+12,MUTED,false);return;
        }
        // Title: number, map, then date · mode · play time.
        var number=text("#"+m.number());
        g.pose().pushPose();g.pose().translate(x1+10,top+9,0);g.pose().scale(1.4f,1.4f,1);g.drawString(font,number,0,0,ACCENT,false);g.pose().popPose();
        int titleX=x1+10+Math.round(font.width(number)*1.4f)+8;
        Marquee.draw(g,font,text(m.map()),titleX,top+12,Math.max(1,x2-10-titleX),INK,false);
        Marquee.draw(g,font,text(m.started()+"  ·  "+mode(m.mode())+"  ·  "+dev.gungang.acoustics.server.MatchReport.time(m.seconds())+"  ·  "+m.players().size()+"명"),x1+10,top+25,x2-x1-20,MUTED,false);
        scoreboard(g,m,x1+8,top+38,x2-8,20);
        // Player tables, scrolled inside the card.
        int bodyTop=detailBodyTop(),bodyBottom=bottom-6;
        detailScroll=Math.min(detailScroll,maxDetailScroll());
        g.enableScissor(x1+4,bodyTop,x2-4,bodyBottom);
        var teams=new LinkedHashMap<String,List<MatchHistory.Player>>();
        for(var p:m.players())teams.computeIfAbsent(p.team(),k->new ArrayList<>()).add(p);
        boolean ffa=m.mode().equals("FREE_FOR_ALL");
        int width=x2-x1-16,y=bodyTop-detailScroll;
        if(!ffa&&teams.keySet().stream().allMatch(t->t.equals("ALPHA")||t.equals("BRAVO"))&&width>=300){
            int half=(width-6)/2;
            int a=table(g,"ALPHA",teams.getOrDefault("ALPHA",List.of()),x1+8,y,half,m.winner(),false);
            int b=table(g,"BRAVO",teams.getOrDefault("BRAVO",List.of()),x1+8+half+6,y,width-half-6,m.winner(),false);
            detailContent=Math.max(a,b);
        }else if(ffa){
            var all=new ArrayList<>(m.players());
            all.sort(Comparator.comparingInt(MatchHistory.Player::score).reversed());
            detailContent=table(g,"개인전 순위",all,x1+8,y,width,m.winner(),true);
        }else{
            int used=0;
            for(var e:teams.entrySet())used+=table(g,e.getKey(),e.getValue(),x1+8,y+used,width,m.winner(),false)+6;
            detailContent=used;
        }
        g.disableScissor();
        if(maxDetailScroll()>0){
            int view=bodyBottom-bodyTop,thumb=Math.max(10,view*view/Math.max(1,detailContent));
            int ty=bodyTop+(view-thumb)*detailScroll/maxDetailScroll();
            g.fill(x2-5,ty,x2-3,ty+thumb,ACCENT);
        }
    }
    /** ALPHA score : score BRAVO, the winning side tinted. */
    private void scoreboard(GuiGraphics g,MatchHistory.Match m,int x1,int y,int x2,int h){
        int mid=(x1+x2)/2;
        boolean alphaWon=m.winner().equals("ALPHA"),bravoWon=m.winner().equals("BRAVO");
        g.fill(x1,y,mid,y+h,alphaWon?blend(TacticalStyle.TRACK,ControlPointHud.ALPHA_TEXT,.16f):TacticalStyle.TRACK);
        g.fill(mid,y,x2,y+h,bravoWon?blend(TacticalStyle.TRACK,ControlPointHud.BRAVO_TEXT,.16f):TacticalStyle.TRACK);
        g.fill(x1,y,x1+2,y+h,ControlPointHud.ALPHA_TEXT);g.fill(x2-2,y,x2,y+h,ControlPointHud.BRAVO_TEXT);
        if(m.winner().equals("ABORTED")){
            var w=text("중단된 경기");
            g.drawString(font,w,mid-font.width(w)/2,y+(h-8)/2,MUTED,false);return;
        }
        if(m.mode().equals("FREE_FOR_ALL")){
            String name=m.players().stream().filter(p->p.id().equals(m.winner())).map(MatchHistory.Player::name).findFirst().orElse(m.winner());
            var w=text("우승  "+name);
            g.drawString(font,w,mid-font.width(w)/2,y+(h-8)/2,ACCENT,false);return;
        }
        var alpha=text("ALPHA"+(alphaWon?"  승리":""));var bravo=text((bravoWon?"승리  ":"")+"BRAVO");
        g.drawString(font,alpha,x1+8,y+(h-8)/2,ControlPointHud.ALPHA_TEXT,false);
        g.drawString(font,bravo,x2-8-font.width(bravo),y+(h-8)/2,ControlPointHud.BRAVO_TEXT,false);
        var score=text(m.alpha()+" : "+m.bravo());
        float s=1.5f;
        g.pose().pushPose();g.pose().translate(mid-font.width(score)*s/2,y+(h-8*s)/2+1,0);g.pose().scale(s,s,1);
        g.drawString(font,score,0,0,INK,false);g.pose().popPose();
    }
    /** One team's rows; returns the height used. */
    private int table(GuiGraphics g,String team,List<MatchHistory.Player> players,int x,int y,int w,String winner,boolean ranked){
        int color=ranked?ACCENT:teamColor(team);
        g.fill(x,y,x+w,y+1,color);
        g.fillGradient(x,y+1,x+w,y+13,blend(PANEL,color,.18f),PANEL);
        g.drawString(font,text(team+(team.equals(winner)?"  · 승리":"")),x+4,y+3,color,false);
        int damageX=x+w-4,kdaX=damageX-Math.max(46,w/5);
        var dh=text("피해량");var kh=text("K / D / A");
        g.drawString(font,dh,damageX-font.width(dh),y+16,TacticalStyle.DIM,false);
        g.drawString(font,kh,kdaX-font.width(kh),y+16,TacticalStyle.DIM,false);
        int row=y+27,rank=0;
        for(var p:players){
            rank++;
            boolean me=p.id().equalsIgnoreCase(target);
            if(me)g.fill(x,row-1,x+w,row+11,blend(PANEL,ACCENT,.2f));
            else if(rank%2==1)g.fill(x,row-1,x+w,row+11,TacticalStyle.TRACK);
            var damage=text(String.format(Locale.ROOT,"%,.1f",p.damage()));
            var kda=text(p.kills()+" / "+p.deaths()+" / "+p.assists());
            g.drawString(font,damage,damageX-font.width(damage),row+1,MUTED,false);
            g.drawString(font,kda,kdaX-font.width(kda),row+1,INK,false);
            var name=text((ranked?rank+"위  ":"")+p.name()).copy().append(dev.gungang.acoustics.server.PlayerTag.component(p.tag()));
            if(ranked&&p.score()>0)name.append(text("  ("+p.score()+")"));
            Marquee.draw(g,font,name,x+4,row+1,Math.max(1,kdaX-font.width(kh)-8-x-4),me?ACCENT:INK,false);
            row+=12;
        }
        return row-y+2;
    }

    private static int teamColor(String team){
        return team.equals("ALPHA")?ControlPointHud.ALPHA_TEXT:team.equals("BRAVO")?ControlPointHud.BRAVO_TEXT:MUTED;
    }
    /** The viewed player's result; free-for-all rows carry the player UUID as both team and winner. */
    private static String result(String team,String winner){
        if(team.isEmpty())return "";
        if(winner.equals("ABORTED"))return "중단";
        if(winner.isEmpty())return "무";
        return team.equals(winner)?"승":"패";
    }
    private static int resultColor(String result){return result.equals("승")?WIN:result.equals("패")?LOSS:MUTED;}
    static String mode(String mode){
        return switch(mode){
            case "PROP_HUNT"->"숨바꼭질";case "FREE_FOR_ALL"->"개인전";case "ESCORT"->"호위";case "BOMB"->"폭탄전";
            case "DEATHMATCH"->"팀 데스매치";case "NORMANDY"->"이동점령";case "CUSTOM"->"기타";default->"쟁탈전";
        };
    }
    @Override public boolean isPauseScreen(){return false;}
}
