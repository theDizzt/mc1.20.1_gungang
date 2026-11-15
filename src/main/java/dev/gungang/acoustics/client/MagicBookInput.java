package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.GungangNetwork;
import dev.gungang.acoustics.server.MagicBookItem;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 39 magic book controls: the attack and use keys are reported to the server as held or not ({@code bookInput}, on
 * every change and twice a second while held); vanilla attacking and using are suppressed. TaCZ's fire select key and
 * the mouse wheel ({@link FireModeWheel}) change the element. While the server allows 흑 flight, the view direction
 * drives the body here (the client moves its own player).
 */
@Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID,value=Dist.CLIENT)
public final class MagicBookInput {
    private static final int REFRESH=10;
    private static KeyMapping fireSelect;
    private static boolean selectMissing,selectDown;
    private static String sent="00";
    private static int sinceSent;

    @SubscribeEvent(priority=EventPriority.HIGH,receiveCanceled=true) public static void keys(InputEvent.InteractionKeyMappingTriggered event){
        var mc=Minecraft.getInstance();
        if(mc.player==null||!MagicBookItem.is(mc.player.getMainHandItem())||!(event.isAttack()||event.isUseItem()))return;
        event.setCanceled(true);event.setSwingHand(false);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();var player=mc.player;
        if(player==null||mc.getConnection()==null){sent="00";selectDown=false;return;}
        boolean holding=MagicBookItem.is(player.getMainHandItem());
        boolean free=holding&&mc.screen==null&&!player.isSpectator()&&player.isAlive();
        String now=free?(mc.options.keyAttack.isDown()?"1":"0")+(mc.options.keyUse.isDown()?"1":"0"):"00";
        sinceSent++;
        if(!now.equals(sent)||!now.equals("00")&&sinceSent>=REFRESH){
            GungangNetwork.CHANNEL.sendToServer(new GungangNetwork.Action("bookInput",now,""));sent=now;sinceSent=0;
        }
        if(!selectMissing&&fireSelect==null){
            try{fireSelect=(KeyMapping)Class.forName("com.tacz.guns.client.input.FireSelectKey").getField("FIRE_SELECT_KEY").get(null);}
            catch(ReflectiveOperationException|LinkageError|ClassCastException missing){selectMissing=true;}
        }
        boolean select=fireSelect!=null&&fireSelect.isDown();
        if(free&&select&&!selectDown)GungangNetwork.CHANNEL.sendToServer(new GungangNetwork.Action("bookMode","",""));
        selectDown=select;
        // 흑 right click: fly where the view points while the server allows it (mana left, key held).
        var stack=player.getMainHandItem();
        if(free&&now.charAt(1)=='1'&&MagicBookItem.element(stack)==MagicBookItem.Element.BLACK&&MagicBookItem.flying(stack)){
            player.setDeltaMovement(player.getLookAngle().scale(MagicBookItem.flightSpeed(stack)));
            player.fallDistance=0;
        }
        if(holding&&free&&now.charAt(0)=='1'||holding&&free&&now.charAt(1)=='1'){
            // Arm raised for third-person viewers while casting.
            if(player.tickCount%8==0)player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        }
    }
    private MagicBookInput(){}
}
