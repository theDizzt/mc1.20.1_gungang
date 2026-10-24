package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.*;
import net.minecraft.client.Minecraft;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class EscortHud {
    private static MatchSnapshot state;
    static void receive(MatchSnapshot value){state=value;}
    private static final int L=-116,R=116,TRACK=38,AMBER=0xFFFFD36A,LIGHT=0xFFE2E7EF;
    /** Deathmatch-style plates and LCD clock, with the route as a thin track underneath. */
    static int draw(GuiGraphics g,MatchSnapshot snapshot){
        var v=snapshot.escort();int top=Math.max(2,BossBarHud.bottom+1);
        float scale=Math.min(.8f,(g.guiWidth()-12)/236f);
        var options=snapshot.options()==null?TeamOptions.defaults():snapshot.options();
        int alpha=options.color("ALPHA")|0xFF000000,bravo=options.color("BRAVO")|0xFF000000;
        boolean finalLeg=v.completed()>=v.checkpoints().size();
        double next=finalLeg?v.length():v.checkpoints().get(v.completed());
        boolean pushing="FORWARD".equals(v.motion()),blocked="CONTESTED".equals(v.motion())||"REVERSE".equals(v.motion());
        g.pose().pushPose();g.pose().translate(g.guiWidth()/2f,top,0);g.pose().scale(scale,scale,1);
        team(g,true,"ALPHA","ATK",0xFFFF8A65,alpha,meters(next-v.distance()),finalLeg?"FINAL":"CP "+(v.completed()+1),pushing);
        team(g,false,"BRAVO","DEF",0xFF4D9FFF,bravo,meters(v.length()-v.distance()),"GOAL",blocked);
        if("PREPARING".equals(snapshot.phase())){
            DeathmatchHud.small(g,"PREPARING",0,2,.75f,AMBER,true);
            MatchTimerHud.drawAt(g,snapshot,0,11);
        }else if(v.overtimeTicks()>0){
            // Overtime replaces the clock: refills to 5.0 while attackers hold the cart.
            DeathmatchHud.small(g,"추가시간",0,2,.75f,AMBER,true);
            overtime(g,String.format(Locale.ROOT,"%.1f",v.overtimeTicks()/20.0),11,1.6f,v.overtimeTicks()<=40?0xFFFF6B6B:AMBER);
        }else MatchTimerHud.drawAt(g,snapshot,0,7);
        track(g,v,alpha,bravo,next);
        g.pose().popPose();
        return top+Math.round(56*scale);
    }
    private static String meters(double d){return Integer.toString((int)Math.ceil(Math.max(0,d)));}
    /** Overtime seconds centred under the label in the number font, shadow-free so its 1px counters stay open. */
    private static void overtime(GuiGraphics g,String text,int y,float size,int color){
        var font=Minecraft.getInstance().font;var number=HudText.number(text);float width=(font.width(number)-1)*size;
        g.pose().pushPose();g.pose().translate(-width/2,y,0);g.pose().scale(size,size,1);
        g.drawString(font,number,0,0,color,false);g.pose().popPose();
    }
    /** Same plate as the deathmatch strip; the big number is a distance instead of a score. */
    private static void team(GuiGraphics g,boolean left,String name,String tag,int tagColor,int color,String value,String target,boolean active){
        int inner=36,outer=116,y1=3,y2=27,x1=left?-outer:inner,x2=left?-inner:outer;
        DeathmatchHud.plate(g,x1,y1,x2,y2,color,left,active);
        var font=Minecraft.getInstance().font;var number=HudText.numbers(value);
        float size=Math.min(1.6f,34f/Math.max(1,font.width(number)));int width=Math.round(font.width(number)*size);
        // Number-font digits are 7 rows tall, so centre them on the plate like the deathmatch score.
        g.pose().pushPose();g.pose().translate(left?x2-8-width:x1+8,y1+12-3.5f*size,0);g.pose().scale(size,size,1);
        g.drawString(font,number,0,0,active?0xFFFFFFFF:0xFFC4CEDA,false);g.pose().popPose();
        int edge=left?x1+7:x2-7;
        DeathmatchHud.small(g,name,edge,y1+5,.75f,0xFFFFFFFF,false,left);
        int w=Math.round(font.width(tag)*.6f)+6,tx=left?edge:edge-w;
        g.fill(tx,y1+14,tx+w,y1+21,(tagColor&0xFFFFFF)|0x40000000);g.fill(tx,y1+14,tx+1,y1+21,tagColor);
        DeathmatchHud.small(g,tag,tx+3,y1+15,.6f,tagColor,false,true);
        DeathmatchHud.small(g,target,left?tx+w+3:tx-3,y1+15,.6f,DeathmatchHud.MUTED,false,left);
    }
    /** Route track: pushed section in ALPHA, current leg highlighted, diamond checkpoints, goal flag and a cart pointer with a status chip. */
    private static void track(GuiGraphics g,EscortMatch.View v,int alpha,int bravo,double next){
        int y=TRACK,cart=x(v,v.distance());
        g.fill(L-1,y-1,R+1,y+4,DeathmatchHud.EDGE);g.fill(L,y,R,y+3,0xFF2A3A50);
        g.fill(cart,y,x(v,next),y+3,0x40FFFFFF);
        g.fill(L,y,cart,y+3,alpha);
        for(int i=0;i<v.checkpoints().size();i++){
            int cx=x(v,v.checkpoints().get(i));
            diamond(g,cx,y+1,4,i<v.completed()?alpha:i==v.completed()?AMBER:LIGHT);
            if(i>=v.completed())diamond(g,cx,y+1,2,0xFF0B121C);
        }
        boolean finalLeg=v.completed()>=v.checkpoints().size();
        g.fill(R-1,y-8,R+1,y+4,LIGHT);g.fill(R-8,y-8,R-1,y-3,finalLeg?AMBER:alpha);
        String text;int color;
        switch(v.motion()){
            case "FORWARD"->{text=">> PUSH x"+String.format(Locale.ROOT,"%.2f",v.multiplier());color=alpha;}
            case "REVERSE"->{text="<< ROLLBACK";color=bravo;}
            case "CONTESTED"->{text="CONTESTED";color=AMBER;}
            case "CHECKPOINT"->{text="HOLD "+String.format(Locale.ROOT,"%.1f",v.stopTicks()/20.0)+"s";color=LIGHT;}
            default->{text="HALTED";color=DeathmatchHud.MUTED;}
        }
        // Down-pointing chevron over a white knob marks the cart.
        for(int i=0;i<4;i++){g.fill(cart-3+i+1,y-6+i+1,cart+4-i+1,y-5+i+1,0x80000000);g.fill(cart-3+i,y-6+i,cart+4-i,y-5+i,color);}
        g.fill(cart-2,y-1,cart+3,y+4,0xFFFFFFFF);
        var font=Minecraft.getInstance().font;int w=Math.round(font.width(text)*.7f)+9,cx=Math.max(L,Math.min(R-w,cart-w/2));
        g.fill(cx,y+7,cx+w,y+17,DeathmatchHud.SURFACE);g.fill(cx,y+7,cx+1,y+17,color);
        DeathmatchHud.small(g,text,cx+5,y+9,.7f,color,false,true);
    }
    private static int x(EscortMatch.View v,double d){return L+(int)Math.round((R-L)*Math.max(0,Math.min(1,d/v.length())));}
    private static void diamond(GuiGraphics g,int cx,int cy,int r,int color){
        for(int dy=-r;dy<=r;dy++){int w=r-Math.abs(dy);g.fill(cx-w,cy+dy,cx+w+1,cy+dy+1,color);}
    }
    @SubscribeEvent public static void beam(RenderLevelStageEvent event){
        var mc=Minecraft.getInstance();if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS||state==null||!state.active()||state.escort()==null||mc.level==null||!mc.level.dimension().location().toString().equals(state.dimension()))return;
        var v=state.escort();var pose=event.getPoseStack();var camera=event.getCamera().getPosition();pose.pushPose();pose.translate(v.x()-.5-camera.x,v.y()-camera.y,v.z()-.5-camera.z);
        float[] color=v.completed()<v.checkpoints().size()?new float[]{1f,.45f,.05f}:new float[]{.55f,1f,.15f};
        var buffer=mc.renderBuffers().bufferSource();net.minecraft.client.renderer.blockentity.BeaconRenderer.renderBeaconBeam(pose,buffer,new net.minecraft.resources.ResourceLocation("textures/entity/beacon_beam.png"),event.getPartialTick(),1f,mc.level.getGameTime(),0,Math.max(64,mc.level.getMaxBuildHeight()-(int)v.y()),color,.18f,.25f);
        pose.popPose();buffer.endBatch();
    }
    private EscortHud(){}
}
