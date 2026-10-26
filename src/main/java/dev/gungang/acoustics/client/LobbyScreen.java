package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.LobbySnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

/** Teams stay visible while maps are browsed in the right panel. */
final class LobbyScreen extends ArenaScreen {
    private static final int ROW=18, WAITING=0xFFE0B860;
    private static final String RANDOM=dev.gungang.acoustics.server.LobbyRules.RANDOM_MAP;
    private LobbySnapshot state;
    private boolean maps,draggingMember,draggingPreview;
    private int page,mapScroll;
    private String target,previewMap,viewedMap,dragId;
    private double dragX,dragY,zoom=1,panX,panY;
    private int viewX,viewY,viewW,viewH,baseW,baseH;
    private record Box(int x,int y,int w,int h) {
        int right(){return x+w;} int bottom(){return y+h;}
        boolean contains(double px,double py){return px>=x&&px<right()&&py>=y&&py<bottom();}
    }
    private record Row(LobbySnapshot.Member member,int x,int y,int w) { }
    /** Row layout: selection bar, portrait, level emblem, then the name; the status chip sits at the right edge. */
    private static final int FACE=6,EMBLEM=22,NAME=39;
    // Share coordinates between rendering, widgets and hit testing.
    private int leftWidth(){return (width-22)*62/100;}
    private int rightX(){return 14+leftWidth();}
    private int rightWidth(){return width-8-rightX();}
    private int lowerTop(){return height-8-Math.max(120,(height-16)*29/100);}
    private Box alpha(){return new Box(8,48,(leftWidth()-6)/2,lowerTop()-54);}
    private Box bravo(){var a=alpha();return new Box(a.right()+6,a.y,leftWidth()-a.w-6,a.h);}
    private Box spectators(){return new Box(8,lowerTop(),leftWidth(),height-8-lowerTop());}
    private Box preview(){return new Box(rightX(),8,rightWidth(),lowerTop()-14);}
    private Box controls(){return new Box(rightX(),lowerTop(),rightWidth(),height-8-lowerTop());}
    private int capacity(){return Math.max(1,(alpha().h-24)/ROW);}
    private int neutralRows(){return Math.max(1,(spectators().h-24)/18);}
    private int neutralCapacity(){return neutralRows()*2;}
    private List<LobbySnapshot.Member> team(String name){return state.members().stream().filter(m->m.team().equals(name)).toList();}
    private List<LobbySnapshot.Member> neutral(){return state.members().stream().filter(m->!m.team().equals("ALPHA")&&!m.team().equals("BRAVO")).toList();}
    private LobbySnapshot.Member member(String id){return state.members().stream().filter(m->m.id().equals(id)).findFirst().orElse(null);}
    private LobbySnapshot.MapView map(String id){return state.maps().stream().filter(m->m.id().equals(id)).findFirst().orElse(null);}
    private boolean admin(){return state.administrator()&&"LOBBY".equals(state.phase());}
    private int pages(){return Math.max(1,Math.max((Math.max(team("ALPHA").size(),team("BRAVO").size())+capacity()-1)/capacity(),(neutral().size()+neutralCapacity()-1)/neutralCapacity()));}
    private List<Row> rows(){
        var result=new ArrayList<Row>();
        for(String name:List.of("ALPHA","BRAVO")){
            var b=name.equals("ALPHA")?alpha():bravo();var list=team(name);int start=page*capacity();
            for(int i=start;i<Math.min(list.size(),start+capacity());i++)result.add(new Row(list.get(i),b.x+4,b.y+22+(i-start)*ROW,b.w-8));
        }
        var b=spectators();var list=neutral();int start=page*neutralCapacity(),column=(b.w-12)/2;
        for(int i=start;i<Math.min(list.size(),start+neutralCapacity());i++){
            int slot=i-start;result.add(new Row(list.get(i),b.x+4+(slot/neutralRows())*(column+4),b.y+22+(slot%neutralRows())*18,column));
        }
        return result;
    }
    LobbyScreen(LobbySnapshot state){super("GUNGANG / MATCH LOBBY");this.state=state;target=state.you();previewMap=state.selectedMap();PreviewCache.clear();}
    void update(LobbySnapshot next){
        boolean changed=!next.selectedMap().equals(state.selectedMap());state=next;
        if(changed||map(previewMap)==null&&!RANDOM.equals(previewMap))previewMap=next.selectedMap();
        if(member(target)==null)target=state.you();
        if(member(dragId)==null){dragId=null;draggingMember=false;}
        rebuildWidgets();
    }
    @Override public void onClose(){super.onClose();minecraft.gui.setOverlayMessage(Component.literal(LobbyClient.LOBBY.getTranslatedKeyMessage().getString()+" 키 또는 /gungang lobby로 대기실 다시 열기"),false);}
    @Override protected void init(){
        page=Math.min(page,pages()-1);baseW=0;
        int pageX=8+leftWidth()-46;
        button("‹",pageX,17,20,18,page>0,MUTED,()->{page--;rebuildWidgets();});
        button("›",pageX+24,17,20,18,page+1<pages(),MUTED,()->{page++;rebuildWidgets();});
        for(var row:rows()){
            var m=row.member();int color=m.team().equals("ALPHA")?LobbyClient.options.color("ALPHA"):m.team().equals("BRAVO")?LobbyClient.options.color("BRAVO"):MUTED;
            var label=Component.literal(m.name());
            if(m.id().equals(state.you()))label.append(Component.literal(" 나").withStyle(s->s.withColor(ACCENT&0xFFFFFF)));
            listButton(label,row.x,row.y,row.w,17,admin()||m.id().equals(state.you()),m.id().equals(target)?color:MUTED,NAME,font.width(status(m))+14,()->{target=m.id();rebuildWidgets();});
        }
        if(maps)mapButtons();
        var b=controls();int x=b.x+5,w=b.w-10,h=18,y=b.y+6,third=(w-8)/3,half=(w-4)/2;
        var picked=member(target);boolean editable=picked!=null&&(admin()||picked.id().equals(state.you()));
        colorButton("알파",x,y,third,h,editable,LobbyClient.options.color("ALPHA"),()->LobbyClient.action("team","ALPHA",target));
        button("관전",x+third+4,y,third,h,editable&&!picked.bot(),MUTED,()->LobbyClient.action("team","SPECTATOR",target));
        colorButton("브라보",x+(third+4)*2,y,w-(third+4)*2,h,editable,LobbyClient.options.color("BRAVO"),()->LobbyClient.action("team","BRAVO",target));
        y+=22;
        button(maps?"미리보기로":"맵 선택",x,y,half,h,true,ACCENT,()->{maps=!maps;draggingPreview=false;rebuildWidgets();});
        button("랜덤 팀 배치",x+half+4,y,w-half-4,h,admin(),MUTED,()->LobbyClient.action("random","",""));
        y+=22;modeButtons(x,y,w,h);y+=22;
        boolean bots=admin()&&state.botEnabled()&&"DEATHMATCH".equals(state.selectedMode());
        button("봇 + "+state.botCount()+"/"+state.botMax(),x,y,half,h,bots&&state.botCount()<state.botMax(),MUTED,()->LobbyClient.action("botAdd","",""));
        button("선택한 봇 제거",x+half+4,y,w-half-4,h,bots&&picked!=null&&picked.bot(),MUTED,()->LobbyClient.action("botRemove","",picked.id()));
        y+=22;
        var self=member(state.you());boolean ready=self!=null&&self.ready();String selection=state.selectedMap()+":"+state.selectedMode();
        boolean allowed=self!=null&&(self.team().equals("ALPHA")||self.team().equals("BRAVO"))&&!state.selectedMap().isEmpty();
        colorButton(ready?"준비 취소":"준비 완료",x,y,Math.max(1,w-42),h,allowed,ready?WAITING:ACCENT,()->LobbyClient.action("ready",Boolean.toString(!ready),selection));
        closeButton(x+w-38,y,38,h);
    }
    private static String status(LobbySnapshot.Member m){return m.ready()?"준비":m.team().equals("UNASSIGNED")?"미배정":m.team().equals("SPECTATOR")?"관전":"대기";}
    private static int statusColor(LobbySnapshot.Member m){return m.ready()?READY:m.team().equals("ALPHA")||m.team().equals("BRAVO")?WAITING:MUTED;}
    private static String modeName(String mode){return switch(mode){case "PROP_HUNT"->"숨바꼭질";case "FREE_FOR_ALL"->"개인전";case "ESCORT"->"호위";case "BOMB"->"폭탄전";case "DEATHMATCH"->"팀 데스매치";case "NORMANDY"->"노르망디";default->"쟁탈전";};}
    private void modeButtons(int x,int y,int w,int h){
        var selected=map(state.selectedMap());if(selected==null){button("선택된 맵 없음",x,y,w,h,false,MUTED,()->{});return;}
        String objective=switch(selected.gameMode()){case "숨바꼭질"->"PROP_HUNT";case "개인전"->"FREE_FOR_ALL";case "호위"->"ESCORT";case "노르망디"->"NORMANDY";default->selected.bomb()?"BOMB":"CONTROL";};
        var modes=new ArrayList<String>();if(!selected.deathmatchOnly())modes.add(objective);if(selected.deathmatch())modes.add("DEATHMATCH");if(!modes.contains("PROP_HUNT"))modes.add("PROP_HUNT");
        int cell=(w-(modes.size()-1)*4)/modes.size();
        for(int i=0;i<modes.size();i++){String mode=modes.get(i);button(modeName(mode),x+i*(cell+4),y,i==modes.size()-1?w-i*(cell+4):cell,h,admin(),mode.equals(state.selectedMode())?ACCENT:MUTED,()->LobbyClient.action("mode",mode,selected.id()));}
    }
    private boolean randomRow(){return !state.maps().isEmpty()&&map(RANDOM)==null;}
    private int mapCount(){return state.maps().size()+(randomRow()?1:0);}
    private int mapCapacity(){return Math.max(1,(preview().h-56)/21);}
    private int maxMapScroll(){return Math.max(0,mapCount()-mapCapacity());}
    private void mapButtons(){
        var b=preview();mapScroll=Math.min(mapScroll,maxMapScroll());
        button("‹",b.right()-46,b.y+4,18,18,mapScroll>0,MUTED,()->scrollMaps(mapScroll-mapCapacity()));
        button("›",b.right()-24,b.y+4,18,18,mapScroll<maxMapScroll(),MUTED,()->scrollMaps(mapScroll+mapCapacity()));
        int offset=randomRow()?1:0;
        for(int i=mapScroll;i<Math.min(mapCount(),mapScroll+mapCapacity());i++){
            var m=i<offset?null:state.maps().get(i-offset);String id=m==null?RANDOM:m.id();String label=m==null?"랜덤 맵 · 임무":(state.selectedMap().equals(id)?"● ":"")+m.id()+" · "+m.name();
            listButton(Component.literal(label),b.x+5,b.y+27+(i-mapScroll)*21,b.w-10,18,true,id.equals(previewMap)?ACCENT:MUTED,()->{previewMap=id;rebuildWidgets();});
        }
        int w=(b.w-14)/2;
        button("미리보기",b.x+5,b.bottom()-23,w,18,true,MUTED,()->{maps=false;rebuildWidgets();});
        button("맵 적용",b.x+9+w,b.bottom()-23,b.w-w-14,18,admin()&&(map(previewMap)!=null||randomRow()&&RANDOM.equals(previewMap)),ACCENT,()->{LobbyClient.action("map",previewMap,"");maps=false;rebuildWidgets();});
    }
    private void scrollMaps(int next){mapScroll=Math.max(0,Math.min(maxMapScroll(),next));rebuildWidgets();}
    /** Raised panel tinted by {@code color}; a positive {@code header} adds a title band of that height. */
    private void panel(GuiGraphics g,Box b,int color,int header){
        int base=blend(PANEL,color,.05f);
        TacticalStyle.surface(g,b.x,b.y,b.w,b.h,blend(base,0xFFFFFFFF,.02f),base,blend(BORDER,color,.25f));
        TacticalStyle.band(g,b.x+1,b.y+1,b.right()-1,header,color,base);
    }
    private void text(GuiGraphics g,String value,int x,int y,int max,int color){g.drawString(font,font.plainSubstrByWidth(value,Math.max(1,max)),x,y,color,false);}
    /** Band title with a leading tick, a dim count and an optional right-aligned chip; returns the chip's left edge. */
    private int panelTitle(GuiGraphics g,Box b,String title,String count,String chip,int chipColor,int color){
        int chipX=b.right()-6;
        if(chip!=null){chipX-=font.width(chip)+8;badge(g,chip,chipX,b.y+5,chipColor);}
        g.fill(b.x+6,b.y+6,b.x+8,b.y+15,color);
        text(g,title,b.x+12,b.y+7,chipX-b.x-18,INK);
        int countX=b.x+12+font.width(title)+5;if(countX<chipX-6)text(g,count,countX,b.y+7,chipX-countX-6,DIM);
        return chipX;
    }
    private void teamPanel(GuiGraphics g,Box b,String title,List<LobbySnapshot.Member> members,int color){
        panel(g,b,color,18);long ready=members.stream().filter(LobbySnapshot.Member::ready).count();
        boolean all=!members.isEmpty()&&ready==members.size();
        panelTitle(g,b,title,members.size()+"명","준비 "+ready+"/"+members.size(),all?READY:MUTED,color);
        // Readiness doubles as the band's divider.
        int x1=b.x+1,x2=b.right()-1;g.fill(x1,b.y+19,x2,b.y+21,TacticalStyle.TRACK);
        if(ready>0)g.fill(x1,b.y+19,x1+Math.round((x2-x1)*ready/(float)members.size()),b.y+21,all?READY:color);
        if(members.isEmpty())text(g,"플레이어 없음 · 이름을 끌어 놓으세요",b.x+12,b.y+29,b.w-18,DIM);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fillGradient(0,0,width,height,0xF00C0D0A,0xF01B1D16);TacticalStyle.grid(g,0,0,width,height,24,0x07FFFFFF);
        var head=new Box(8,8,leftWidth(),34);panel(g,head,ACCENT,0);
        g.fill(head.x+7,head.y+7,head.x+9,head.y+27,ACCENT);
        text(g,"GUNGANG / MATCH LOBBY",head.x+14,head.y+7,leftWidth()-70,INK);text(g,"이름을 끌어 팀 이동 · "+(page+1)+" / "+pages()+" 페이지",head.x+14,head.y+20,leftWidth()-70,MUTED);
        teamPanel(g,alpha(),"알파",team("ALPHA"),LobbyClient.options.color("ALPHA"));teamPanel(g,bravo(),"브라보",team("BRAVO"),LobbyClient.options.color("BRAVO"));
        var b=spectators();panel(g,b,MUTED,18);panelTitle(g,b,"관전 / 미배정",neutral().size()+"명",null,0,MUTED);
        if(neutral().isEmpty())text(g,"관전자 없음",b.x+12,b.y+29,b.w-18,DIM);
        panel(g,controls(),MUTED,0);var p=preview();panel(g,p,ACCENT,maps?24:33);
        if(maps){g.fill(p.x+6,p.y+7,p.x+8,p.y+16,ACCENT);text(g,"맵 선택",p.x+12,p.y+8,p.w-64,INK);}else renderPreview(g);
        super.render(g,mx,my,partial);
        for(var row:rows()){
            var m=row.member();var skin=m.bot()?BotRenderer.texture(m.skin()):MatchHud.skin(m.id());
            g.fill(row.x+FACE-1,row.y+1,row.x+FACE+14,row.y+16,0xFF0A0B08);
            net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g,skin,row.x+FACE,row.y+2,13);
            PlayerLevels.draw(g,m.id(),row.x+EMBLEM,row.y+2,13);
            String status=status(m);badge(g,status,row.x+row.w-font.width(status)-11,row.y+3,statusColor(m));
        }
        if(draggingMember)renderDrag(g,mx,my);
    }
    private void renderPreview(GuiGraphics g){
        baseW=0;var b=preview();if(!java.util.Objects.equals(viewedMap,previewMap)){viewedMap=previewMap;zoom=1;panX=panY=0;draggingPreview=false;}
        var m=map(previewMap);if(m==null){text(g,RANDOM.equals(previewMap)?"랜덤 맵 · 임무":"선택된 맵 없음",b.x+6,b.y+9,b.w-12,INK);text(g,"맵 선택에서 적용할 맵을 고르세요",b.x+6,b.y+26,b.w-12,MUTED);return;}
        boolean selected=m.id().equals(state.selectedMap());
        String tag=selected?"현재 경기":"미리보기";int tagX=b.right()-6-font.width(tag)-8;badge(g,tag,tagX,b.y+7,selected?READY:MUTED);
        g.fill(b.x+6,b.y+7,b.x+8,b.y+29,ACCENT);
        text(g,m.name(),b.x+12,b.y+8,tagX-b.x-18,INK);text(g,selected?modeName(state.selectedMode()):m.gameMode(),b.x+12,b.y+20,b.w-18,ACCENT);
        int infoH=Math.min(70,Math.max(36,b.h/3)),infoY=b.bottom()-infoH;viewX=b.x+6;viewY=b.y+38;viewW=b.w-12;viewH=infoY-viewY-6;
        var image=PreviewCache.image(m);
        if(viewH>0&&viewW>0)g.fillGradient(viewX,viewY,viewX+viewW,viewY+viewH,0xFF1C1D17,0xFF0E0F0B);
        if(image.texture()!=null&&viewH>0&&viewW>0){
            float scale=Math.min(viewW/(float)image.width(),viewH/(float)image.height());baseW=Math.max(1,Math.round(image.width()*scale));baseH=Math.max(1,Math.round(image.height()*scale));clampPreview();
            int iw=(int)Math.round(baseW*zoom),ih=(int)Math.round(baseH*zoom),ix=viewX+(viewW-iw)/2+(int)Math.round(panX),iy=viewY+(viewH-ih)/2+(int)Math.round(panY);
            g.enableScissor(viewX,viewY,viewX+viewW,viewY+viewH);g.blit(image.texture(),ix,iy,iw,ih,0,0,image.width(),image.height(),image.width(),image.height());g.disableScissor();
            TacticalStyle.brackets(g,viewX,viewY,viewW,viewH,8,ACCENT);
            if(zoom>1.01){String zoomTag=String.format(java.util.Locale.ROOT,"x%.1f",zoom);badge(g,zoomTag,viewX+viewW-font.width(zoomTag)-12,viewY+viewH-15,ACCENT);}
        }else if(viewH>10)text(g,image.error(),viewX+4,viewY+4,viewW-8,DIM);
        String weather=switch(m.weather()==null?"clear":m.weather()){case "rain"->"비";case "snow"->"눈";case "fog"->"안개";default->"맑음";};String time=switch(m.timeOfDay()==null?"day":m.timeOfDay()){case "dawn"->"새벽";case "dusk"->"해질녘";case "night"->"밤";default->"낮";};
        int chipX=b.x+6;chipX+=badge(g,weather,chipX,infoY-2,MUTED)+3;chipX+=badge(g,time,chipX,infoY-2,MUTED)+6;
        text(g,"휠 확대 / 드래그 이동",chipX,infoY,b.right()-6-chipX,DIM);
        String description=selected&&"DEATHMATCH".equals(state.selectedMode())?state.options().deathmatch().timeLimitSeconds()+"초 · 목표 "+state.options().deathmatch().winningKills()+"킬":m.description();
        int y=infoY+13;for(var line:font.split(Component.literal(description==null?"":description),Math.max(1,b.w-12))){if(y+9>b.bottom()-17)break;g.drawString(font,line,b.x+6,y,MUTED,false);y+=11;}
        text(g,m.releaseDate()==null||m.releaseDate().isEmpty()?"맵 선택 → 맵 적용으로 경기 맵 변경":"출시 "+ReleaseDateText.format(m.releaseDate()),b.x+6,b.bottom()-12,b.w-12,DIM);
    }
    private String zoneAt(double x,double y){return alpha().contains(x,y)?"ALPHA":bravo().contains(x,y)?"BRAVO":spectators().contains(x,y)?"SPECTATOR":null;}
    private boolean overPreview(double x,double y){return !maps&&baseW>0&&x>=viewX&&x<viewX+viewW&&y>=viewY&&y<viewY+viewH;}
    private void clampPreview(){double maxX=Math.max(0,(baseW*zoom-viewW)/2),maxY=Math.max(0,(baseH*zoom-viewH)/2);panX=Math.max(-maxX,Math.min(maxX,panX));panY=Math.max(-maxY,Math.min(maxY,panY));}
    @Override public boolean mouseScrolled(double x,double y,double delta){
        if(maps&&preview().contains(x,y)){scrollMaps(mapScroll-(int)Math.signum(delta));return true;}
        if(zoneAt(x,y)!=null){page=Math.max(0,Math.min(pages()-1,page-(int)Math.signum(delta)));rebuildWidgets();return true;}
        if(!overPreview(x,y))return super.mouseScrolled(x,y,delta);
        double previous=zoom;zoom=Math.max(1,Math.min(8,zoom*Math.pow(1.2,delta)));double dx=x-viewX-viewW/2.0,dy=y-viewY-viewH/2.0;panX=dx-(dx-panX)*zoom/previous;panY=dy-(dy-panY)*zoom/previous;clampPreview();return true;
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button==0)for(var row:rows())if(x>=row.x&&x<row.x+row.w&&y>=row.y&&y<row.y+17&&(admin()||row.member.id().equals(state.you()))){dragId=row.member.id();dragX=x;dragY=y;draggingMember=false;target=dragId;break;}
        if(button==0&&overPreview(x,y)){draggingPreview=true;return true;}return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){
        if(button==0&&dragId!=null){if((x-dragX)*(x-dragX)+(y-dragY)*(y-dragY)>16)draggingMember=true;if(draggingMember)return true;}
        if(button==0&&draggingPreview){panX+=dx;panY+=dy;clampPreview();return true;}return super.mouseDragged(x,y,button,dx,dy);
    }
    @Override public boolean mouseReleased(double x,double y,int button){
        if(button==0&&dragId!=null){var moved=member(dragId);String zone=zoneAt(x,y);boolean dropped=draggingMember;
            if(dropped&&moved!=null&&zone!=null&&!zone.equals(moved.team())&&!(moved.bot()&&zone.equals("SPECTATOR")))LobbyClient.action("team",zone,moved.id());
            dragId=null;draggingMember=false;rebuildWidgets();if(dropped)return true;
        }
        if(button==0&&draggingPreview){draggingPreview=false;return true;}return super.mouseReleased(x,y,button);
    }
    private void renderDrag(GuiGraphics g,int mx,int my){
        var moved=member(dragId);if(moved==null)return;String zone=zoneAt(mx,my);
        if(zone!=null){
            var b=zone.equals("ALPHA")?alpha():zone.equals("BRAVO")?bravo():spectators();boolean allowed=!zone.equals(moved.team())&&!(moved.bot()&&zone.equals("SPECTATOR"));
            int color=allowed?READY:0xFFE05050;TacticalStyle.round(g,b.x,b.y,b.w,b.h,color&0xFFFFFF|0x26000000);TacticalStyle.roundFrame(g,b.x,b.y,b.w,b.h,color);
        }
        int w=font.width(moved.name())+NAME-1,x=mx+6,y=my-3;var skin=moved.bot()?BotRenderer.texture(moved.skin()):MatchHud.skin(moved.id());
        g.pose().pushPose();g.pose().translate(0,0,400);
        TacticalStyle.surface(g,x,y,w,19,blend(PANEL,ACCENT,.2f),PANEL,ACCENT);
        net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g,skin,x+FACE,y+3,13);PlayerLevels.draw(g,moved.id(),x+EMBLEM,y+3,13);
        g.drawString(font,moved.name(),x+NAME-4,y+6,INK,false);g.pose().popPose();
    }
}
