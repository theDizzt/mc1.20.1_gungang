package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.PropCatalog;
import dev.gungang.acoustics.server.PropHuntSnapshot;
import dev.gungang.acoustics.server.PropInteraction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** The hider's block choice: up to three cards with icon, key, name and tags, the pick outlined, and the time left. */
final class PropChoiceHud {
    private static final int CARD=74,GAP=6,ICON=32,ROW=5;
    static void draw(GuiGraphics g,PropHuntSnapshot s){
        var offers=s.offers();if(offers==null||offers.isEmpty())return;
        var mc=Minecraft.getInstance();var font=mc.font;
        // A first-come list can hold up to nine blocks: five per row.
        int n=offers.size(),columns=Math.min(n,ROW),rows=(n+ROW-1)/ROW,cardH=ICON+28;
        int width=columns*CARD+(columns-1)*GAP,left=(g.guiWidth()-width)/2,height=rows*cardH+(rows-1)*GAP;
        int top=Math.min(Math.round(g.guiHeight()*.56f),g.guiHeight()-height-24);
        boolean shared=s.sharedChoice();
        String title=(s.firstChoice()?Lang.tr("변신할 블록 선택"):Lang.tr("다음 블록 선택"))+(shared?Lang.tr(" (선착순)"):"")+" · "+s.choiceSeconds()+Lang.tr("초");
        g.pose().pushPose();g.pose().translate(0,0,200);
        g.fill(left-6,top-16,left+width+6,top+height+16,0xB0101418);
        g.drawCenteredString(font,title,g.guiWidth()/2,top-12,0xFFFFFFFF);
        for(int i=0;i<n;i++){
            String type=offers.get(i),block=PropCatalog.display(type);
            int x=left+(i%ROW)*(CARD+GAP),y=top+(i/ROW)*(cardH+GAP);boolean picked=i==s.picked(),taken=!picked&&s.taken(i);
            g.fill(x,y,x+CARD,y+ICON+28,picked?0x6070CDBF:taken?0x40000000:0x40FFFFFF);
            g.renderOutline(x,y,CARD,ICON+28,picked?0xFF70CDBF:taken?0x40FF6B6B:0x60FFFFFF);
            g.drawString(font,"["+(i+1)+"]",x+3,y+3,picked?0xFF70CDBF:taken?0xFF808080:0xFFFFD84A,true);
            PropAbilityHud.icon(g,block,PropCatalog.state(block),x+(CARD-ICON)/2,y+4,ICON);
            g.pose().pushPose();g.pose().translate(0,0,300);
            String name=PropCatalog.name(block).getString();
            Marquee.draw(g,font,name,x+CARD/2-Math.min(CARD-4,font.width(name))/2,y+ICON+6,CARD-4,0xFFE2E8F0,true);
            var tags=PropInteraction.tagComponent(block,false).append(net.minecraft.network.chat.Component.literal("["+PropCatalog.number(block)+"]").withStyle(net.minecraft.ChatFormatting.GRAY));
            // The black "은신" tag leads the line with a light outline; the coloured tags and number follow.
            String stealth=PropInteraction.stealth(block)?Lang.tr("은신 "):"";
            int lineW=font.width(stealth)+font.width(tags),lineX=x+CARD/2-lineW/2;
            if(!stealth.isEmpty())TaczAmmoHud.text(g,font,Lang.tr("은신"),lineX,y+ICON+17,0xFF000000);
            g.drawString(font,tags,lineX+font.width(stealth),y+ICON+17,0xFFFFFFFF);
            if(taken){g.fill(x,y,x+CARD,y+ICON+28,0x90101418);g.drawCenteredString(font,Lang.tr("선점됨"),x+CARD/2,y+ICON/2,0xFFFF6B6B);}
            g.pose().popPose();
        }
        String foot=s.picked()>=0?(s.firstChoice()?Lang.tr("선택 완료"):Lang.tr("선택 완료 · 시간이 되면 변신")):Lang.tr("숫자 키 1~")+Math.min(n,9)+(shared?Lang.tr(" · 선착순 · 미선택 시 남은 블록 중 무작위"):Lang.tr(" · 고르지 않으면 무작위"));
        g.drawCenteredString(font,foot,g.guiWidth()/2,top+height+4,0xFFB0B0B0);
        g.pose().popPose();
    }
    private PropChoiceHud(){}
}
