package dev.gungang.acoustics.client;

public final class VoidAnchorClient {
    public static void receive(dev.gungang.acoustics.server.GungangNetwork.AnchorPull packet) {
        var p=net.minecraft.client.Minecraft.getInstance().player;
        if(p!=null)dev.gungang.acoustics.server.VoidAnchorPull.receive(p,packet.anchor(),packet.active(),new net.minecraft.world.phys.Vec3(packet.x(),packet.y(),packet.z()));
    }
    private VoidAnchorClient() { }
}
