package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class ShutterEntity extends BlockEntity {
    private BlockPos controller;
    private int bottom,top;
    private double startLift;
    private long startTime,refreshedAt=Long.MIN_VALUE,lastTick=Long.MIN_VALUE;
    private boolean powered,initialized,wasMoving;
    private List<BlockPos> members;
    public ShutterEntity(BlockPos p,BlockState s){super(ShutterBlocks.ENTITY.get(),p,s);controller=p;bottom=top=p.getY();}
    public double lift(float partial){return level==null?startLift:ShutterMotion.lift(startLift,level.getGameTime()-startTime+partial,powered,top-bottom+1);}
    public double localBottom(float partial){return ShutterMotion.bottom(bottom,worldPosition.getY(),lift(partial));}
    public boolean powered(){return powered;}
    void resetClosed(){
        startLift=0;startTime=level.getGameTime();powered=false;wasMoving=false;
        initialized=false;members=null;refreshedAt=lastTick=Long.MIN_VALUE;
        controller=worldPosition;bottom=top=worldPosition.getY();sync();
        level.scheduleTick(worldPosition,getBlockState().getBlock(),1);
    }
    public int bottomY(){return bottom;}
    public boolean moving(){double v=lift(0);return powered?v<top-bottom+1:v>0;}
    @Override public void onLoad(){super.onLoad();if(level!=null&&!level.isClientSide)level.scheduleTick(worldPosition,getBlockState().getBlock(),1);}
    @Override protected void saveAdditional(CompoundTag tag){
        super.saveAdditional(tag);tag.putLong("Controller",controller.asLong());tag.putInt("Bottom",bottom);tag.putInt("Top",top);
        // Save the current position and reference time, so restarting does not restart the animation.
        tag.putDouble("Lift",lift(0));tag.putLong("Time",level==null?startTime:level.getGameTime());tag.putBoolean("Powered",powered);tag.putBoolean("Initialized",initialized);
    }
    @Override public void load(CompoundTag tag){
        super.load(tag);controller=tag.contains("Controller")?BlockPos.of(tag.getLong("Controller")):worldPosition;
        bottom=tag.contains("Bottom")?tag.getInt("Bottom"):worldPosition.getY();top=Math.max(bottom,tag.contains("Top")?tag.getInt("Top"):bottom);
        startLift=tag.getDouble("Lift");if(!Double.isFinite(startLift))startLift=0;startLift=Math.max(0,Math.min(top-bottom+1,startLift));
        startTime=tag.getLong("Time");powered=tag.getBoolean("Powered");initialized=tag.getBoolean("Initialized");members=null;
    }
    @Override public CompoundTag getUpdateTag(){return saveWithoutMetadata();}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    private void sync(){setChanged();level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}
    private static boolean signal(ServerLevel level,List<BlockPos> group){return group.stream().anyMatch(level::hasNeighborSignal);}
    /** Rebuild once per group per tick; never force-load a chunk. Changes to size, holes and chunk loads are supported. */
    public static void refresh(ServerLevel level,BlockPos origin){
        if(!(level.getBlockEntity(origin) instanceof ShutterEntity first)||first.refreshedAt==level.getGameTime())return;
        var group=new ArrayList<BlockPos>();var seen=new HashSet<BlockPos>();var queue=new ArrayDeque<BlockPos>();queue.add(origin);seen.add(origin);
        while(!queue.isEmpty()){
            var p=queue.removeFirst();if(!(level.getBlockEntity(p) instanceof ShutterEntity))continue;group.add(p);
            var state=level.getBlockState(p);
            for(var d:ShutterBlock.connections(state)){var next=p.relative(d);if(level.hasChunkAt(next)&&ShutterBlock.joined(level,p,state,d)&&seen.add(next))queue.add(next);}
        }
        if(group.isEmpty())return;
        group.sort(Comparator.<BlockPos>comparingInt(p->p.getY()).thenComparingInt(p->p.getX()).thenComparingInt(p->p.getZ()));
        var root=group.get(0);int min=root.getY(),max=group.stream().mapToInt(BlockPos::getY).max().orElse(min);
        var existing=group.stream().map(level::getBlockEntity).map(e->(ShutterEntity)e).filter(e->e.initialized).findFirst().orElse(first);
        double lift=Math.max(0,Math.min(max-min+1,existing.bottom+existing.lift(0)-min));
        boolean power=signal(level,group),changed=power!=existing.powered;
        for(var p:group){
            var be=(ShutterEntity)level.getBlockEntity(p);be.startLift=lift;be.startTime=level.getGameTime();be.powered=power;
            be.controller=root;be.bottom=min;be.top=max;be.initialized=true;be.refreshedAt=level.getGameTime();be.members=p.equals(root)?List.copyOf(group):null;be.sync();
        }
        var lead=(ShutterEntity)level.getBlockEntity(root);lead.wasMoving=lead.moving();
        if(changed&&lead.wasMoving)lead.sound(power?SoundEvents.IRON_DOOR_OPEN:SoundEvents.IRON_DOOR_CLOSE,.7f,.75f);
    }
    private void sound(SoundEvent sound,float volume,float pitch){
        double x=worldPosition.getX()+.5,z=worldPosition.getZ()+.5;
        if(members!=null){x=members.stream().mapToInt(BlockPos::getX).average().orElse(x-.5)+.5;z=members.stream().mapToInt(BlockPos::getZ).average().orElse(z-.5)+.5;}
        level.playSound(null,x,(bottom+top+1)/2d,z,sound,SoundSource.BLOCKS,volume,pitch);
    }
    public static void tick(Level level,BlockPos pos,BlockState state,ShutterEntity be){
        if(!(level instanceof ServerLevel server))return;
        if(!be.initialized||!level.hasChunkAt(be.controller)||!(level.getBlockEntity(be.controller) instanceof ShutterEntity))refresh(server,pos);
        if(!pos.equals(be.controller))return;
        if(be.members==null)refresh(server,pos);
        if(be.members==null||be.lastTick==level.getGameTime())return;be.lastTick=level.getGameTime();
        if(level.getGameTime()%10==0&&signal(server,be.members)!=be.powered)refresh(server,pos);
        boolean moving=be.moving();
        if(moving&&level.getGameTime()%10==0)be.sound(be.powered?SoundEvents.PISTON_EXTEND:SoundEvents.PISTON_CONTRACT,.35f,.65f);
        if(!moving&&be.wasMoving){
            be.sound(SoundEvents.IRON_TRAPDOOR_CLOSE,.65f,.75f);
            for(var p:be.members)if(level.getBlockEntity(p) instanceof ShutterEntity tile)tile.sync();
        }
        be.wasMoving=moving;
    }
}
