package dev.gungang.acoustics.client;
import dev.gungang.acoustics.server.M95;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
public final class M95Client {
    public static boolean key(int key,int scan,int action){
        var mc=Minecraft.getInstance();
        if(key!=GLFW.GLFW_KEY_T||mc.screen!=null||mc.player==null||!mc.player.isAlive()||mc.player.isSpectator()||!M95.is(mc.player.getMainHandItem()))return false;
        if(action==GLFW.GLFW_PRESS)LobbyClient.action("m95Mode","","");
        return true;
    }
    private M95Client(){}
}
