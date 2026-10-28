package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;

public final class ConveyorWrench extends Item {
    public ConveyorWrench(){super(new Properties().stacksTo(1));}
    @Override public InteractionResult useOn(UseOnContext c){
        if(!(c.getLevel().getBlockEntity(c.getClickedPos()) instanceof ConveyorEntity belt))return InteractionResult.PASS;
        if(c.getPlayer() instanceof ServerPlayer p&&allowed(p,c.getClickedPos()))GungangNetwork.send(p,new GungangNetwork.ConveyorView(c.getClickedPos(),belt.speed));
        return InteractionResult.sidedSuccess(c.getLevel().isClientSide);
    }
    private static boolean allowed(ServerPlayer p,BlockPos pos){
        return p.isAlive()&&!p.isSpectator()&&p.mayBuild()&&p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64
                &&(p.getMainHandItem().getItem() instanceof ConveyorWrench||p.getOffhandItem().getItem() instanceof ConveyorWrench)
                &&p.level().hasChunkAt(pos)&&p.level().mayInteract(p,pos);
    }
    public static void apply(ServerPlayer player,BlockPos start,int speed,boolean reverse){
        if(player==null||!allowed(player,start)||speed<1||speed>30||!(player.level().getBlockState(start).getBlock() instanceof ConveyorBlock))return;
        var level=player.serverLevel();
        var states=new LinkedHashMap<BlockPos,BlockState>();var exits=new HashMap<BlockPos,Direction>();var queue=new ArrayDeque<BlockPos>();
        var initial=level.getBlockState(start);var exit=ConveyorBlock.output(initial);
        if(reverse){var ports=ConveyorBlock.ports(initial.getValue(ConveyorBlock.SHAPE));exit=ports[0]==exit?ports[1]:ports[0];}
        queue.add(start);exits.put(start,exit);
        while(!queue.isEmpty()){
            var pos=queue.remove();var state=level.getBlockState(pos);states.put(pos,state);
            if(states.size()>4096){message(player,"gungang.conveyor.too_large");return;}
            if(!level.mayInteract(player,pos)){message(player,"gungang.conveyor.protected");return;}
            for(var d:ConveyorBlock.ports(state.getValue(ConveyorBlock.SHAPE))){
                var next=pos.relative(d);
                if(!level.hasChunkAt(next)){message(player,"gungang.conveyor.unloaded");return;}
                var neighbor=level.getBlockState(next);
                if(!ConveyorBlock.sameHeight(state,neighbor)||!ConveyorBlock.hasPort(neighbor,d.getOpposite()))continue;
                var pair=ConveyorBlock.ports(neighbor.getValue(ConveyorBlock.SHAPE));
                Direction nextExit=exits.get(pos)==d?(pair[0]==d.getOpposite()?pair[1]:pair[0]):d.getOpposite();
                if(exits.containsKey(next)){if(exits.get(next)!=nextExit){message(player,"gungang.conveyor.invalid");return;}continue;}
                exits.put(next,nextExit);queue.add(next);
            }
        }
        for(var entry:states.entrySet()){
            var pos=entry.getKey();var state=entry.getValue();
            level.setBlock(pos,state.setValue(ConveyorBlock.REVERSED,ConveyorBlock.ports(state.getValue(ConveyorBlock.SHAPE))[0]==exits.get(pos)),2);
            if(level.getBlockEntity(pos) instanceof ConveyorEntity belt){belt.speed=speed;belt.initialized=true;belt.setChanged();belt.sync();}
        }
        player.displayClientMessage(Component.translatable("gungang.conveyor.applied",states.size(),String.format(Locale.ROOT,"%.1f",speed/10d)),true);
    }
    private static void message(ServerPlayer p,String key){p.displayClientMessage(Component.translatable(key),false);}
}
