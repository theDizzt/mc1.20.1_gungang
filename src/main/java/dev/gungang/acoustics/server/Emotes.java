package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.network.PacketDistributor;

/**
 * Emotes on the number keys (1-0) in survival and adventure. A hider plays the disguise's emote (a kaomoji popup and an
 * effect on the block); everyone else plays a body animation. Slots without an emote yet (8, 9, 0) do nothing.
 */
public final class Emotes {
    /** Defined slots (1-based); 8-10 are reserved for later emotes. */
    public static final int SLOTS=7;
    /** Length of one emote in ticks; the client plays it for this long. */
    public static final int DURATION=60;
    private static final int COOLDOWN=20,RANGE=96;
    private static final Map<UUID,Integer> LAST=new HashMap<>();
    public static void request(ServerPlayer p,String value){
        int slot;try{slot=Integer.parseInt(value);}catch(NumberFormatException e){return;}
        if(slot<1||slot>SLOTS||!p.isAlive())return;
        var mode=p.gameMode.getGameModeForPlayer();if(mode!=GameType.SURVIVAL&&mode!=GameType.ADVENTURE)return;
        int now=p.server.getTickCount();if(now-LAST.getOrDefault(p.getUUID(),-1000)<COOLDOWN)return;LAST.put(p.getUUID(),now);
        var hunt=MatchManager.propHunt();var prop=hunt==null?null:hunt.prop(p.getUUID());
        net.minecraft.world.entity.Entity body=prop!=null&&!prop.isRemoved()?prop:p;
        var packet=new GungangNetwork.Emote(body.getId(),slot,body!=p);
        for(var viewer:p.serverLevel().players())
            if(viewer==p||viewer.distanceToSqr(body)<RANGE*RANGE)GungangNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->viewer),packet);
        if(hunt!=null&&hunt.participant(p.getUUID()))for(var bot:BotManager.entities()){
            var agent=BotManager.agent(bot);
            if(agent!=null)agent.propHunt.reactions.observe(bot,hunt,p,slot,now);
        }
    }
    /** Uses the same block animation/popup packet as a player's disguise, without broadcasting another social cue. */
    static void bot(BotEntity bot,int slot){
        var hunt=MatchManager.realPropHunt();var prop=bot.disguise();
        if(slot<1||slot>SLOTS||hunt==null||!hunt.hider(bot.getUUID())||!bot.isAlive()||prop==null||prop.isRemoved()||!hunt.hiding()&&!hunt.hunting())return;
        var packet=new GungangNetwork.Emote(prop.getId(),slot,true);
        for(var viewer:((net.minecraft.server.level.ServerLevel)bot.level()).players())
            if(viewer.distanceToSqr(prop)<RANGE*RANGE)GungangNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->viewer),packet);
    }
    public static void forget(UUID id){LAST.remove(id);}
    private Emotes(){}
}
