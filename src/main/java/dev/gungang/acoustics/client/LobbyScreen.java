package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.LobbySnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class LobbyScreen extends ArenaScreen {
    private static final int WAITING = 0xFFE0B860;
    private LobbySnapshot state;
    private boolean maps;
    private int page;
    /** First visible row of the map list; the wheel moves it one row at a time. */
    private int mapScroll;
    private String target;
    private String previewMap;
    private String viewedMap;
    private double zoom=1,panX,panY;
    private int viewX,viewY,viewW,viewH,baseW,baseH;
    private boolean draggingPreview,draggingMapBar;
    private void dragMapBar(double y){scrollMaps((int)Math.round((y-85)/(capacity()*27.0-3)*maxMapScroll()));}
    private boolean overPreview(double x,double y){return maps&&baseW>0&&x>=viewX&&x<viewX+viewW&&y>=viewY&&y<viewY+viewH;}
    private void clampPreview(){
        double maxX=Math.max(0,(baseW*zoom-viewW)/2),maxY=Math.max(0,(baseH*zoom-viewH)/2);
        panX=Math.max(-maxX,Math.min(maxX,panX));panY=Math.max(-maxY,Math.min(maxY,panY));
    }
    private int mapRows(){return state.maps().size()+(randomRow()?1:0);}
    private int maxMapScroll(){return Math.max(0,mapRows()-capacity());}
    private int mapListWidth(){return Math.max(90,(body()-24)/3);}
    private boolean overMapList(double x,double y){int lx=left()+12;return maps&&x>=lx&&x<lx+mapListWidth()&&y>=85&&y<85+capacity()*27;}
    private boolean overMapBar(double x,double y){int edge=left()+12+mapListWidth();return maps&&maxMapScroll()>0&&x>=edge-8&&x<=edge+2&&y>=85&&y<85+capacity()*27;}
    private void scrollMaps(int rows){int next=Math.max(0,Math.min(maxMapScroll(),rows));if(next!=mapScroll){mapScroll=next;rebuildWidgets();}}
    @Override public boolean mouseScrolled(double x,double y,double delta){
        if(overMapList(x,y)){scrollMaps(mapScroll-(int)Math.signum(delta));return true;}
        if(!overPreview(x,y))return super.mouseScrolled(x,y,delta);
        double previous=zoom;zoom=Math.max(1,Math.min(8,zoom*Math.pow(1.2,delta)));
        double dx=x-viewX-viewW/2.0,dy=y-viewY-viewH/2.0;
        panX=dx-(dx-panX)*zoom/previous;panY=dy-(dy-panY)*zoom/previous;clampPreview();return true;
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button==0&&overPreview(x,y)){draggingPreview=true;return true;}
        if(button==0&&overMapBar(x,y)){draggingMapBar=true;dragMapBar(y);return true;}
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){
        if(button==0&&draggingPreview){panX+=dx;panY+=dy;clampPreview();return true;}
        if(button==0&&draggingMapBar){dragMapBar(y);return true;}
        return super.mouseDragged(x,y,button,dx,dy);
    }
    @Override public boolean mouseReleased(double x,double y,int button){
        if(button==0&&draggingPreview){draggingPreview=false;return true;}
        if(button==0&&draggingMapBar){draggingMapBar=false;return true;}
        return super.mouseReleased(x,y,button);
    }
    private static final String RANDOM = dev.gungang.acoustics.server.LobbyRules.RANDOM_MAP;
    /** Set when an admin rolls 00, so the preview jumps to whatever the server picked. */
    private boolean followRandom;
    /** The synthetic 00 row sits above the maps; a real map with id 00 replaces it. */
    private boolean randomRow() { return !state.maps().isEmpty() && state.maps().stream().noneMatch(m -> m.id().equals(RANDOM)); }
    @Override public void onClose(){
        super.onClose();
        minecraft.gui.setOverlayMessage(Component.literal(LobbyClient.LOBBY.getTranslatedKeyMessage().getString()+" 키 또는 /gungang lobby로 대기실 다시 열기"),false);
    }
    LobbyScreen(LobbySnapshot state) { super("GUNGANG / MATCH LOBBY"); this.state = state; target = state.you();previewMap=state.selectedMap();PreviewCache.clear(); }
    void update(LobbySnapshot next) {
        if (followRandom && !next.selectedMap().isEmpty()) { previewMap = next.selectedMap(); followRandom = false; }
        state = next; if (state.members().stream().noneMatch(m -> m.id().equals(target))) target = state.you(); rebuildWidgets(); }
    private int neutralCapacity() {return height<300?1:2;}
    private int neutralTop() {return height-91-neutralCapacity()*24;}
    private int capacity() { return Math.max(1, maps?(height-186)/27:(neutralTop()-120)/24); }
    private java.util.List<LobbySnapshot.Member> team(String team) { return state.members().stream().filter(m->m.team().equals(team)).toList(); }
    private java.util.List<LobbySnapshot.Member> neutral() { return state.members().stream().filter(m->!m.team().equals("ALPHA")&&!m.team().equals("BRAVO")).toList(); }
    @Override protected void init() {
        int x = left() + 12, w = body() - 24, half = (w - 8) / 2;
        button("플레이어 / 팀", x, 57, half, 22, true, !maps ? ACCENT : MUTED, () -> { maps = false; page = 0; rebuildWidgets(); });
        button("맵 선택", x + half + 8, 57, half, 22, true, maps ? ACCENT : MUTED, () -> { maps = true; page = 0; mapScroll = 0; rebuildWidgets(); });
        var alpha=team("ALPHA");
        var bravo=team("BRAVO");
        var neutral=neutral();
        int offset = randomRow() ? 1 : 0;
        int count = maps ? state.maps().size() + offset : Math.max(Math.max(alpha.size(),bravo.size()),((neutral.size()+neutralCapacity()-1)/neutralCapacity())*capacity());
        page = Math.min(page, Math.max(0, (count - 1) / capacity()));
        mapScroll = Math.min(mapScroll, maxMapScroll());
        int listW = mapListWidth() - (maxMapScroll() > 0 ? 6 : 0);
        for (int i = mapScroll; maps && i < Math.min(count, mapScroll + capacity()); i++) {
            int y = 85 + (i - mapScroll) * 27;
                if (i < offset) {
                    var label = Component.literal("랜덤 · 맵·모드 무작위").withStyle(s -> s.withColor(0xE0B860));
                    listButton(label, x, y, listW, 24, true, RANDOM.equals(previewMap) ? ACCENT : MUTED, () -> {
                        previewMap = RANDOM;
                        if (state.administrator()) { followRandom = true; LobbyClient.action("map", RANDOM, ""); }
                        rebuildWidgets();
                    });
                    continue;
                }
                var map = state.maps().get(i - offset);
                var label = Component.empty();
                if(state.selectedMap().equals(map.id()))label.append(Component.literal("● ").withStyle(s->s.withColor(ACCENT&0xFFFFFF)));
                label.append(Component.literal(map.id()+"  ").withStyle(s->s.withColor(0xC8B060))).append(Component.literal(map.name()));
                listButton(label, x, y, listW, 24,
                        true,map.id().equals(previewMap)?ACCENT:MUTED, () -> {previewMap=map.id();if(state.administrator())LobbyClient.action("map", map.id(), "");rebuildWidgets();});
        }
        if(!maps) {
            members(alpha,x+4,100,half-8,LobbyClient.options.color("ALPHA"),capacity(),page*capacity());
            members(bravo,x+half+12,100,half-8,LobbyClient.options.color("BRAVO"),capacity(),page*capacity());
            members(neutral,x+4,neutralTop(),w-8,MUTED,neutralCapacity(),page*neutralCapacity());
        }
        int y = height - 90;
        if (maps) {
            button("‹", x, y, 28, 20, mapScroll > 0, MUTED, () -> scrollMaps(mapScroll - capacity()));
            button("›", x + 34, y, 28, 20, mapScroll < maxMapScroll(), MUTED, () -> scrollMaps(mapScroll + capacity()));
        } else {
            button("‹", x, y, 28, 20, page > 0, MUTED, () -> { page--; rebuildWidgets(); });
            button("›", x + 34, y, 28, 20, (page + 1) * capacity() < count, MUTED, () -> { page++; rebuildWidgets(); });
        }
        button("랜덤 팀 배치", x + 70, y, Math.max(50,w - 140), 20, state.administrator(), ACCENT, () -> LobbyClient.action("random", "", ""));
        closeButton(x + w - 62,y,62,20);
        int third = (w - 12) / 3;
        if(maps) {
            var selected=state.maps().stream().filter(m->m.id().equals(state.selectedMap())).findFirst().orElse(null);
            boolean allowed=state.administrator()&&selected!=null&&selected.deathmatch();
            String objectiveMode=selected!=null&&"숨바꼭질".equals(selected.gameMode())?"PROP_HUNT":selected!=null&&"개인전".equals(selected.gameMode())?"FREE_FOR_ALL":selected!=null&&"호위".equals(selected.gameMode())?"ESCORT":selected!=null&&selected.bomb()?"BOMB":"CONTROL";
            if(!objectiveMode.equals("PROP_HUNT")) {
                // Any map can also be played as prop hunt: objective · deathmatch · prop hunt.
                button(objectiveMode.equals("FREE_FOR_ALL")?"개인전":objectiveMode.equals("ESCORT")?"호위":objectiveMode.equals("BOMB")?"폭탄전":"쟁탈전",x,height-64,third,20,selected!=null&&state.administrator()&&(objectiveMode.equals("FREE_FOR_ALL")||objectiveMode.equals("ESCORT")||allowed&&!selected.deathmatchOnly()),objectiveMode.equals(state.selectedMode())?ACCENT:MUTED,()->LobbyClient.action("mode",objectiveMode,""));
                button("팀 데스매치",x+third+6,height-64,third,20,allowed,"DEATHMATCH".equals(state.selectedMode())?ACCENT:MUTED,()->LobbyClient.action("mode","DEATHMATCH",""));
                button("숨바꼭질",x+(third+6)*2,height-64,third,20,selected!=null&&state.administrator(),"PROP_HUNT".equals(state.selectedMode())?ACCENT:MUTED,()->LobbyClient.action("mode","PROP_HUNT",""));
            } else
            button(objectiveMode.equals("PROP_HUNT")?"숨바꼭질":objectiveMode.equals("FREE_FOR_ALL")?"개인전":objectiveMode.equals("ESCORT")?"호위":objectiveMode.equals("BOMB")?"폭탄전":"쟁탈전",x,height-64,half,20,selected!=null&&state.administrator()&&(objectiveMode.equals("PROP_HUNT")||objectiveMode.equals("FREE_FOR_ALL")||objectiveMode.equals("ESCORT")||allowed&&!selected.deathmatchOnly()),objectiveMode.equals(state.selectedMode())?ACCENT:MUTED,()->LobbyClient.action("mode",objectiveMode,""));
            if(objectiveMode.equals("PROP_HUNT"))button("팀 데스매치",x+half+8,height-64,half,20,allowed,"DEATHMATCH".equals(state.selectedMode())?ACCENT:MUTED,()->LobbyClient.action("mode","DEATHMATCH",""));
        } else {
        colorButton("FREE_FOR_ALL".equals(state.selectedMode())?"참가":"Team ALPHA", x, height - 64, third, 20, !maps, LobbyClient.options.color("ALPHA"), () -> LobbyClient.action("team", "ALPHA", target));
        colorButton("FREE_FOR_ALL".equals(state.selectedMode())?"참가":"Team BRAVO", x + (third + 6) * 2, height - 64, third, 20, !maps, LobbyClient.options.color("BRAVO"), () -> LobbyClient.action("team", "BRAVO", target));
        button("관전자", x + third + 6, height - 64, third, 20, !maps, MUTED, () -> LobbyClient.action("team", "SPECTATOR", target));
        }
        var self = state.members().stream().filter(m -> m.id().equals(state.you())).findFirst().orElse(null);
        boolean canReady = self != null && (self.team().equals("ALPHA") || self.team().equals("BRAVO")) && !state.selectedMap().isEmpty();
        Runnable ready = () -> LobbyClient.action("ready", "", "");
        if (self != null && self.ready()) button("준비 취소", x, height - 38, w, 20, canReady, WAITING, ready);
        else colorButton("준비 완료", x, height - 38, w, 20, canReady, ACCENT, ready);
    }
    private void members(java.util.List<LobbySnapshot.Member> list,int x,int y,int w,int color,int count,int offset) {
        for(int i=offset;i<Math.min(list.size(),offset+count);i++) {
            var member=list.get(i);
            String status=member.ready()?"READY":member.team().equals("UNASSIGNED")?"미배정":member.team().equals("SPECTATOR")?"관전자":"대기";
            int statusColor=member.ready()?READY:member.team().equals("UNASSIGNED")?DIM:member.team().equals("SPECTATOR")?MUTED:WAITING;
            var label=Component.empty();
            if(member.id().equals(target))label.append(Component.literal("› ").withStyle(s->s.withColor(ACCENT&0xFFFFFF)));
            label.append(Component.literal(member.name()));
            if(member.id().equals(state.you()))label.append(Component.literal(" (나)").withStyle(s->s.withColor(ACCENT&0xFFFFFF)));
            label.append(Component.literal(" · ").withStyle(s->s.withColor(DIM&0xFFFFFF)))
                    .append(Component.literal(status).withStyle(s->s.withColor(statusColor&0xFFFFFF)));
            listButton(label,x,y+(i-offset)*24,w,21,
                    state.administrator()||member.id().equals(state.you()),member.id().equals(target)?color:MUTED,()->{target=member.id();rebuildWidgets();});
        }
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        var selectedMap = state.maps().stream().filter(m -> m.id().equals(state.selectedMap())).map(LobbySnapshot.MapView::name).findFirst();
        String mode="PROP_HUNT".equals(state.selectedMode())?"숨바꼭질":"FREE_FOR_ALL".equals(state.selectedMode())?"개인전":"ESCORT".equals(state.selectedMode())?"호위":"BOMB".equals(state.selectedMode())?"폭탄전":"DEATHMATCH".equals(state.selectedMode())?"팀 데스매치":"NORMANDY".equals(state.selectedMode())?"노르망디":"쟁탈전";
        var subtitle = Component.empty()
                .append(Component.literal(selectedMap.orElse("활성 맵 없음 · 서버 config/gungang/server/maps의 enabled 확인")).withStyle(s -> s.withColor((selectedMap.isPresent() ? INK : 0xFFE08060) & 0xFFFFFF)))
                .append(Component.literal(" · ").withStyle(s -> s.withColor(DIM & 0xFFFFFF)))
                .append(Component.literal(mode).withStyle(s -> s.withColor(ACCENT & 0xFFFFFF)))
                .append(Component.literal("  /  전원 준비 → "+("NORMANDY".equals(state.selectedMode())?45:30)+"초 준비"));
        shell(g, subtitle);
        if(!maps) {
            int x=left()+12,w=body()-24,half=(w-8)/2;
            teamCard(g,"ALPHA",team("ALPHA"),x,x+half,LobbyClient.options.color("ALPHA"));
            teamCard(g,"BRAVO",team("BRAVO"),x+half+8,x+w,LobbyClient.options.color("BRAVO"));
            var neutral=neutral();
            int top=neutralTop()-17;
            card(g,x,top,x+w,neutralTop()+neutralCapacity()*24-2);
            int pages=pageCount();
            cardHeader(g,x,top,x+w,"미배정 / 관전자",MUTED,neutral.size()+"명"+(pages>1?"  ·  페이지 "+(page+1)+"/"+pages:""));
            if(neutral.isEmpty())g.drawString(font,"대기 중인 플레이어 없음",x+8,neutralTop()+6,DIM,false);
        }
        if (maps) {
            renderPreview(g);
            int rows=mapRows(),max=maxMapScroll();
            if(max>0){
                int edge=left()+12+mapListWidth(),track=capacity()*27-3,thumb=Math.max(12,track*capacity()/rows),ty=85+(track-thumb)*mapScroll/max;
                g.fill(edge-4,85,edge-1,85+track,0xFF1E2B3B);g.fill(edge-4,ty,edge-1,ty+thumb,ACCENT);
            }
            var selected = state.maps().stream().filter(m -> m.id().equals(state.selectedMap())).findFirst().orElse(null);
            if (selected != null) {
                String description="DEATHMATCH".equals(state.selectedMode())?state.options().deathmatch().timeLimitSeconds()+"초 · 목표 "+state.options().deathmatch().winningKills()+"킬 · 동점 시 무승부":selected.description();
                String release=selected.releaseDate()==null||selected.releaseDate().isEmpty()?"":"출시 "+selected.releaseDate().replace('-','.');
                int releaseWidth=release.isEmpty()?0:font.width(release)+12;
                g.fill(left() + 12, height - 104, left() + 14, height - 94, ACCENT);
                g.drawString(font, font.plainSubstrByWidth(description, Math.max(1,body() - 30 - releaseWidth)), left() + 18, height - 103, MUTED, false);
                if(!release.isEmpty())g.drawString(font, release, left() + body() - 12 - font.width(release), height - 103, DIM, false);
            }
        }
        super.render(g, mouseX, mouseY, partial);
    }
    private int pageCount() {
        int count=Math.max(Math.max(team("ALPHA").size(),team("BRAVO").size()),((neutral().size()+neutralCapacity()-1)/neutralCapacity())*capacity());
        return Math.max(1,(count+capacity()-1)/capacity());
    }
    private void teamCard(GuiGraphics g,String name,java.util.List<LobbySnapshot.Member> list,int x1,int x2,int color) {
        int top=81,bottom=100+capacity()*24-2;
        card(g,x1,top,x2,bottom);
        long ready=list.stream().filter(LobbySnapshot.Member::ready).count();
        cardHeader(g,x1,top,x2,name,color,list.size()+"명 · 준비 "+ready+"/"+list.size());
        if(list.isEmpty())g.drawString(font,"플레이어 없음",x1+8,106,DIM,false);
    }
    private void renderPreview(GuiGraphics g) {
        baseW=0;
        if(!java.util.Objects.equals(viewedMap,previewMap)){viewedMap=previewMap;zoom=1;panX=panY=0;draggingPreview=false;}
        var selected=state.maps().stream().filter(m->m.id().equals(previewMap)).findFirst().orElse(null);
        int x=left()+12+Math.max(90,(body()-24)/3)+8,right=left()+body()-12,w=right-x,top=85,bottom=height-110;
        if(selected==null&&RANDOM.equals(previewMap)&&randomRow()&&bottom>top+40) {
            card(g,x,top,right,bottom);
            g.fillGradient(x,top,right,top+18,0xFF1B293C,PANEL);g.fill(x,top,right,top+1,0xFFE0B860);
            g.drawString(font,"랜덤 맵 · 모드",x+6,top+6,INK,false);
            int y=top+28;
            for(String text:new String[]{"활성화된 맵 중 하나와 그 맵에서 가능한 게임모드를 무작위로 고릅니다.",
                    "가능하면 현재 선택과 다른 조합을 고르며, 결과는 로비 채팅에 표시됩니다.",
                    state.administrator()?"목록의 랜덤을 다시 누르면 다시 뽑습니다.":"관리자만 랜덤 선택을 실행할 수 있습니다."})
                for(var line:font.split(Component.literal(text),Math.max(1,w-12))){if(y+9>bottom)return;g.drawString(font,line,x+6,y,MUTED,false);y+=12;}
            return;
        }
        if(selected==null) {
            if(bottom>top){card(g,x,top,right,bottom);g.drawString(font,"왼쪽 목록에서 맵을 선택하세요",x+8,top+8,DIM,false);}
            return;
        }
        card(g,x,top,right,bottom);
        g.fillGradient(x,top,right,top+18,0xFF1B293C,PANEL);
        g.fill(x,top,right,top+1,ACCENT);
        int tagX=right-6;
        if(selected.id().equals(state.selectedMap())){String tag="현재 선택";tagX-=font.width(tag)+8;badge(g,tag,tagX,top+4,READY);tagX-=4;}
        String modeTag=selected.gameMode();
        if(modeTag!=null&&!modeTag.isEmpty()){tagX-=font.width(modeTag)+8;badge(g,modeTag,tagX,top+4,ACCENT);tagX-=4;}
        g.drawString(font,font.plainSubstrByWidth(selected.name(),Math.max(1,tagX-x-8)),x+6,top+6,INK,false);
        String weather=switch(selected.weather()==null?"clear":selected.weather()){case "rain"->"비";case "snow"->"눈";case "fog"->"안개";default->"맑음";};
        String time=switch(selected.timeOfDay()==null?"day":selected.timeOfDay()){case "dawn"->"새벽";case "dusk"->"해질녘";case "night"->"밤";default->"낮";};
        g.drawString(font,"날씨: "+weather+" · 시간대: "+time,x+6,top+23,MUTED,false);
        g.drawString(font,font.plainSubstrByWidth("휠: 확대/축소 · 드래그: 이동",Math.max(1,w-12)),x+6,bottom-12,DIM,false);
        var image=PreviewCache.image(selected);int imageY=top+37,availableH=bottom-imageY-17;
        if(image.texture()!=null&&availableH>0) {
            float scale=Math.min((w-12f)/image.width(),availableH/(float)image.height());
            baseW=Math.max(1,Math.round(image.width()*scale));baseH=Math.max(1,Math.round(image.height()*scale));
            viewX=x+6;viewY=imageY;viewW=w-12;viewH=availableH;clampPreview();
            int iw=Math.max(1,(int)Math.round(baseW*zoom)),ih=Math.max(1,(int)Math.round(baseH*zoom));
            int ix=viewX+(viewW-iw)/2+(int)Math.round(panX),iy=imageY+(availableH-ih)/2+(int)Math.round(panY);
            g.enableScissor(viewX,viewY,viewX+viewW,viewY+viewH);
            g.fill(ix-1,iy-1,ix+iw+1,iy+ih+1,BORDER);
            g.blit(image.texture(),ix,iy,iw,ih,0,0,image.width(),image.height(),image.width(),image.height());
            g.disableScissor();
        }else if(availableH>0) {
            int y=imageY+4;for(var line:font.split(Component.literal(image.error()),Math.max(1,w-12))) {
                if(y+9>bottom)break;g.drawString(font,line,x+6,y,MUTED,false);y+=12;
            }
        }
    }
}
