package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.PropCatalog;
import dev.gungang.acoustics.server.PropHuntSnapshot;
import dev.gungang.acoustics.server.PropInteraction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** The hider's block choice: up to three cards with icon, key, name and tags, the pick outlined, and the time left. */
final class PropChoiceHud {
    private static final int CARD=74,GAP=6,ICON=32;
    static void draw(GuiGraphics g,PropHuntSnapshot s){
        var offers=s.offers();if(offers==null||offers.isEmpty())return;
        var mc=Minecraft.getInstance();var font=mc.font;
        int n=offers.size(),width=n*CARD+(n-1)*GAP,left=(g.guiWidth()-width)/2,top=Math.round(g.guiHeight()*.56f);
        String title=(s.firstChoice()?"변신할 블록 선택":"다음 블록 선택")+" · "+s.choiceSeconds()+"초";
        g.pose().pushPose();g.pose().translate(0,0,200);
        g.fill(left-6,top-16,left+width+6,top+ICON+44,0xB0101418);
        g.drawCenteredString(font,title,g.guiWidth()/2,top-12,0xFFFFFFFF);
        for(int i=0;i<n;i++){
            String type=offers.get(i),block=PropCatalog.display(type);
            int x=left+i*(CARD+GAP);boolean picked=i==s.picked();
            g.fill(x,top,x+CARD,top+ICON+28,picked?0x6070CDBF:0x40FFFFFF);
            g.renderOutline(x,top,CARD,ICON+28,picked?0xFF70CDBF:0x60FFFFFF);
            g.drawString(font,"["+(i+1)+"]",x+3,top+3,picked?0xFF70CDBF:0xFFFFD84A,true);
            PropAbilityHud.icon(g,block,PropCatalog.state(block),x+(CARD-ICON)/2,top+4,ICON);
            g.pose().pushPose();g.pose().translate(0,0,300);
            String name=font.plainSubstrByWidth(PropCatalog.name(block).getString(),CARD-4);
            g.drawCenteredString(font,name,x+CARD/2,top+ICON+6,0xFFE2E8F0);
            String tags=(PropInteraction.special(block)?"§e특수 ":"")+(type.startsWith("@")||type.startsWith("#")?"§d색상 ":"")+"§7["+PropCatalog.number(block)+"]";
            g.drawCenteredString(font,tags,x+CARD/2,top+ICON+17,0xFFFFFFFF);
            g.pose().popPose();
        }
        String foot=s.picked()>=0?(s.firstChoice()?"선택 완료":"선택 완료 · 시간이 되면 변신"):"숫자 키 1~"+n+" · 고르지 않으면 무작위";
        g.drawCenteredString(font,foot,g.guiWidth()/2,top+ICON+32,0xFFB0B0B0);
        g.pose().popPose();
    }
    private PropChoiceHud(){}
}
