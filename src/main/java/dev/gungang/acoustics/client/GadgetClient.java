package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.core.Direction;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class GadgetClient {
    private static String preview="";
    private static double range=5;
    private static boolean armed;
    private static String expected="";
    private static int previewSlot=-1;
    /** Only an H macro preview may keep going once the item is back in the offhand; a main-hand one ends when the item leaves that hand. */
    private static boolean previewOffhand;
    public static void expect(String kind) {
        var player=Minecraft.getInstance().player;
        if(player!=null){expected=kind;previewSlot=player.getInventory().selected;previewOffhand=OffhandMacro.executingClick();}
    }
    private static boolean valid(String kind) {
        var mc=Minecraft.getInstance();var player=mc.player;
        return player!=null&&player.isAlive()&&!player.isSpectator()&&mc.screen==null
                &&player.getInventory().selected==previewSlot
                &&(CombatGadgets.is(player.getMainHandItem(),kind)||previewOffhand&&CombatGadgets.is(player.getOffhandItem(),kind));
    }
    public static boolean placing() {return !preview.isEmpty()&&valid(preview);}
    public static void cancel() {
        preview="";expected="";armed=false;
        if(Minecraft.getInstance().getConnection()!=null)LobbyClient.action("cancelplace","","");
    }
    @SubscribeEvent public static void movement(MovementInputUpdateEvent e) {
        if(SurvivalGadgets.jetting(e.getEntity()))e.getInput().jumping=false;
        if(UtilityHud.rooted()) {
            var input=e.getInput();input.forwardImpulse=0;input.leftImpulse=0;input.jumping=false;
            input.up=false;input.down=false;input.left=false;input.right=false;
        }
    }
    public static void receive(String kind,double distance) {
        if(!kind.isEmpty()&&(!kind.equals(expected)||!valid(kind))){cancel();return;}
        preview=kind;range=distance;armed=false;if(kind.isEmpty())expected="";
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {preview="";expected="";previewSlot=-1;SurvivalGadgets.clearClient();}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        var mc=Minecraft.getInstance();
        if(!expected.isEmpty()&&!valid(expected))cancel();
        // A gun in the main hand can swallow the attack click before it reaches click() below.
        else if(placing()&&mc.options.keyAttack.isDown())cancel();
        if(e.phase==TickEvent.Phase.END&&!mc.options.keyUse.isDown()&&!OffhandMacro.USE.isDown())armed=true;
    }
    @SubscribeEvent public static void click(InputEvent.InteractionKeyMappingTriggered e) {
        if(SurvivalGadgets.jetting(Minecraft.getInstance().player)) {e.setCanceled(true);e.setSwingHand(false);return;}
        if(preview.isEmpty())return;
        if(!valid(preview)){cancel();return;}
        if(e.isAttack()||e.isUseItem()) {
            e.setCanceled(true);e.setSwingHand(false);
            if(e.isAttack()) {cancel();}
            else if(armed) {LobbyClient.action("place","","");armed=false;}
        }
    }
    @SubscribeEvent public static void hint(RenderGuiEvent.Post e) {
        if(preview.isEmpty())return;var mc=Minecraft.getInstance();if(mc.screen!=null||mc.options.hideGui||!HudVisibility.enabled())return;
        String text="우클릭 설치 · 좌클릭/보조 손 키 취소";var g=e.getGuiGraphics();
        g.drawString(mc.font,text,(g.guiWidth()-mc.font.width(text))/2,g.guiHeight()/2+25,0xFFFFFFFF,true);
    }
    @SubscribeEvent public static void world(RenderLevelStageEvent e) {
        if(preview.isEmpty()||e.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        var mc=Minecraft.getInstance();if(mc.player==null)return;
        var pos=GadgetManager.target(mc.player,range);if(pos==null)return;
        boolean wall=preview.equals("barrier"),east=mc.player.getDirection().getAxis()==Direction.Axis.X;
        boolean valid=GadgetManager.valid(mc.player,pos,wall,east);
        var pose=e.getPoseStack();var camera=e.getCamera().getPosition();
        pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        var buffers=mc.renderBuffers().bufferSource();
        LevelRenderer.renderLineBox(pose,buffers.getBuffer(RenderType.lines()),GadgetManager.bounds(pos,wall,east),valid?.25f:1,valid?1:.2f,.3f,1);
        buffers.endBatch(RenderType.lines());pose.popPose();
    }
    private GadgetClient() { }
}
