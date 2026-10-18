package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;

/** Opt-in actual Forge/world validation; all placed blocks are restored. */
public final class GadgetSmokeChecksEscort {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void run(MinecraftServer server)throws Exception {
        var level=server.overworld();var old=new LinkedHashMap<BlockPos,BlockState>();
        var positions=List.of(new BlockPos(400,240,400),new BlockPos(408,240,400),new BlockPos(416,242,404),new BlockPos(424,244,408));
        var blocks=List.of(EscortBlocks.START.get(),EscortBlocks.PATH.get(),EscortBlocks.CHECKPOINT.get(),EscortBlocks.END.get());
        var player=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);var original=player.position();EscortMatch match=null;
        try{
            for(int i=0;i<positions.size();i++){var p=positions.get(i);old.put(p,level.getBlockState(p));level.setBlock(p,blocks.get(i).defaultBlockState(),3);}
            var rules=new EscortRules(new ArenaMap.Area(399,239,399,425,245,409),null,null,null,null,null,null,null,null,null,null,null,null,null);
            var route=EscortRoute.scan(level,rules);check(route.checkpoints.size()==1&&route.length>24,"Curve/slope route and checkpoint");
            check(route.at(0).distanceTo(new Vec3(400.5,240.05,400.5))<1e-5,"Start is marker center");
            check(route.at(route.length).distanceTo(new Vec3(424.5,244.05,408.5))<1e-5,"End is marker center");
            var json=ServerFiles.GSON.toJsonTree(ArenaMap.example()).getAsJsonObject();json.remove("capture");json.add("escort",ServerFiles.GSON.toJsonTree(rules));json.remove("timeLimitSeconds");
            var map=ServerFiles.GSON.fromJson(json,ArenaMap.class).validate();check(map.timeLimitSeconds()==240&&!DeathmatchRules.supported(map),"Escort defaults and mode exclusivity");
            match=new EscortMatch(server,map,route);check(match.cart.canBeCollidedWith(),"Cart supports collision");
            check(Math.abs(match.cart.getBoundingBox().getYsize()-2.5)<1e-5,"Cart deck collision height");
            for(var p:positions)check(level.getBlockState(p).getValue(EscortBlocks.Marker.HIDDEN),"Markers hidden during play");
            var rails=new ArrayList<EscortRail>();for(var e:level.getAllEntities())if(e instanceof EscortRail r&&!e.isRemoved())rails.add(r);
            check(!rails.isEmpty(),"Persistent rail geometry spawned");
            var tag=new net.minecraft.nbt.CompoundTag();rails.get(0).save(tag);check(tag.getFloat("Length")>0,"Track segment persistence");
            // Exercise entity collision support with a real FakePlayer in the server entity lookup.
            player.setPos(match.cart.getX(),match.cart.getBoundingBox().maxY+.1,match.cart.getZ());player.setDeltaMovement(Vec3.ZERO);
            level.addNewPlayer(player);player.move(net.minecraft.world.entity.MoverType.SELF,new Vec3(0,-.3,0));
            check(Math.abs(player.getY()-match.cart.getBoundingBox().maxY)<1e-5,"Player lands on cart deck");
            var before=player.position();match.cart.advance(route.at(.1),route.direction(.1));
            check(player.position().distanceTo(before)>.05,"Standing player follows cart motion");
            var frozen=match.cart.position();match.tick(false);check(match.cart.position().equals(frozen),"Preparation does not move cart");
            match.close();match=null;for(var p:positions)check(!level.getBlockState(p).getValue(EscortBlocks.Marker.HIDDEN),"Marker visibility restored");
        }finally{
            if(match!=null)match.close();level.removePlayerImmediately(player,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);player.setPos(original);
            old.forEach((p,s)->level.setBlock(p,s,3));EscortTracks.get(level).rebuild(level,null);
        }
        System.out.println("GUNGANG_ESCORT_SMOKE_PASS route, rails, persistence, cart collision, standing passenger, cleanup");
    }
}
