package dev.gungang.acoustics.client;
import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class LobbyKeySmokeChecks {
    private static int stage,ticks;
    private static int connectingTicks;
    private static InputConstants.Key original;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void key(int key)throws Exception{
        var mc=Minecraft.getInstance();var method=net.minecraft.client.KeyboardHandler.class.getDeclaredMethod("keyPress",long.class,int.class,int.class,int.class,int.class);
        method.setAccessible(true);method.invoke(mc.keyboardHandler,mc.getWindow().getWindow(),key,0,GLFW.GLFW_PRESS,0);
        method.invoke(mc.keyboardHandler,mc.getWindow().getWindow(),key,0,GLFW.GLFW_RELEASE,0);
    }
    private static void bind(InputConstants.Key key){LobbyClient.LOBBY.setKey(key);net.minecraft.client.KeyMapping.resetMapping();}
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END||stage==9)return;var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;
        org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        try{
            if(stage==0){if(mc.screen==null||mc.getOverlay()!=null)return;
                if(!(mc.screen instanceof TitleScreen)){mc.setScreen(new TitleScreen());return;}
                original=LobbyClient.LOBBY.getKey();bind(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_L));
                ConnectScreen.startConnecting(mc.screen,mc,net.minecraft.client.multiplayer.resolver.ServerAddress.parseString("127.0.0.1:25569"),new net.minecraft.client.multiplayer.ServerData("lobby test","127.0.0.1:25569",false),false);stage=1;return;}
            if(mc.player==null||mc.getConnection()==null){
                if(++connectingTicks%100==0)System.out.println("LOBBY_TEST_CONNECT screen="+mc.screen);
                if(connectingTicks>800)throw new AssertionError("Connection timeout: "+mc.screen);
                return;
            }
            if(++ticks<(stage==1?120:25))return;ticks=0;
            if(stage==1){check(mc.screen instanceof LobbyScreen,"Server fixture did not open lobby");
                check(!LobbyClient.lobbyKey(GLFW.GLFW_KEY_L,0,GLFW.GLFW_PRESS),"Key intercepted inside a screen");mc.setScreen(null);
                // Stale/closed local snapshot must not prevent the authoritative server from reopening its lobby.
                LobbyClient.receive(ServerFiles.GSON.toJson(new LobbySnapshot(false,false,false,mc.player.getStringUUID(),"","LOBBY",0,java.util.List.of(),java.util.List.of(),TeamOptions.defaults(),"DEATHMATCH")));
                key(GLFW.GLFW_KEY_L);stage=2;
            }else if(stage==2){check(mc.screen instanceof LobbyScreen,"Default L did not reopen lobby (or advancements stole it)");
                mc.setScreen(null);bind(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_K));check(!LobbyClient.lobbyKey(GLFW.GLFW_KEY_L,0,GLFW.GLFW_PRESS),"Old key remained bound");key(GLFW.GLFW_KEY_K);stage=3;
            }else if(stage==3){check(mc.screen instanceof LobbyScreen,"Rebound keyboard key failed");mc.setScreen(null);bind(InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_4));
                var method=net.minecraft.client.MouseHandler.class.getDeclaredMethod("onPress",long.class,int.class,int.class,int.class);method.setAccessible(true);
                method.invoke(mc.mouseHandler,mc.getWindow().getWindow(),GLFW.GLFW_MOUSE_BUTTON_4,GLFW.GLFW_PRESS,0);
                method.invoke(mc.mouseHandler,mc.getWindow().getWindow(),GLFW.GLFW_MOUSE_BUTTON_4,GLFW.GLFW_RELEASE,0);stage=4;
            }else if(stage==4){check(mc.screen instanceof LobbyScreen,"Rebound mouse button failed");mc.setScreen(null);bind(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_L));mc.getConnection().sendCommand("gungang lobby close");stage=5;
            }else if(stage==5){key(GLFW.GLFW_KEY_L);stage=6;
            }else {check(mc.screen==null,"Closed lobby key opened unrelated UI");bind(original);System.out.println("GUNGANG_LOBBY_KEY_PASS default_L stale_snapshot keyboard_rebind mouse_rebind screen_guard closed_lobby");stage=9;mc.stop();}
        }catch(Throwable e){e.printStackTrace();System.out.println("GUNGANG_LOBBY_KEY_FAIL");if(original!=null)bind(original);stage=9;mc.stop();}
    }
}
