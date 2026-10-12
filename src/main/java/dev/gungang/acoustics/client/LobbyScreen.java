package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.LobbySnapshot;
import net.minecraft.client.gui.GuiGraphics;

final class LobbyScreen extends ArenaScreen {
    private LobbySnapshot state;
    private boolean maps;
    private int page;
    private String target;
    private String previewMap;
    @Override public void onClose(){
        super.onClose();
        minecraft.gui.setOverlayMessage(net.minecraft.network.chat.Component.literal(LobbyClient.LOBBY.getTranslatedKeyMessage().getString()+" 키 또는 /gungang lobby로 대기실 다시 열기"),false);
    }
    LobbyScreen(LobbySnapshot state) { super("GUNGANG / MATCH LOBBY"); this.state = state; target = state.you();previewMap=state.selectedMap();PreviewCache.clear(); }
    void update(LobbySnapshot next) { state = next; if (state.members().stream().noneMatch(m -> m.id().equals(target))) target = state.you(); rebuildWidgets(); }
    private int neutralCapacity() {return height<300?1:2;}
    private int neutralTop() {return height-91-neutralCapacity()*24;}
    private int capacity() { return Math.max(1, maps?(height-186)/27:(neutralTop()-120)/24); }
    @Override protected void init() {
        int x = left() + 12, w = body() - 24, half = (w - 8) / 2;
        button("플레이어 / 팀", x, 57, half, 22, true, !maps ? ACCENT : MUTED, () -> { maps = false; page = 0; rebuildWidgets(); });
        button("맵 선택", x + half + 8, 57, half, 22, true, maps ? ACCENT : MUTED, () -> { maps = true; page = 0; rebuildWidgets(); });
        var alpha=state.members().stream().filter(m->m.team().equals("ALPHA")).toList();
        var bravo=state.members().stream().filter(m->m.team().equals("BRAVO")).toList();
        var neutral=state.members().stream().filter(m->!m.team().equals("ALPHA")&&!m.team().equals("BRAVO")).toList();
        int count = maps ? state.maps().size() : Math.max(Math.max(alpha.size(),bravo.size()),((neutral.size()+neutralCapacity()-1)/neutralCapacity())*capacity());
        page = Math.min(page, Math.max(0, (count - 1) / capacity()));
        for (int i = page * capacity(); maps && i < Math.min(count, (page + 1) * capacity()); i++) {
            int y = 85 + (i - page * capacity()) * 27;
                var map = state.maps().get(i);
                button((state.selectedMap().equals(map.id()) ? "● " : "") + map.name(), x, y, Math.max(90,w/3), 24,
                        true,map.id().equals(previewMap)?ACCENT:MUTED, () -> {previewMap=map.id();if(state.administrator())LobbyClient.action("map", map.id(), "");rebuildWidgets();});
        }
        if(!maps) {
            members(alpha,x,100,half,LobbyClient.options.color("ALPHA"),capacity(),page*capacity());
            members(bravo,x+half+8,100,half,LobbyClient.options.color("BRAVO"),capacity(),page*capacity());
            members(neutral,x,neutralTop(),w,MUTED,neutralCapacity(),page*neutralCapacity());
        }
        int y = height - 90;
        button("‹", x, y, 28, 20, page > 0, MUTED, () -> { page--; rebuildWidgets(); });
        button("›", x + 34, y, 28, 20, (page + 1) * capacity() < count, MUTED, () -> { page++; rebuildWidgets(); });
        button("랜덤 팀 배치", x + 70, y, Math.max(50,w - 140), 20, state.administrator(), ACCENT, () -> LobbyClient.action("random", "", ""));
        closeButton(x + w - 62,y,62,20);
        int third = (w - 12) / 3;
        if(maps) {
            var selected=state.maps().stream().filter(m->m.id().equals(state.selectedMap())).findFirst().orElse(null);
            boolean allowed=state.administrator()&&selected!=null&&selected.deathmatch();
            button("쟁탈전",x,height-64,half,20,allowed,"CONTROL".equals(state.selectedMode())?ACCENT:MUTED,()->LobbyClient.action("mode","CONTROL",""));
            button("팀 데스매치",x+half+8,height-64,half,20,allowed,"DEATHMATCH".equals(state.selectedMode())?ACCENT:MUTED,()->LobbyClient.action("mode","DEATHMATCH",""));
        } else {
        colorButton("Team ALPHA", x, height - 64, third, 20, !maps, LobbyClient.options.color("ALPHA"), () -> LobbyClient.action("team", "ALPHA", target));
        colorButton("Team BRAVO", x + (third + 6) * 2, height - 64, third, 20, !maps, LobbyClient.options.color("BRAVO"), () -> LobbyClient.action("team", "BRAVO", target));
        button("관전자", x + third + 6, height - 64, third, 20, !maps, MUTED, () -> LobbyClient.action("team", "SPECTATOR", target));
        }
        var self = state.members().stream().filter(m -> m.id().equals(state.you())).findFirst().orElse(null);
        button(self != null && self.ready() ? "준비 취소" : "준비 완료", x, height - 38, w, 20,
                self != null && (self.team().equals("ALPHA") || self.team().equals("BRAVO")) && !state.selectedMap().isEmpty(), ACCENT, () -> LobbyClient.action("ready", "", ""));
    }
    private void members(java.util.List<LobbySnapshot.Member> list,int x,int y,int w,int color,int count,int offset) {
        for(int i=offset;i<Math.min(list.size(),offset+count);i++) {
            var member=list.get(i);
            button((member.id().equals(target)?"› ":"")+member.name()+" · "+(member.ready()?"READY":member.team().equals("UNASSIGNED")?"미배정":member.team().equals("SPECTATOR")?"관전자":"대기"),x,y+(i-offset)*24,w,21,
                    state.administrator()||member.id().equals(state.you()),color,()->{target=member.id();rebuildWidgets();});
        }
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        String map = state.maps().stream().filter(m -> m.id().equals(state.selectedMap())).map(LobbySnapshot.MapView::name).findFirst().orElse("활성 맵 없음 · 서버 config/gungang/server/maps의 enabled 확인");
        String mode="DEATHMATCH".equals(state.selectedMode())?"팀 데스매치":"NORMANDY".equals(state.selectedMode())?"노르망디":"쟁탈전";
        shell(g, map + " · "+mode+"  /  전원 준비 → "+("NORMANDY".equals(state.selectedMode())?45:30)+"초 준비");
        if(!maps) {
            int x=left()+12,w=body()-24,half=(w-8)/2;
            g.drawString(font,"ALPHA",x+4,85,LobbyClient.options.color("ALPHA"),false);
            g.drawString(font,"BRAVO",x+half+12,85,LobbyClient.options.color("BRAVO"),false);
            g.drawString(font,"미배정 / 관전자",x+4,neutralTop()-13,MUTED,false);
        }
        if (maps) {
            renderPreview(g);
            var selected = state.maps().stream().filter(m -> m.id().equals(state.selectedMap())).findFirst().orElse(null);
            if (selected != null) {
                String description="DEATHMATCH".equals(state.selectedMode())?state.options().deathmatch().timeLimitSeconds()+"초 · 목표 "+state.options().deathmatch().winningKills()+"킬 · 동점 시 무승부":selected.description();
                g.drawString(font, font.plainSubstrByWidth(description, Math.max(1,body() - 24)), left() + 12, height - 103, MUTED, false);
            }
        }
        super.render(g, mouseX, mouseY, partial);
    }
    private void renderPreview(GuiGraphics g) {
        var selected=state.maps().stream().filter(m->m.id().equals(previewMap)).findFirst().orElse(null);
        if(selected==null)return;
        int x=left()+12+Math.max(90,(body()-24)/3)+8,right=left()+body()-12,w=right-x,top=85,bottom=height-110;
        g.fill(x,top,right,bottom,0xFF101A28);
        g.drawString(font,font.plainSubstrByWidth(selected.name()+" · "+selected.gameMode(),Math.max(1,w-12)),x+6,top+6,INK,false);
        var image=PreviewCache.image(selected);int imageY=top+22,availableH=bottom-imageY-5;
        if(image.texture()!=null&&availableH>0) {
            float scale=Math.min((w-12f)/image.width(),availableH/(float)image.height());
            int iw=Math.max(1,Math.round(image.width()*scale)),ih=Math.max(1,Math.round(image.height()*scale));
            g.blit(image.texture(),x+(w-iw)/2,imageY+(availableH-ih)/2,iw,ih,0,0,image.width(),image.height(),image.width(),image.height());
        }else if(availableH>0) {
            int y=imageY+4;for(var line:font.split(net.minecraft.network.chat.Component.literal(image.error()),Math.max(1,w-12))) {
                if(y+9>bottom)break;g.drawString(font,line,x+6,y,MUTED,false);y+=12;
            }
        }
    }
}
