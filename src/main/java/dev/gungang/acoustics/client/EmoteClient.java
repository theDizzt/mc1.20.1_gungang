package dev.gungang.acoustics.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.Emotes;
import dev.gungang.acoustics.server.GungangNetwork;
import dev.gungang.acoustics.server.PropEntity;
import java.util.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Emote keys 1-0 (survival and adventure only, where the hotbar is pinned) and their playback: players move their
 * arms and head; a disguise shows a kaomoji above the block with its own effect (hearts, a hop, "!", notes, shaking,
 * tears, flames). Slots 8-0 are bound but have no emote yet.
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class EmoteClient {
    public static final String CATEGORY="key.categories.gungang_emote";
    public static final KeyMapping[] KEYS=new KeyMapping[10];
    static{
        for(int i=0;i<KEYS.length;i++)
            KEYS[i]=new KeyMapping("key.gungang.emote."+(i+1),KeyConflictContext.IN_GAME,InputConstants.Type.KEYSYM,i==9?GLFW.GLFW_KEY_0:GLFW.GLFW_KEY_1+i,CATEGORY);
    }
    /** Hider kaomoji per slot (1-based index - 1). */
    static final String[] FACES={"(♡°▽°♡)","(๑>◡<๑)","Σ(°△°|||)︴","＼(≧▽≦)／","(⁄ ⁄>⁄ ▽ ⁄<⁄ ⁄)","(˚ ˃̣̣̥⌓˂̣̣̥ )","(╯°□°)╯︵ ┻━┻"};
    private record Play(int slot,boolean prop,long start){}
    private static final Map<Integer,Play> PLAYING=new HashMap<>();

    public static void receive(GungangNetwork.Emote packet){
        var level=Minecraft.getInstance().level;if(level==null||packet.slot()<1||packet.slot()>Emotes.SLOTS)return;
        PLAYING.put(packet.entity(),new Play(packet.slot(),packet.prop(),level.getGameTime()));
        var entity=level.getEntity(packet.entity());
        if(entity!=null&&packet.prop()&&packet.slot()==3)burst(entity,ParticleTypes.CRIT,10,.25);
    }
    private static Play play(Entity entity){
        var p=PLAYING.get(entity.getId());if(p==null)return null;
        return elapsed(p,0)>=Emotes.DURATION?null:p;
    }
    private static float elapsed(Play p,float partial){var l=Minecraft.getInstance().level;return l==null?Emotes.DURATION:l.getGameTime()-p.start+partial;}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        for(int i=0;i<KEYS.length;i++)while(KEYS[i].consumeClick())
            if(i<Emotes.SLOTS&&mc.screen==null&&HotbarLock.locked()&&LobbyClient.connected())LobbyClient.action("emote",String.valueOf(i+1),"");
        var level=mc.level;if(level==null){PLAYING.clear();return;}
        if(mc.isPaused())return;
        PLAYING.entrySet().removeIf(e->level.getGameTime()-e.getValue().start>=Emotes.DURATION||level.getEntity(e.getKey())==null);
        for(var e:PLAYING.entrySet()){
            var play=e.getValue();if(!play.prop)continue;
            var entity=level.getEntity(e.getKey());if(entity==null)continue;
            long t=level.getGameTime()-play.start;
            switch(play.slot){
                case 1->{if(t%6==0)burst(entity,ParticleTypes.HEART,1,.35);}
                case 4->{if(t%5==0)note(entity);}
                case 6->{if(t%2==0)burst(entity,ParticleTypes.FALLING_WATER,2,.3);if(t%8==0)burst(entity,ParticleTypes.SPLASH,3,.3);}
                case 7->{if(t%2==0)burst(entity,ParticleTypes.FLAME,2,.45);if(t%6==0)burst(entity,ParticleTypes.LARGE_SMOKE,1,.3);}
                default->{}
            }
        }
    }
    /** Particles around the top of an entity. */
    private static void burst(Entity entity,ParticleOptions type,int count,double spread){
        var level=entity.level();var r=level.random;
        for(int i=0;i<count;i++)level.addParticle(type,entity.getX()+(r.nextDouble()*2-1)*spread,entity.getY()+entity.getBbHeight()+.1+r.nextDouble()*.3,entity.getZ()+(r.nextDouble()*2-1)*spread,
                type==ParticleTypes.FLAME?(r.nextDouble()-.5)*.02:0,type==ParticleTypes.FLAME||type==ParticleTypes.LARGE_SMOKE?.03:0,type==ParticleTypes.FLAME?(r.nextDouble()-.5)*.02:0);
    }
    private static void note(Entity entity){
        var r=entity.level().random;
        // The note particle reads its colour from the x speed (0..1).
        entity.level().addParticle(ParticleTypes.NOTE,entity.getX()+(r.nextDouble()-.5)*.6,entity.getY()+entity.getBbHeight()+.3,entity.getZ()+(r.nextDouble()-.5)*.6,r.nextInt(25)/24d,0,0);
    }

    /** Extra render offset of a disguise: two hops (slot 2) or a shiver (slot 5). */
    public static Vec3 offset(PropEntity prop,float partial){
        var p=play(prop);if(p==null||!p.prop)return Vec3.ZERO;
        float t=elapsed(p,partial);
        if(p.slot==2&&t<24){float hop=(t%12)/12f;return new Vec3(0,4*hop*(1-hop)*.45,0);}
        if(p.slot==5){double a=.045*(1-t/Emotes.DURATION);return new Vec3(Math.sin(t*2.7)*a,0,Math.cos(t*3.3)*a);}
        return Vec3.ZERO;
    }
    /** The kaomoji (and the "!" of slot 3) above a disguise, facing the camera. */
    public static void popup(PropEntity prop,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        var p=play(prop);if(p==null||!p.prop)return;
        float t=elapsed(p,partial);var mc=Minecraft.getInstance();var font=mc.font;
        float rise=Math.min(1,t/6f);float alpha=t>Emotes.DURATION-10?(Emotes.DURATION-t)/10f:1;
        var lift=offset(prop,partial);
        pose.pushPose();
        pose.translate(lift.x,prop.getBbHeight()+.35+.2*rise+lift.y,lift.z);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        float scale=.025f*(.6f+.4f*rise);pose.scale(-scale,-scale,scale);
        int a=Mth.clamp((int)(alpha*255),8,255);
        text(font,FACES[p.slot-1],0,pose,buffers,light,(a<<24)|0xFFFFFF,(int)(a*.25f)<<24);
        if(p.slot==3){
            // A red "!" bobbing over the face.
            pose.pushPose();pose.translate(0,-12-Math.abs(Math.sin(t*.5))*3,0);pose.scale(2,2,2);
            text(font,"!",0,pose,buffers,light,(a<<24)|0xFF4040,0);
            pose.popPose();
        }
        pose.popPose();
    }
    private static void text(Font font,String text,float y,PoseStack pose,MultiBufferSource buffers,int light,int color,int background){
        font.drawInBatch(text,-font.width(text)/2f,y,color,false,pose.last().pose(),buffers,Font.DisplayMode.NORMAL,background,net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);
    }

    /** Arm and head poses of a player emote, applied after the vanilla animation. */
    public static void pose(HumanoidModel<?> model,LivingEntity entity){
        if(!(entity instanceof Player))return;
        var p=play(entity);if(p==null||p.prop)return;
        float t=elapsed(p,Minecraft.getInstance().getFrameTime());
        // Ease in over 4 ticks and out over the last 6, blending with the vanilla pose.
        float w=Math.min(1,Math.min(t/4f,(Emotes.DURATION-t)/6f));if(w<=0)return;
        var r=model.rightArm;var l=model.leftArm;var h=model.head;
        switch(p.slot){
            case 1->{ // 손 흔들기
                set(r,-2.8f,0,.25f+.45f*Mth.sin(t*.7f),w);
            }
            case 2->{ // 깔깔 웃기: belly held, head thrown back and shaking
                float shake=Mth.sin(t*1.9f)*.12f;
                set(r,-.55f+shake,.2f,-.35f,w);set(l,-.55f-shake,-.2f,.35f,w);
                h.xRot=Mth.lerp(w,h.xRot,-.45f+shake);
                model.body.xRot=Mth.lerp(w,model.body.xRot,-.1f+shake*.5f);
            }
            case 3->{ // 만세
                float pump=.2f*Mth.sin(t*.55f);
                set(r,-2.95f+pump,0,.3f,w);set(l,-2.95f+pump,0,-.3f,w);
                h.xRot=Mth.lerp(w,h.xRot,-.25f);
            }
            case 4->{ // 맙소사: hands on the head, head shaking slowly
                set(r,-2.75f,0,-.55f,w);set(l,-2.75f,0,.55f,w);
                h.xRot=Mth.lerp(w,h.xRot,-.2f);h.yRot+=w*.35f*Mth.sin(t*.35f);
            }
            case 5->{ // 눈 가리기
                set(r,-2.3f,0,-.45f,w);set(l,-2.3f,0,.45f,w);
                h.xRot=Mth.lerp(w,h.xRot,.15f);
            }
            case 6->{ // 머리 아파 하기: a hand on the forehead, the head drooping and swaying
                set(r,-2.45f,-.3f,-.55f,w);set(l,-.25f,0,-.15f,w);
                h.xRot=Mth.lerp(w,h.xRot,.35f);h.zRot=Mth.lerp(w,h.zRot,.18f*Mth.sin(t*.25f));
            }
            case 7->{ // 삿대질: right arm jabbing forward, left hand on the hip
                float jab=.25f*Math.max(0,Mth.sin(t*1.2f));
                set(r,-1.57f-jab+h.xRot,h.yRot,0,w);set(l,-.2f,0,-.75f,w);
            }
            default->{}
        }
        model.hat.copyFrom(h);
    }
    private static void set(net.minecraft.client.model.geom.ModelPart part,float x,float y,float z,float w){
        part.xRot=Mth.lerp(w,part.xRot,x);part.yRot=Mth.lerp(w,part.yRot,y);part.zRot=Mth.lerp(w,part.zRot,z);
    }
    private EmoteClient(){}
}
