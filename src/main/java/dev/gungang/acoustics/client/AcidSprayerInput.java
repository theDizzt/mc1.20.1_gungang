package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.AcidSprayer;
import dev.gungang.acoustics.server.GungangNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The sprayer owns its trigger instead of running the pack's delayed explosive shot. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class AcidSprayerInput {
    private static KeyMapping shootKey;
    private static boolean keyMissing,animationMissing;
    private static int cooldown,lastAnimation=-100,lastShot=-100;
    private static java.lang.reflect.Method display,animation,trigger,gunModel,node;
    private static java.lang.reflect.Field visible;

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.screen!=null||mc.getConnection()==null||!WindBlowerInput.holdingAcid()){
            cooldown=0;lastAnimation=-100;lastShot=-100;return;
        }
        if(shootKey==null&&!keyMissing){
            try{shootKey=(KeyMapping)Class.forName("com.tacz.guns.client.input.ShootKey").getField("SHOOT_KEY").get(null);}
            catch(ReflectiveOperationException|LinkageError missing){keyMissing=true;}
        }
        if(shootKey==null||!shootKey.isDown()){cooldown=0;return;}
        if(mc.player.isSpectator()||!mc.player.isAlive()||FreeForAllHud.blocked()
                ||dev.gungang.acoustics.server.SafeZones.contains(mc.player)||GadgetClient.placing())return;
        if(cooldown>0){cooldown--;return;}
        cooldown=AcidSprayer.SPRAY_INTERVAL-1;
        GungangNetwork.CHANNEL.sendToServer(new GungangNetwork.Action("acidSpray","",""));
    }

    /** Only accepted server shots animate; both buttons use the model's original shoot state. */
    public static void fired(boolean push){
        var mc=Minecraft.getInstance();
        if(mc.player==null||!WindBlowerInput.holdingAcid()||animationMissing)return;
        int now=mc.player.tickCount;
        boolean animate=push||now-lastShot>4||now-lastAnimation>=40;
        lastShot=now;
        if(!animate)return;
        try{
            if(display==null){
                display=Class.forName("com.tacz.guns.api.TimelessAPI").getMethod("getGunDisplay",ItemStack.class);
                animation=Class.forName("com.tacz.guns.client.resource.GunDisplayInstance").getMethod("getAnimationStateMachine");
                trigger=Class.forName("com.tacz.guns.api.client.animation.statemachine.AnimationStateMachine").getMethod("trigger",String.class);
                gunModel=Class.forName("com.tacz.guns.client.resource.GunDisplayInstance").getMethod("getGunModel");
                node=Class.forName("com.tacz.guns.client.model.bedrock.BedrockModel").getMethod("getNode",String.class);
                visible=Class.forName("com.tacz.guns.client.model.bedrock.BedrockPart").getField("visible");
            }
            var optional=(java.util.Optional<?>)display.invoke(null,mc.player.getMainHandItem());
            if(optional.isPresent()){
                // The displacer also animates an expanding emissive charge inside the model.
                // Hiding just the ordinary muzzle flash does not remove that geometry.
                var model=gunModel.invoke(optional.get());
                if(model!=null){
                    for(var name:new String[]{"lau_illuminated","inl_illuminated"}){
                        var part=node.invoke(model,name);
                        if(part!=null)visible.setBoolean(part,false);
                    }
                }
                var machine=animation.invoke(optional.get());
                if(machine!=null){trigger.invoke(machine,"shoot");lastAnimation=now;}
            }
        }catch(ReflectiveOperationException|RuntimeException|LinkageError missing){
            animationMissing=true;
            com.mojang.logging.LogUtils.getLogger().warn("GunGang: acid sprayer animation unavailable",missing);
        }
    }
    private AcidSprayerInput(){}
}
