package dev.gungang.acoustics.client;

import dev.gungang.acoustics.GungangAcoustics;
import dev.gungang.acoustics.server.GungangNetwork;
import dev.gungang.acoustics.server.SecretBlocks;
import it.unimi.dsi.fastutil.longs.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client copy of the current dimension's secret-wand marks; changed blocks and their neighbours are re-meshed. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID,value=Dist.CLIENT)
public final class SecretBlocksClient {
    public static void receive(GungangNetwork.SecretBlockSync p){
        var old=SecretBlocks.client();var next=p.full()?new Long2ByteOpenHashMap():new Long2ByteOpenHashMap(old);
        var touched=new LongOpenHashSet(p.positions());
        if(p.full())touched.addAll(old.keySet());
        for(int i=0;i<p.positions().length;i++){if(p.modes()[i]==0)next.remove(p.positions()[i]);else next.put(p.positions()[i],p.modes()[i]);}
        SecretBlocks.client(next);
        if(Minecraft.getInstance().level==null)return;
        var renderer=Minecraft.getInstance().levelRenderer;
        for(long key:touched){int x=BlockPos.getX(key),y=BlockPos.getY(key),z=BlockPos.getZ(key);renderer.setBlocksDirty(x-1,y-1,z-1,x+1,y+1,z+1);}
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){SecretBlocks.client(Long2ByteMaps.EMPTY_MAP);}
    private SecretBlocksClient(){}
}
