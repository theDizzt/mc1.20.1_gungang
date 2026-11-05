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
            status(g,point.contested()?"경합 중":point.completed()>=4?"점령 완료":"거점 "+(char)('A'+point.completed()),point.contested()?0:point.completed()>=4?-1:point.progress());
            gauge(g,point.completed()>=4?-100:point.progress(),alpha,bravo); // BRAVO attacks: negative progress
        } else {
            MatchRibbonHud.wing(g,true,alpha,state.alphaScore(),point.goal(),"ALPHA","목표 "+point.goal());
            MatchRibbonHud.wing(g,false,bravo,state.bravoScore(),point.goal(),"BRAVO","목표 "+point.goal());
            String status=point.contested()?"경합 중":point.owner()>0?"ALPHA":point.owner()<0?"BRAVO":"중립";
            // Colour follows whoever is capturing; a settled point keeps its owner's colour.
            float side=point.contested()?0:point.progress()!=0?point.progress():point.owner();
            status(g,status+" · "+Math.round(Math.abs(point.progress()))+"%",side);
            gauge(g,point.progress(),alpha,bravo);
        }
        MatchTimerHud.drawAt(g,state,0,3);
        g.pose().popPose();return top+Math.round(44*scale);
    }
    /** ALPHA: yellow with a black outline. BRAVO: purple with a white outline. Neutral or contested: white on black. */
    static final int ALPHA_TEXT=0xFFFFD43B,BRAVO_TEXT=0xFFA35BFF;
    private static void status(GuiGraphics g,String text,float side){
        if(side>0)MatchRibbonHud.outlined(g,text,0,22,.85f,ALPHA_TEXT,0xFF000000);
        else if(side<0)MatchRibbonHud.outlined(g,text,0,22,.85f,BRAVO_TEXT,0xFFFFFFFF);
        else MatchRibbonHud.outlined(g,text,0,22,.85f,0xFFFFFFFF,0xFF000000);
    }
    private static final int HALF=30,GY=34,GH=5;
    private static final BarTween GAUGE=new BarTween(.45,.3);
    private static int gaugeSide;
    /**
     * Capture gauge growing out of the centre both ways in the capturing team's colour ({@code progress} is
     * -100..100, positive for ALPHA). It eases between values, flashes what changed, and while the capture
     * keeps moving a sheen runs from the centre outward.
     */
    private static void gauge(GuiGraphics g,float progress,int alpha,int bravo){
        int side=progress>0?1:progress<0?-1:0;
        // The other team taking over starts the bar again from the centre.
        if(side!=0&&side!=gaugeSide){gaugeSide=side;GAUGE.reset();GAUGE.update(0);}
        float shown=GAUGE.update(Math.max(0,Math.min(1,Math.abs(progress)/100f)));
        int color=(gaugeSide>=0?alpha:bravo)|0xFF000000;
        g.fill(-HALF-1+1,GY-1+1,HALF+1+1,GY+GH+1+1,MatchRibbonHud.SHADOW);
        g.fill(-HALF-1,GY-1,HALF+1,GY+GH+1,0xC0101418);
        g.fill(-HALF,GY,HALF,GY+GH,0x80303840);
        int half=Math.round(HALF*shown);
        if(half>0){
            g.fill(-half,GY,half,GY+GH,color);
            g.fill(-half,GY,half,GY+1,0x50FFFFFF);
            float flash=GAUGE.flash();
            if(flash>0){
                int lo=Math.round(HALF*GAUGE.flashLow()),hi=Math.round(HALF*GAUGE.flashHigh());
                int a=Math.round((GAUGE.rising()?170:120)*flash)<<24|0xFFFFFF;
                if(hi>lo){g.fill(-hi,GY,-lo,GY+GH,a);g.fill(lo,GY,hi,GY+GH,a);}
            }
            double since=GAUGE.since();
            if(since>=0&&since<1.2){
                // Sheen from the centre outward, repeating while progress keeps changing.
                float t=(float)((System.nanoTime()/1e9%.8)/.8);int pos=Math.round((half+6)*t),a=Math.max(0,pos-6),b=Math.min(half,pos);
                int alphaSheen=Math.round(120*(1-t)*(float)Math.min(1,(1.2-since)/.4));
                if(b>a&&alphaSheen>0){g.fill(-b,GY,-a,GY+GH,alphaSheen<<24|0xFFFFFF);g.fill(a,GY,b,GY+GH,alphaSheen<<24|0xFFFFFF);}
            }
            // Bright tips at the growing ends.
            g.fill(-half,GY,-half+1,GY+GH,0xFFDCE5EC);g.fill(half-1,GY,half,GY+GH,0xFFDCE5EC);
        }
        // Centre notch: where the bar starts.
        g.fill(0,GY-2,1,GY+GH+2,0xFFE0E5E9);
    }
    private ControlPointHud(){}
}
