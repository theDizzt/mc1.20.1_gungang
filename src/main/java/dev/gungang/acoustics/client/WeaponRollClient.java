package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.ServerFiles;
import dev.gungang.acoustics.server.WeaponRotation;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * 랜덤 무기: the time to the next weapon change under the match HUD, and while a choice is open the offer cards (primary
 * and secondary each) with number keys to take one first-come, like 숨바꼭질's block choice.
 */
public final class WeaponRollClient {
    private static final int CARD=96,GAP=6,ROW=5,CARD_H=62;
    private static WeaponRotation.View state=WeaponRotation.View.none();
    private static final Map<String,ItemStack> ICONS=new HashMap<>();

    public static void receive(String json){
        WeaponRotation.View next=null;
        try{next=json==null?null:ServerFiles.GSON.fromJson(json,WeaponRotation.View.class);}catch(RuntimeException ignored){}
        state=next==null?WeaponRotation.View.none():next;
        if(!state.active())ICONS.clear();
    }
    private static boolean choosing(){return state.active()&&state.open()&&state.offers()!=null&&!state.offers().isEmpty()&&state.picked()<0;}
    /** Number keys 1..n take an offer while this player has not picked yet. */
    public static boolean key(int key,int scan,int action){
        var mc=Minecraft.getInstance();if(mc.screen!=null||!choosing())return false;
        int n=Math.min(9,state.offers().size());
        for(int i=0;i<n;i++)if(key==GLFW.GLFW_KEY_1+i){
            if(action==GLFW.GLFW_PRESS&&!state.taken().contains(i))LobbyClient.action("weaponPick",Integer.toString(i+1),"");
            return true;
        }
        return false;
    }
    private static ItemStack icon(String snbt){
        if(snbt==null||snbt.isEmpty())return ItemStack.EMPTY;
        return ICONS.computeIfAbsent(snbt,s->{try{return ItemStack.of(net.minecraft.nbt.TagParser.parseTag(s));}catch(Exception bad){return ItemStack.EMPTY;}});
    }
    /** Under the match HUD at {@code y}: the countdown, then the cards when a choice is open. */
    static void draw(GuiGraphics g,int y){
        if(!state.active())return;
        var font=Minecraft.getInstance().font;
        if(state.nextSeconds()>=0&&!state.open()){
            String text=Lang.tr("무기 교체까지 ")+state.nextSeconds()/60+":"+String.format("%02d",state.nextSeconds()%60);
            int w=font.width(text)+12,x=g.guiWidth()/2-w/2;
            g.fill(x,y+2,x+w,y+14,0x90141511);
            g.drawString(font,text,x+6,y+4,0xFFFFD84A,false);
        }
        if(!state.open()||state.offers()==null||state.offers().isEmpty())return;
        var offers=state.offers();int n=Math.min(9,offers.size()),columns=Math.min(n,ROW),rows=(n+ROW-1)/ROW;
        int width=columns*CARD+(columns-1)*GAP,left=(g.guiWidth()-width)/2,height=rows*CARD_H+(rows-1)*GAP;
        int top=Math.min(Math.round(g.guiHeight()*.56f),g.guiHeight()-height-24);
        String title=(state.first()?Lang.tr("시작 무기 선택"):Lang.tr("다음 무기 선택"))+Lang.tr(" (선착순) · ")+state.seconds()+Lang.tr("초");
        g.pose().pushPose();g.pose().translate(0,0,200);
        g.fill(left-6,top-16,left+width+6,top+height+16,0xB0101418);
        g.drawCenteredString(font,title,g.guiWidth()/2,top-12,0xFFFFFFFF);
        for(int i=0;i<n;i++){
            var card=offers.get(i);
            int x=left+(i%ROW)*(CARD+GAP),cy=top+(i/ROW)*(CARD_H+GAP);boolean picked=i==state.picked(),taken=!picked&&state.taken().contains(i);
            g.fill(x,cy,x+CARD,cy+CARD_H,picked?0x6070CDBF:taken?0x40000000:0x40FFFFFF);
            g.renderOutline(x,cy,CARD,CARD_H,picked?0xFF70CDBF:taken?0x40FF6B6B:0x60FFFFFF);
            g.drawString(font,"["+(i+1)+"]",x+3,cy+3,picked?0xFF70CDBF:taken?0xFF808080:0xFFFFD84A,true);
            Marquee.draw(g,font,card.category(),x+22,cy+3,CARD-26,0xFF9AA4B2,false);
            line(g,icon(card.primaryItem()),Lang.tr("주"),card.primary(),x,cy+15);
            line(g,icon(card.secondaryItem()),Lang.tr("보조"),card.secondary(),x,cy+37);
            if(taken){g.pose().pushPose();g.pose().translate(0,0,300);g.fill(x,cy,x+CARD,cy+CARD_H,0x90101418);g.drawCenteredString(font,Lang.tr("선점됨"),x+CARD/2,cy+CARD_H/2-4,0xFFFF6B6B);g.pose().popPose();}
        }
        String foot=state.picked()>=0?(state.first()?Lang.tr("선택 완료"):Lang.tr("선택 완료 · 교체 시간에 지급")):Lang.tr("숫자 키 1~")+n+Lang.tr(" · 선착순 · 미선택 시 남은 무기 중 무작위");
        g.drawCenteredString(font,foot,g.guiWidth()/2,top+height+4,0xFFB0B0B0);
        g.pose().popPose();
    }
    private static void line(GuiGraphics g,ItemStack stack,String label,String name,int x,int y){
        var font=Minecraft.getInstance().font;
        if(!stack.isEmpty())g.renderItem(stack,x+3,y);
        g.pose().pushPose();g.pose().translate(0,0,250);
        g.drawString(font,label,x+22,y,0xFF9AA4B2,false);
        Marquee.draw(g,font,name,x+22,y+9,CARD-25,0xFFE2E8F0,false);
        g.pose().popPose();
    }
    private WeaponRollClient(){}
}
