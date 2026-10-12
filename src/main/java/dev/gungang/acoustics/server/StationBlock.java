package dev.gungang.acoustics.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

public class StationBlock extends BaseEntityBlock {
    public enum Kind { WAR_TABLE,ARMORY,ALPHA,BRAVO,SMALL,LARGE }
    public static final BooleanProperty COOLING=BooleanProperty.create("cooling");
    public final Kind kind;
    public StationBlock(Kind kind) {
        super(Properties.copy(Blocks.IRON_BLOCK).strength(3.5f).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK).lightLevel(s->kind==Kind.SMALL||kind==Kind.LARGE?15:0));
        this.kind=kind;registerDefaultState(stateDefinition.any().setValue(COOLING,false));
    }
    public boolean healthPack(){return kind==Kind.SMALL||kind==Kind.LARGE;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(COOLING);}
    @Override public RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new StationEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide?null:createTickerHelper(type,StationBlocks.ENTITY.get(),StationEntity::tick);}
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(player instanceof ServerPlayer p&&interactionEntity(level,pos,state) instanceof StationEntity station)station.interact(p,false);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void stepOn(Level level,BlockPos pos,BlockState state,Entity entity){
        if(entity instanceof ServerPlayer p&&interactionEntity(level,pos,state) instanceof StationEntity station)station.interact(p,true);
        super.stepOn(level,pos,state,entity);
    }
    public BlockEntity interactionEntity(Level level,BlockPos pos,BlockState state){return level.getBlockEntity(pos);}
}
