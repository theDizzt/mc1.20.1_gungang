package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.Defibrillator;
import dev.gungang.acoustics.server.GungangNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 78 제세동기 while held (use key, or the off-hand use macro key): locks onto the on-screen teammate nearest the
 * crosshair within 32 blocks, marks them with a green "[+]" above the head, and tells the server whom it shows so the
 * release charges exactly the marked player.
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class DefibrillatorClient {
    private static LivingEntity target;
    private static int sent=-1;
    private static boolean aiming;
    /** The cone stays inside the view: half the vertical field of view, at most the lock-on angle. */
    private static double cone(Minecraft mc){return Math.min(Defibrillator.AIM_ANGLE,mc.options.fov().get()*.5);}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();var p=mc.player;
        boolean using=p!=null&&mc.level!=null&&p.isUsingItem()&&Defibrillator.is(p.getUseItem())&&!p.isSpectator();
        target=using?Defibrillator.target(p,cone(mc)):null;
        int id=target==null?-1:target.getId();
        // The pick goes out before the release packet on the same connection; the server forgets it on release.
        if(using&&(!aiming||id!=sent))send(id);
        aiming=using;if(!using)sent=-1;
    }
    private static void send(int id){
        if(Minecraft.getInstance().getConnection()==null)return;
        sent=id;GungangNetwork.CHANNEL.sendToServer(new GungangNetwork.Action("defibTarget",id<0?"":Integer.toString(id),""));
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS||target==null||target.isRemoved())return;
        var mc=Minecraft.getInstance();var camera=event.getCamera().getPosition();var pose=event.getPoseStack();
        var at=target.getPosition(event.getPartialTick());
        pose.pushPose();
        pose.translate(at.x-camera.x,at.y+target.getBbHeight()+.75-camera.y,at.z-camera.z);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        // Keep the marker readable at range: grow with distance (about the same size on screen past 8 blocks).
        float scale=.025f*(float)Math.max(1,Math.sqrt(at.distanceToSqr(camera))/8);
        pose.scale(-scale,-scale,scale);
        var buffers=mc.renderBuffers().bufferSource();String text="[+]";
        // Seen through walls so a partly covered teammate still shows; drawn twice for a solid bright look.
        mc.font.drawInBatch(text,-mc.font.width(text)/2f,0,0xFF33FF55,false,pose.last().pose(),buffers,Font.DisplayMode.SEE_THROUGH,0,15728880);
        mc.font.drawInBatch(text,-mc.font.width(text)/2f,0,0xFF33FF55,false,pose.last().pose(),buffers,Font.DisplayMode.NORMAL,0,15728880);
        buffers.endBatch();
        pose.popPose();
    }
    private DefibrillatorClient(){}
}
