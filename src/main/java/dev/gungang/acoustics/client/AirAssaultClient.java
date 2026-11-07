package dev.gungang.acoustics.client;

import dev.gungang.acoustics.server.AirAssaultMotion;
import dev.gungang.acoustics.server.GungangNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public final class AirAssaultClient {
    public static void receive(GungangNetwork.AssaultMotion packet) {
        var player=Minecraft.getInstance().player;
        if(player!=null)AirAssaultMotion.receive(player,packet.active(),new Vec3(packet.x(),packet.y(),packet.z()));
    }
    private AirAssaultClient() { }
}
