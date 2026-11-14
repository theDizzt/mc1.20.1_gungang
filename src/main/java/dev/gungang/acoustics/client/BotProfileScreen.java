package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.BotManager.ProfileView;
import dev.gungang.acoustics.server.LobbySnapshot;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Lobby: picks which of the stored bot profiles (name, skin, 4-digit tag, career) a lobby bot plays as. Profiles
 * another lobby bot already uses are shown but cannot be taken. Administrators only; the server checks again.
 */
final class BotProfileScreen extends ArenaScreen {
    private static final int GAP=4;
    private final LobbyScreen parent;
    private final String bot;
    BotProfileScreen(LobbyScreen parent,String bot){super("GUNGANG / BOT DATA");this.parent=parent;this.bot=bot;}

    void update(LobbySnapshot state){
        parent.update(state);
        if(member(state,bot)==null)minecraft.setScreen(parent); // removed meanwhile
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    private static LobbySnapshot.Member member(LobbySnapshot state,String id){
        for(var m:state.members())if(m.id().equals(id))return m;return null;
    }
    /** Ids of the profiles other lobby bots play as. */
    private Set<String> taken(){
        var set=new HashSet<String>();
        for(var m:parent.state().members())if(m.bot()&&!m.id().equals(bot))set.add(m.id());
        return set;
    }
    private int columns(){return body()-24>=480?4:3;}
    private int gridLeft(){return left()+12;}
    private int gridTop(){return 62;}
    private int cardWidth(){return (body()-24-GAP*(columns()-1))/columns();}
    private int cardHeight(){
        int rows=(BotProfilesClient.profiles().size()+columns()-1)/Math.max(1,columns());
        return Math.max(30,Math.min(52,(height-62-58-GAP*(Math.max(1,rows)-1))/Math.max(1,rows)));
    }
    @Override protected void init(){
        int w=body()-24;
        closeButton(gridLeft()+w-80,height-48,80,22);
        button(Lang.tr("← 대기실"),gridLeft()+w-80-6-80,height-48,80,22,true,MUTED,this::onClose);
    }
    private ProfileView at(double x,double y){
        var list=BotProfilesClient.profiles();int cw=cardWidth(),ch=cardHeight();
        for(int i=0;i<list.size();i++){
            int cx=gridLeft()+(i%columns())*(cw+GAP),cy=gridTop()+(i/columns())*(ch+GAP);
            if(x>=cx&&x<cx+cw&&y>=cy&&y<cy+ch)return list.get(i);
        }
        return null;
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(super.mouseClicked(x,y,button))return true;
        var p=button==0?at(x,y):null;
        if(p==null||taken().contains(p.id()))return false;
        if(!p.id().equals(bot)){
            LobbyClient.action("botProfile",Integer.toString(p.index()),bot);
            parent.pick(p.id());
        }
        minecraft.setScreen(parent);
        return true;
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick){
        var current=member(parent.state(),bot);
        shell(g,(current==null?"":current.name()+" · ")+Lang.tr("플레이할 봇 데이터를 선택하세요"));
        var list=BotProfilesClient.profiles();
        if(list.isEmpty())g.drawString(font,Lang.tr("봇 데이터를 불러오는 중..."),gridLeft(),gridTop()+6,MUTED,false);
        var taken=taken();var hovered=at(mouseX,mouseY);
        int cw=cardWidth(),ch=cardHeight();
        for(int i=0;i<list.size();i++){
            var p=list.get(i);
            int x=gridLeft()+(i%columns())*(cw+GAP),y=gridTop()+(i/columns())*(ch+GAP);
            card(g,p,x,y,cw,ch,p.id().equals(bot),taken.contains(p.id()),p==hovered);
        }
        super.render(g,mouseX,mouseY,partialTick);
    }
    private void card(GuiGraphics g,ProfileView p,int x,int y,int w,int h,boolean selected,boolean taken,boolean hover){
        int base=selected?blend(TacticalStyle.TRACK,ACCENT,.16f):hover&&!taken?blend(TacticalStyle.TRACK,INK,.06f):TacticalStyle.TRACK;
        TacticalStyle.tile(g,x,y,w,h,base,selected?ACCENT:TacticalStyle.HAIRLINE);
        int face=Math.min(h-8,32);
        net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g,BotRenderer.texture(p.skin()),x+5,y+(h-face)/2,face);
        if(taken)g.fill(x+5,y+(h-face)/2,x+5+face,y+(h-face)/2+face,0x99141511);
        String number=Integer.toString(p.index()+1);
        int tx=x+face+10,room=Math.max(1,w-(tx-x)-6),nameRoom=Math.max(1,room-font.width(number)-4);
        var name=Component.literal(p.name()).append(dev.gungang.acoustics.server.PlayerTag.component(p.tag()));
        Marquee.draw(g,font,name,tx,y+5,nameRoom,taken?DIM:INK,false);
        String status=selected?Lang.tr("현재"):taken?Lang.tr("사용 중"):"";
        if(h>=40){
            LevelEmblem.draw(g,p.level(),tx,y+16,11);
            if(!status.isEmpty())g.drawString(font,status,tx+15,y+18,selected?ACCENT:MUTED,false);
        }else g.drawString(font,"Lv."+p.level()+(status.isEmpty()?"":"  ·  "+status),tx,y+18,selected?ACCENT:MUTED,false);
        if(h>=40){
            String record=String.format(Locale.ROOT,Lang.tr("%d전 %d승 · K/D %.2f"),p.matches(),p.wins(),p.kills()/(double)Math.max(1,p.deaths()));
            Marquee.draw(g,font,record,tx,y+h-12,room,taken?DIM:MUTED,false);
        }
        g.drawString(font,number,x+w-5-font.width(number),y+5,DIM,false);
    }
}
