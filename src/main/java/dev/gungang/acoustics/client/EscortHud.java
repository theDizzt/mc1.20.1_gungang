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
    private static final int L=-179,R=179,TRACK=7,AMBER=0xFFFFD36A,LIGHT=0xFFE2E7EF;
    /** One continuous route bar above the clock, using the ribbon width and white end caps. */
    static int draw(GuiGraphics g,MatchSnapshot snapshot){
        var v=snapshot.escort();int top=Math.max(8,BossBarHud.bottom+7);
        float scale=MatchRibbonHud.scale(g);
        var options=snapshot.options()==null?TeamOptions.defaults():snapshot.options();
        int alpha=options.color("ALPHA")|0xFF000000,bravo=options.color("BRAVO")|0xFF000000;
        double next=v.completed()>=v.checkpoints().size()?v.length():v.checkpoints().get(v.completed());
        g.pose().pushPose();g.pose().translate(g.guiWidth()/2f,top,0);g.pose().scale(scale,scale,1);
        g.fill(-180,3,180,4,LIGHT);g.fill(-180,6,-179,13,LIGHT);g.fill(179,6,180,13,LIGHT);
        track(g,v,alpha,bravo,next);
        TeamEmblems.draw(g,"ALPHA",-180,15,20);TeamEmblems.draw(g,"BRAVO",160,15,20);
        DeathmatchHud.small(g,"ALPHA · ATK",-156,19,.85f,alpha,false,true);
        DeathmatchHud.small(g,motion(v),-156,30,.8f,0xFFFFFFFF,false,true);
        distance(g,meters(next-v.distance())+"m",-72,23);
        DeathmatchHud.small(g,"BRAVO · DEF",156,19,.85f,bravo,false,false);
        DeathmatchHud.small(g,v.completed()>=v.checkpoints().size()?"최종 목적지":"체크포인트 "+(v.completed()+1)+" / "+v.checkpoints().size(),156,30,.8f,0xFFFFFFFF,false,false);
        distance(g,meters(v.length()-v.distance())+"m",72,23);
        if(v.overtimeTicks()>0){
            overtime(g,String.format(Locale.ROOT,"%.1f",v.overtimeTicks()/20.0),17,1.6f,v.overtimeTicks()<=40?0xFFFF6B6B:AMBER);
            DeathmatchHud.small(g,"추가시간",0,32,.8f,AMBER,true);
        }else {
            MatchTimerHud.drawAt(g,snapshot,0,16,true);
            if("PREPARING".equals(snapshot.phase()))DeathmatchHud.small(g,"준비 중",0,32,.8f,LIGHT,true);
        }
        g.pose().popPose();return top+Math.round(55*scale);
    }
    private static String motion(EscortMatch.View v){
        return switch(v.motion()){
            case "FORWARD" -> "전진 x"+String.format(Locale.ROOT,"%.2f",v.multiplier());
            case "REVERSE" -> "후퇴 중";
            case "CONTESTED" -> "경합 중";
            case "CHECKPOINT" -> "정차 "+String.format(Locale.ROOT,"%.1f",v.stopTicks()/20.0)+"s";
            default -> "정지";
        };
    }
    private static void distance(GuiGraphics g,String text,int cx,int cy){
        float size=Math.min(1.8f,56f/Math.max(1,HudText.numberWidth(text)));
        g.pose().pushPose();g.pose().translate(cx-HudText.numberWidth(text)*size/2,cy-3.5f*size,0);
        g.pose().scale(size,size,1);HudText.drawNumber(g,HudText.number(text),0,0,0xFFFFFFFF);
        g.pose().popPose();
    }
    private static String meters(double d){return Integer.toString((int)Math.ceil(Math.max(0,d)));}
    /** Overtime seconds centred under the label in the number font, with the black HUD number shadow. */
    private static void overtime(GuiGraphics g,String text,int y,float size,int color){
        var font=Minecraft.getInstance().font;var number=HudText.number(text);float width=(font.width(number)-1)*size;
        g.pose().pushPose();g.pose().translate(-width/2,y,0);g.pose().scale(size,size,1);
        HudText.drawNumber(g,number,0,0,color);g.pose().popPose();
    }
    /** Route track: pushed section in ALPHA, current leg highlighted, diamond checkpoints, goal flag and a cart pointer with a status chip. */
    private static void track(GuiGraphics g,EscortMatch.View v,int alpha,int bravo,double next){
        int y=TRACK,cart=x(v,v.distance());
        g.fill(L,y,R,y+4,0xB0141B23);
        g.fill(cart,y,x(v,next),y+4,0x40FFFFFF);
        g.fill(L,y,cart,y+4,alpha);
        for(int i=0;i<v.checkpoints().size();i++){
            int cx=x(v,v.checkpoints().get(i));
            diamond(g,cx,y+1,4,i<v.completed()?alpha:i==v.completed()?AMBER:LIGHT);
            if(i>=v.completed())diamond(g,cx,y+1,2,0xFF0B121C);
            MatchRibbonHud.outlined(g,String.valueOf(i+1),cx,-6,.8f,0xFFFFFFFF);
        }
        boolean finalLeg=v.completed()>=v.checkpoints().size();
        g.fill(R-1,y-8,R+1,y+4,LIGHT);
        for(int row=0;row<3;row++)for(int col=0;col<4;col++)
            g.fill(R-9+col*2,y-8+row*2,R-7+col*2,y-6+row*2,(row+col)%2==0?0xFFFFFFFF:0xFF000000);
        String text;int color;
        switch(v.motion()){
            case "FORWARD"->{text=">> PUSH x"+String.format(Locale.ROOT,"%.2f",v.multiplier());color=alpha;}
            case "REVERSE"->{text="<< ROLLBACK";color=bravo;}
            case "CONTESTED"->{text="CONTESTED";color=LIGHT;}
            case "CHECKPOINT"->{text="HOLD "+String.format(Locale.ROOT,"%.1f",v.stopTicks()/20.0)+"s";color=LIGHT;}
            default->{text="HALTED";color=DeathmatchHud.MUTED;}
        }
        // Down-pointing chevron over a white knob marks the cart.
        for(int i=0;i<4;i++){g.fill(cart-3+i+1,y-6+i+1,cart+4-i+1,y-5+i+1,0x80000000);g.fill(cart-3+i,y-6+i,cart+4-i,y-5+i,color);}
        g.fill(cart-2,y-1,cart+3,y+4,0xFFFFFFFF);

    }
    private static int x(EscortMatch.View v,double d){return L+(int)Math.round((R-L)*Math.max(0,Math.min(1,d/Math.max(.001,v.length()))));}
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
