package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.GungangNetwork;
import dev.gungang.acoustics.server.ShurikenItem;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 33 shuriken controls: the attack key throws, TaCZ's fire select key cycles Semi / Burst / Ricochet and TaCZ's reload
 * key reloads (TaCZ itself ignores both keys for a non-gun item). Jumping again in mid-air double-jumps while a
 * shuriken is carried; the jump is applied here, to the client-driven movement, and the server adds its effects.
 */
@Mod.EventBusSubscriber(modid=dev.gungang.acoustics.GungangAcoustics.ID,value=Dist.CLIENT)
public final class ShurikenInput {
    private static KeyMapping fireSelect,reload;
    private static boolean keysMissing,selectDown,reloadDown,jumpDown,airJumped;
    private static long lastThrow=Long.MIN_VALUE/2;

    private static KeyMapping key(String type,String field){
        try{return (KeyMapping)Class.forName("com.tacz.guns.client.input."+type).getField(field).get(null);}
        catch(ReflectiveOperationException|LinkageError|ClassCastException missing){return null;}
    }
    private static void keys(){
        if(keysMissing||fireSelect!=null&&reload!=null)return;
        fireSelect=key("FireSelectKey","FIRE_SELECT_KEY");reload=key("ReloadKey","RELOAD_KEY");
        // Without TaCZ the shuriken still throws and reloads on its own when empty; only mode switching is unavailable.
        if(fireSelect==null||reload==null)keysMissing=true;
    }
    private static void send(String action){GungangNetwork.CHANNEL.sendToServer(new GungangNetwork.Action(action,"",""));}

    @SubscribeEvent(priority=EventPriority.HIGH,receiveCanceled=true) public static void attack(InputEvent.InteractionKeyMappingTriggered event){
        var mc=Minecraft.getInstance();
        if(!event.isAttack()||mc.player==null||mc.level==null||mc.screen!=null||!ShurikenItem.is(mc.player.getMainHandItem()))return;
        event.setCanceled(true);event.setSwingHand(false);
        if(mc.player.isSpectator())return;
        // Click only as fast as the server throws, so every swing seen is a real throw.
        long now=mc.level.getGameTime();var stack=mc.player.getMainHandItem();
        if(now-lastThrow>=0&&now-lastThrow<ShurikenItem.throwTicks(stack))return;
        if(ShurikenItem.reloadEnd(stack)>0)return;
        lastThrow=now;
        send("shurikenThrow");
        if(ShurikenItem.ammo(stack)>0)mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();var player=mc.player;
        if(player==null||mc.getConnection()==null){selectDown=reloadDown=jumpDown=airJumped=false;return;}
        keys();
        boolean free=mc.screen==null&&!player.isSpectator();
        boolean holding=ShurikenItem.is(player.getMainHandItem());
        boolean select=fireSelect!=null&&fireSelect.isDown(),reloading=reload!=null&&reload.isDown();
        if(free&&holding&&select&&!selectDown)send("shurikenMode");
        if(free&&holding&&reloading&&!reloadDown)send("shurikenReload");
        selectDown=select;reloadDown=reloading;
        boolean jump=mc.options.keyJump.isDown();
        if(player.onGround()||player.isInWater()||player.onClimbable()||player.isPassenger())airJumped=false;
        else if(free&&jump&&!jumpDown&&!airJumped&&!player.getAbilities().flying&&!player.isFallFlying()&&ShurikenItem.carried(player)){
            airJumped=true;
            var motion=player.getDeltaMovement();
            double velocity=jumpVelocity(player);
            player.setDeltaMovement(motion.x,Math.max(motion.y,velocity),motion.z);
            player.fallDistance=0;
            send("shurikenJump");
        }
        jumpDown=jump;
    }
    private static double jumpVelocity(net.minecraft.world.entity.player.Player player){
        for(var stack:player.getInventory().items)if(ShurikenItem.is(stack))return ShurikenItem.jumpVelocity(stack);
        return ShurikenItem.jumpVelocity(player.getOffhandItem());
    }
    private ShurikenInput(){}
}
