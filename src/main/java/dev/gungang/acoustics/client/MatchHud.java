package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class MatchHud {
    private static MatchSnapshot state;
    static MatchSnapshot state(){return state;}
    private static int page;
    private static MatchSnapshot pendingResult;
    private static int resultDelay;
    public static boolean active() { return state!=null&&state.active(); }
    public static boolean roundIntermission(){return active()&&state.bomb()!=null&&"ROUND_END".equals(state.bomb().phase());}
    public static void receive(String json) {
        state=ServerFiles.GSON.fromJson(json,MatchSnapshot.class);
        refreshGates();
        EscortHud.receive(state);
        MapEnvironmentClient.receive(state);
        BombWorldHud.receive(state);
        BombSpectator.receive(state);
        if(roundIntermission())OffhandMacro.stopForRoundEnd();
        RespawnPortrait.sync(state.respawns());
        pendingResult=null;resultDelay=0;
        if(state.result()) {
            var mc=Minecraft.getInstance();
            pendingResult=state;resultDelay=state.point()!=null&&"PROP_HUNT".equals(state.point().mode())?1:60;
            mc.setScreen(null);
            mc.gui.setTimes(10,40,10);
            mc.gui.setSubtitle(Component.empty());
            mc.gui.setTitle(resultTitle(state));
        }
    }
    static Component resultTitle(MatchSnapshot result) {
        if(result.winner().equals("DRAW"))return Component.literal("무승부");
        if(result.winner().equals("ABORTED"))return Component.literal("경기 종료");
        if(FreeForAllHud.is(result)){var row=result.players().stream().filter(r->r.id().equals(result.winner())).findFirst().orElse(null);return Component.literal((row==null?"":row.name())+" 승리").withStyle(style->style.withColor(row==null?0xFFFFFF:row.color()));}
        var options=result.options()==null?TeamOptions.defaults():result.options();
        return Component.empty().append(Component.literal("Team "+result.winner()).withStyle(s->s.withColor(options.color(result.winner())&0xFFFFFF)))
                .append(Component.literal(" 승리").withStyle(ChatFormatting.WHITE));
    }
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END||pendingResult==null)return;
        var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null) {pendingResult=null;resultDelay=0;return;}
        if(--resultDelay<=0) {
            var result=pendingResult;pendingResult=null;
            mc.gui.clear();
            mc.setScreen(new Results(result));
        }
    }
    /** Gate rule for SpawnGateBlock: FREE_FOR_ALL, the bomb attacker, or null, from the live match snapshot. */
    static String gateRule(){
        var s=state;if(s==null||!s.active())return null;
        if(s.point()!=null&&"PROP_HUNT".equals(s.point().mode()))return "PROP_HUNT";
        if(FreeForAllHud.is(s))return dev.gungang.acoustics.server.SpawnGateBlock.FREE_FOR_ALL;
        return s.bomb()==null?null:s.bomb().attacker();
    }
    private static String lastGateRule;
    /** Gate tint is baked into chunk meshes, so rebuild them when the bomb sides swap or the mode changes. */
    private static void refreshGates(){
        String rule=gateRule();
        if(java.util.Objects.equals(rule,lastGateRule))return;
        lastGateRule=rule;var mc=Minecraft.getInstance();
        if(mc.level!=null)mc.levelRenderer.allChanged();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { state=null;lastGateRule=null; page=0;ScorePop.reset();pendingResult=null;resultDelay=0;MapEnvironmentClient.receive(null);BombWorldHud.receive(null);BombSpectator.receive(null); }
    @SubscribeEvent public static void hide(RenderGuiOverlayEvent.Pre e) {
        if(HudVisibility.enabled()&&state!=null&&state.active()&&e.getOverlay().id().equals(VanillaGuiOverlay.PLAYER_LIST.id())) e.setCanceled(true);
    }
    @SubscribeEvent public static void scroll(InputEvent.MouseScrollingEvent e) {
        var mc=Minecraft.getInstance();
        if(HudVisibility.enabled()&&state!=null&&state.active()&&mc.screen==null&&mc.options.keyPlayerList.isDown()) { page=Math.max(0,page+(e.getScrollDelta()<0?1:-1)); e.setCanceled(true); }
        else BombSpectator.scroll(e);
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post e) {
        var mc=Minecraft.getInstance(); if(state==null||!state.active()||mc.options.hideGui||!HudVisibility.enabled())return;
        var g=e.getGuiGraphics();
        if(state.point()!=null&&"PROP_HUNT".equals(state.point().mode())){PropHuntClient.draw(g);return;}
        boolean deathmatch=state.point()!=null&&(FreeForAllHud.is(state)||"DEATHMATCH".equals(state.point().mode())||"BOMB".equals(state.point().mode()));
        int y;
        if(state.escort()!=null)y=EscortHud.draw(g,state);
        else if(deathmatch)y=DeathmatchHud.draw(g,state);
        else {y=ControlPointHud.draw(g,state)+4;if(state.point()==null||!"NORMANDY".equals(state.point().mode()))MatchTimerHud.draw(g,state,y);}
        if(mc.screen==null&&mc.options.keyPlayerList.isDown()) {
            String label="경과 시간",time=MatchReport.time(state.elapsedSeconds());
            int w=mc.font.width(label)+mc.font.width(time)+20,cx=g.guiWidth()/2-w/2;
            g.fill(cx,y+18,cx+w,y+31,0x900B121C);g.fill(cx,y+30,cx+w,y+31,0xFF70CDBF);
            g.drawString(mc.font,label,cx+7,y+21,0xFF9AAABD,false);
            g.drawString(mc.font,time,cx+w-7-mc.font.width(time),y+21,0xFFFFFFFF,false);
            int height=g.guiHeight()-y-48;
            if(state.bomb()!=null){
                // Bomb mode: the scoreboard shares the width with a square minimap on its right.
                int map=Math.max(0,Math.min(Math.min(180,height-14),(g.guiWidth()-16)/3)),width=Math.min(480,g.guiWidth()-16-map),left=Math.max(4,(g.guiWidth()-width-8-map)/2);
                table(g,state,left,y+35,width,height,page);
                BombMinimap.draw(g,state,left+width+8,y+35+13,map);
            }
            else table(g,state,Math.max(4,(g.guiWidth()-480)/2),y+35,Math.min(480,g.guiWidth()-8),height,page);
        }
    }
    private static final int HEAD=28,COLS=13,ROW=18,FOOT=13,GAP=8;
    private static final Comparator<MatchSnapshot.Row> RANK=Comparator.comparingInt(MatchSnapshot.Row::kills).reversed()
            .thenComparing(Comparator.comparingDouble(MatchSnapshot.Row::damage).reversed())
            .thenComparingInt(MatchSnapshot.Row::deaths).thenComparing(MatchSnapshot.Row::name);
    static int table(GuiGraphics g,MatchSnapshot value,int x,int y,int width,int height,int requestedPage) {
        if(value.point()!=null&&"PROP_HUNT".equals(value.point().mode()))return PropHuntClient.table(g,value,x,y,width,height,requestedPage);
        if(FreeForAllHud.is(value))return FreeForAllHud.table(g,value,x,y,width,height,requestedPage);
        int capacity=capacity(height),pages=tablePages(value,height),p=Math.min(requestedPage,pages-1);
        int half=(width-GAP)/2,shown=1;
        var options=value.options()==null?TeamOptions.defaults():value.options();
        List<List<MatchSnapshot.Row>> teams=new ArrayList<>();
        for(String name:new String[]{"ALPHA","BRAVO"}) {
            var rows=value.players().stream().filter(r->r.team().equals(name)).sorted(RANK).toList();
            teams.add(rows);shown=Math.max(shown,Math.min(capacity,rows.size()-p*capacity));
        }
        int bottom=y+HEAD+COLS+shown*ROW+(pages>1?FOOT:4);
        for(int team=0;team<2;team++) {
            String name=team==0?"ALPHA":"BRAVO";
            int score=team==0?value.alphaScore():value.bravoScore(),other=team==0?value.bravoScore():value.alphaScore();
            teamCard(g,name,options.color(name),score,score>other,teams.get(team),x+team*(half+GAP),y,half,bottom,p,capacity,pages);
        }
        return pages;
    }
    private static void teamCard(GuiGraphics g,String name,int color,int score,boolean leading,List<MatchSnapshot.Row> rows,
                                 int left,int top,int width,int bottom,int page,int capacity,int pages) {
        var mc=Minecraft.getInstance();var font=mc.font;int right=left+width,rgb=color&0xFFFFFF;
        // Translucent card so the match stays faintly visible; shadow only on the exposed edges.
        g.fill(right,top+2,right+2,bottom+2,0x40000000);g.fill(left+2,bottom,right,bottom+2,0x40000000);
        g.fillGradient(left,top,right,bottom,0xA6131D2B,0xA60B121C);
        g.fillGradient(left,top,right,top+HEAD,rgb|0x50000000,rgb|0x08000000);
        g.fill(left,top,right,top+2,color|0xFF000000);
        g.fill(left+8,top+9,left+10,top+19,color|0xFF000000);
        g.drawString(font,name,left+14,top+7,0xFFFFFFFF,false);
        long kills=rows.stream().mapToLong(MatchSnapshot.Row::kills).sum();
        g.drawString(font,rows.size()+"명 · "+kills+" 킬",left+14,top+17,0xFF9AAABD,false);
        String total=String.valueOf(score);
        g.pose().pushPose();g.pose().translate(right-8-font.width(total)*2,top+6,0);g.pose().scale(2,2,1);
        g.drawString(font,total,0,0,leading?0xFFFFFFFF:0xFFB4C0CE,false);g.pose().popPose();
        // Columns are right-anchored so the name gets every remaining pixel.
        int dmg=right-8,assist=dmg-46,death=assist-22,kill=death-22,face=left+26,nameX=face+16,cy=top+HEAD;
        g.fill(left,cy,right,cy+COLS,0x30000000);g.fill(left,cy+COLS-1,right,cy+COLS,0xFF22324A);
        g.drawString(font,"#",left+8,cy+3,0xFF5F6E80,false);
        g.drawString(font,"플레이어",face,cy+3,0xFF5F6E80,false);
        column(g,"K",kill,cy+3,0xFF5F6E80);column(g,"D",death,cy+3,0xFF5F6E80);
        column(g,"A",assist,cy+3,0xFF5F6E80);column(g,"DMG",dmg,cy+3,0xFF5F6E80);
        String self=mc.player==null?"":mc.player.getUUID().toString();
        int start=page*capacity,count=Math.max(0,Math.min(capacity,rows.size()-start));
        if(count==0)g.drawString(font,"플레이어 없음",left+(width-font.width("플레이어 없음"))/2,cy+COLS+5,0xFF5F6E80,false);
        for(int i=0;i<count;i++) {
            var row=rows.get(start+i);int ry=cy+COLS+i*ROW,rank=start+i+1;boolean mine=row.id().equals(self);
            if(mine){g.fill(left,ry,right,ry+ROW,rgb|0x38000000);g.fill(left,ry,left+2,ry+ROW,color|0xFF000000);}
            else if(i%2==1)g.fill(left,ry,right,ry+ROW,0x10FFFFFF);
            String number=String.valueOf(rank);
            g.drawString(font,number,left+8+(10-font.width(number))/2,ry+5,rank==1&&row.kills()>0?0xFFFFD166:0xFF5F6E80,false);
            g.fill(face-1,ry+2,face+13,ry+16,mine?color|0xFF000000:0xFF2A3A50);
            PlayerFaceRenderer.draw(g,skin(row.id()),face,ry+3,12);
            g.drawString(font,font.plainSubstrByWidth(row.name(),Math.max(1,kill-22-nameX)),nameX,ry+5,mine?0xFFFFFFFF:0xFFE2E8F0,false);
            column(g,String.valueOf(row.kills()),kill,ry+5,0xFFFFFFFF);
            column(g,String.valueOf(row.deaths()),death,ry+5,0xFFE08A8A);
            column(g,String.valueOf(row.assists()),assist,ry+5,0xFF9AAABD);
            column(g,String.format(Locale.ROOT,"%.1f",row.damage()),dmg,ry+5,0xFFFFC870);
        }
        if(pages>1) {
            String label=(page+1)+" / "+pages;
            g.fill(left+8,bottom-FOOT,right-8,bottom-FOOT+1,0xFF22324A);
            g.drawString(font,label,right-8-font.width(label),bottom-FOOT+3,0xFF5F6E80,false);
        }
    }
    static void column(GuiGraphics g,String text,int right,int y,int color) {
        var font=Minecraft.getInstance().font;g.drawString(font,text,right-font.width(text),y,color,false);
    }
    static ResourceLocation skin(String id) {
        var connection=Minecraft.getInstance().getConnection();
        try {
            var info=connection==null?null:connection.getPlayerInfo(UUID.fromString(id));
            if(info!=null)return info.getSkinLocation();
            return DefaultPlayerSkin.getDefaultSkin(UUID.fromString(id));
        } catch(IllegalArgumentException ignored) { return DefaultPlayerSkin.getDefaultSkin(); }
    }
    private static int capacity(int height) { return Math.max(1,(height-HEAD-COLS-FOOT)/ROW); }
    private static int tablePages(MatchSnapshot value,int height) {
        if(value.point()!=null&&"PROP_HUNT".equals(value.point().mode()))return PropHuntClient.pages(value,height);
        if(FreeForAllHud.is(value))return FreeForAllHud.pages(value,height);
        long count=Math.max(value.players().stream().filter(r->r.team().equals("ALPHA")).count(),value.players().stream().filter(r->r.team().equals("BRAVO")).count());
        int capacity=capacity(height);return Math.max(1,(int)((count+capacity-1)/capacity));
    }
    private static final class Results extends ArenaScreen {
        final MatchSnapshot result; int current;
        Results(MatchSnapshot result) { super(resultTitle(result)); this.result=result; }
        @Override boolean seeThrough() { return true; }
        @Override protected void init() {
            button("이전",left()+10,height-40,60,20,true,ACCENT,()->current=Math.max(0,current-1));
            button("다음",left()+76,height-40,60,20,true,ACCENT,()->current=Math.min(tablePages(result,height-112)-1,current+1));
            closeButton(left()+body()-80,height-40,70,20);
        }
        @Override public void render(GuiGraphics g,int mx,int my,float partial) {
            if(FreeForAllHud.is(result)){
                var winner=result.players().stream().filter(r->r.id().equals(result.winner())).findFirst().orElse(null);
                var subtitle=Component.literal(result.map()+" · 플레이 시간 "+MatchReport.time(result.seconds()));
                if(winner!=null)subtitle.append(Component.literal(" · 1위 ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(winner.name()+" "+winner.score()+"점").withStyle(s->s.withColor(winner.color()&0xFFFFFF)));
                shell(g,subtitle);
            }
            else shell(g,Component.literal(result.map()+" · 플레이 시간 "+MatchReport.time(result.seconds())+" · ")
                    .append(Component.literal("Team ALPHA "+result.alphaScore()).withStyle(s->s.withColor((result.options()==null?TeamOptions.defaults():result.options()).color("ALPHA")&0xFFFFFF)))
                    .append(Component.literal(" : ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("Team Bravo "+result.bravoScore()).withStyle(s->s.withColor((result.options()==null?TeamOptions.defaults():result.options()).color("BRAVO")&0xFFFFFF))));
            table(g,result,left()+8,62,body()-16,height-112,current); super.render(g,mx,my,partial);
        }
    }
    private MatchHud() {}
}
