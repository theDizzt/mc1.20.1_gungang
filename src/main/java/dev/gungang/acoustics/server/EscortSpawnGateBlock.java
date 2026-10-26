package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/**
 * Escort-only team spawn gate. Each gate belongs to one respawn stage: 0 is the opening spawn, N the spawn the escort
 * rules switch to once checkpoint N is cleared. Only during an escort match, and only while its team spawns at that
 * stage, is it ACTIVE and works exactly like a team spawn gate. Gates of the other stages display red crosses and stay LOCKED,
 * keeping every player out of spawns no longer in use. Outside escort matches a gate is invisible and does nothing.
 * Creative editors holding one see inactive gates as markers and right-click a gate to cycle its stage.
 */
public final class EscortSpawnGateBlock extends SpawnGateBlock {
    public static final BooleanProperty ACTIVE=BooleanProperty.create("active");
    /** During an escort match an inactive gate is a red-cross wall no player may pass; bullets still fly through. */
    public static final BooleanProperty LOCKED=BooleanProperty.create("locked");
    public static final IntegerProperty STAGE=IntegerProperty.create("stage",0,3);
    /** Client: the local player is a creative editor holding an escort gate; set by ClientSetup. */
    public static java.util.function.BooleanSupplier clientEditing=()->false;
    public EscortSpawnGateBlock(String team){
        super(team,properties().lightLevel(s->s.getValue(ACTIVE)?8:0));
        registerDefaultState(stateDefinition.any().setValue(ACTIVE,false).setValue(LOCKED,false).setValue(STAGE,0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(ACTIVE,LOCKED,STAGE);}
    @Override public net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state){return state.getValue(ACTIVE)||state.getValue(LOCKED)?net.minecraft.world.level.block.RenderShape.MODEL:net.minecraft.world.level.block.RenderShape.INVISIBLE;}
    @Override public boolean present(BlockState state){return state.getValue(ACTIVE);}
    @Override public VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){
        if(!present(state)&&state.getValue(LOCKED))
            return context instanceof EntityCollisionContext entity&&entity.getEntity() instanceof Player player&&!player.isSpectator()?Shapes.block():Shapes.empty();
        return super.getCollisionShape(state,level,pos,context);
    }
    public static boolean editing(Entity entity){
        return entity instanceof Player p&&p.isCreative()&&(gateItem(p.getMainHandItem())||gateItem(p.getOffhandItem()));
    }
    private static boolean gateItem(ItemStack stack){return stack.getItem() instanceof BlockItem item&&item.getBlock() instanceof EscortSpawnGateBlock;}
    static String label(int stage){return stage==0?"시작 스폰":"체크포인트 "+stage+" 통과 후 스폰";}
    private Component describe(int stage){return Component.literal(team+" 호위 스폰 홀로그램 · "+label(stage));}
    /** Inactive gates have no outline, except for an editor who must be able to select and break them. */
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){
        if(!present(state)&&context instanceof EntityCollisionContext entity&&editing(entity.getEntity()))return Shapes.block();
        return super.getShape(state,level,pos,context);
    }
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!editing(player)||present(state))return InteractionResult.PASS;
        if(level.isClientSide)return InteractionResult.SUCCESS;
        if(MatchManager.active()){player.displayClientMessage(Component.literal("경기 중에는 호위 스폰 단계를 바꿀 수 없습니다."),true);return InteractionResult.CONSUME;}
        int next=(state.getValue(STAGE)+1)%4;level.setBlock(pos,state.setValue(STAGE,next),3);
        player.displayClientMessage(describe(next),true);
        return InteractionResult.CONSUME;
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){
        if(!level.isClientSide&&placer instanceof Player player)player.displayClientMessage(Component.literal(describe(state.getValue(STAGE)).getString()+" · 우클릭으로 단계 변경"),true);
    }
    @Override public void animateTick(BlockState state,Level level,BlockPos pos,RandomSource random){
        if(!present(state)&&clientEditing.getAsBoolean())
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK_MARKER,state),pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,0,0,0);
    }
    @Override public void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving){
        if(level instanceof ServerLevel server&&old.getBlock()!=this)Index.get(server).change(pos,true);
    }
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
        if(level instanceof ServerLevel server&&next.getBlock()!=this)Index.get(server).change(pos,false);
        super.onRemove(state,level,pos,next,moving);
    }

    /**
     * Switches every indexed gate in a level: on when its stage is the one its team currently spawns at, locked for
     * every other stage. A null map (no escort match) turns every gate fully off.
     */
    public static void apply(ServerLevel level,Map<String,Integer> stages){
        var index=Index.get(level);
        for(var pos:List.copyOf(index.gates)){
            var state=level.getBlockState(pos);
            if(!(state.getBlock() instanceof EscortSpawnGateBlock gate)){index.change(pos,false);continue;}
            boolean on=stages!=null&&Objects.equals(stages.get(gate.team),state.getValue(STAGE)),locked=stages!=null&&!on;
            var next=state.setValue(ACTIVE,on).setValue(LOCKED,locked);
            if(next!=state)level.setBlock(pos,next,3);
        }
    }
    public static void deactivate(net.minecraft.server.MinecraftServer server){for(var level:server.getAllLevels())apply(level,null);}

    /** Persistent gate positions per level, so a match can switch gates without scanning the map. */
    static final class Index extends SavedData {
        final Set<BlockPos> gates=new HashSet<>();
        static Index get(ServerLevel level){return level.getDataStorage().computeIfAbsent(Index::load,Index::new,"gungang_escort_gates");}
        private static Index load(CompoundTag tag){var data=new Index();for(long p:tag.getLongArray("Gates"))data.gates.add(BlockPos.of(p));return data;}
        @Override public CompoundTag save(CompoundTag tag){tag.putLongArray("Gates",gates.stream().mapToLong(BlockPos::asLong).toArray());return tag;}
        void change(BlockPos pos,boolean add){if(add?gates.add(pos.immutable()):gates.remove(pos))setDirty();}
    }
}
