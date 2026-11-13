package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.GungangNetwork;
import dev.gungang.acoustics.server.WindBlower;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The 35 classic blower's right click (TaCZ's aim key) fires the air cannon instead of aiming
 * ({@link dev.gungang.acoustics.mixin.TaczAimInputMixin} keeps TaCZ from aiming with it).
 */
@Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID,value=Dist.CLIENT)
public final class WindBlowerInput {
    private static KeyMapping aimKey;
    private static boolean aimKeyMissing,previous;

    public static boolean holding(){var player=Minecraft.getInstance().player;return player!=null&&WindBlower.is(player.getMainHandItem());}
    /** 36 산성가스분사기: its right click pushes acid gas ({@code AcidSprayer.push}) instead of aiming. */
    public static boolean holdingAcid(){var player=Minecraft.getInstance().player;return player!=null&&dev.gungang.acoustics.server.AcidSprayer.is(player.getMainHandItem());}
    private static KeyMapping aimKey(){
        if(aimKey==null&&!aimKeyMissing){
            try{aimKey=(KeyMapping)Class.forName("com.tacz.guns.client.input.AimKey").getField("AIM_KEY").get(null);}
            catch(ReflectiveOperationException|LinkageError|ClassCastException missing){aimKeyMissing=true;}
        }
        return aimKey;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();var key=aimKey();
        boolean down=key!=null&&key.isDown();
        boolean edge=down&&!previous;previous=down;
        if(!edge||mc.player==null||mc.screen!=null||mc.getConnection()==null||mc.player.isSpectator())return;
        if(holding())GungangNetwork.CHANNEL.sendToServer(new GungangNetwork.Action("airCannon","",""));
        else if(holdingAcid())GungangNetwork.CHANNEL.sendToServer(new GungangNetwork.Action("acidPush","",""));
    }
    private WindBlowerInput(){}
}
