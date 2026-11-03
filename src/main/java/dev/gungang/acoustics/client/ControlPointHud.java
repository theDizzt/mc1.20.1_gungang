package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.MatchSnapshot;
import dev.gungang.acoustics.server.TeamOptions;
import net.minecraft.client.gui.GuiGraphics;

/** Shared ribbon with a compact capture indicator or four Normandy objectives. */
final class ControlPointHud {
    static int draw(GuiGraphics g,MatchSnapshot state) {
        var point=state.point();int top=Math.max(2,BossBarHud.bottom+1);
        if(point==null)return top;
        float scale=MatchRibbonHud.scale(g);
        var options=state.options()==null?TeamOptions.defaults():state.options();
        int alpha=options.color("ALPHA"),bravo=options.color("BRAVO");
        g.pose().pushPose();g.pose().translate(g.guiWidth()/2f,top,0);g.pose().scale(scale,scale,1);
        MatchRibbonHud.frame(g);
        if("NORMANDY".equals(point.mode())) {
            int[] colors={0xFF2455FF,0xFF28D83B,0xFFFF8000,0xFFFF3333};
            for(int i=0;i<4;i++) {
                int start=i<2?-179+i*69:44+(i-2)*69,end=start+66;
                boolean complete=i<point.completed(),active=i==point.completed();
                float progress=complete?100:active?Math.abs(point.progress()):0;
                g.fill(start,6,end,11,0xB0141B23);
                g.fill(start,6,start+Math.round(66*Math.min(100,progress)/100),11,colors[i]);
                MatchRibbonHud.outlined(g,String.valueOf((char)('A'+i)),(start+end)/2,5,.8f,0xFFFFFFFF);
                int value=Math.round(progress);
                DeathmatchHud.score(g,2+i,value,String.valueOf(value),(start+end)/2f,24,1.8f,true);
            }
            DeathmatchHud.small(g,point.contested()?"경합 중":point.completed()>=4?"점령 완료":"거점 "+(char)('A'+point.completed()),0,24,.8f,0xFFFFFFFF,true);
            int tint=colors[Math.min(3,Math.max(0,point.completed()))];
            float progress=point.completed()>=4?100:Math.abs(point.progress());
            g.fill(-24,35,24,39,0x60000000);
            g.fill(-24,35,-24+Math.round(48*Math.min(100,progress)/100),39,tint);
        } else {
            MatchRibbonHud.wing(g,true,alpha,state.alphaScore(),point.goal(),"ALPHA","목표 "+point.goal());
            MatchRibbonHud.wing(g,false,bravo,state.bravoScore(),point.goal(),"BRAVO","목표 "+point.goal());
            int tint=point.progress()>0?alpha:point.progress()<0?bravo:0xFFE2E7EF;
            String status=point.contested()?"경합 중":point.owner()>0?"ALPHA":point.owner()<0?"BRAVO":"중립";
            DeathmatchHud.small(g,status+" · "+Math.round(Math.abs(point.progress()))+"%",0,24,.8f,point.contested()?0xFFFFFFFF:tint,true);
            g.fill(-24,35,24,39,0x60000000);
            int fill=Math.round(24*Math.max(0,Math.min(1,Math.abs(point.progress())/100f)));
            g.fill(point.progress()>0?-fill:0,35,point.progress()>0?0:fill,39,tint|0xFF000000);
        }
        MatchTimerHud.drawAt(g,state,0,3);
        g.pose().popPose();return top+Math.round(44*scale);
    }
    private ControlPointHud(){}
}
