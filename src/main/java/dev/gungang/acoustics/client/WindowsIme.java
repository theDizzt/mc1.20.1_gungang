package dev.gungang.acoustics.client;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;
import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFWNativeWin32;

/** Detach the window's IME only during gameplay; restore its original context for text screens.
 * https://learn.microsoft.com/windows/win32/api/imm/nf-imm-immassociatecontext
 */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class WindowsIme {
    public interface Imm32 extends StdCallLibrary {Pointer ImmAssociateContext(Pointer window,Pointer context);}
    private static Imm32 api;
    private static Pointer saved,window;
    private static boolean detached,failed;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.START||failed||!com.sun.jna.Platform.isWindows())return;
        var mc=Minecraft.getInstance();
        boolean gameplay=mc.player!=null&&mc.screen==null&&mc.isWindowActive();
        try {
            if(gameplay&&!detached){
                if(api==null)api=Native.load("imm32",Imm32.class);
                long handle=GLFWNativeWin32.glfwGetWin32Window(mc.getWindow().getWindow());
                if(handle==0)return;
                window=new Pointer(handle);saved=api.ImmAssociateContext(window,null);detached=true;
            } else if(!gameplay&&detached){
                api.ImmAssociateContext(window,saved);detached=false;saved=null;window=null;
            }
        } catch(LinkageError|RuntimeException e){
            failed=true;
            com.mojang.logging.LogUtils.getLogger().warn("[GunGang] Windows IME support unavailable",e);
        }
    }
    private WindowsIme(){}
}
