package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.core.*;
import net.minecraft.core.particles.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class JumpPadBlock extends Block {
    public static final BooleanProperty NORTH=BlockStateProperties.NORTH,EAST=BlockStateProperties.EAST,SOUTH=BlockStateProperties.SOUTH,WEST=BlockStateProperties.WEST;
    private static final VoxelShape SHAPE=Block.box(0,0,0,16,2,16);
    public final int height;
    public JumpPadBlock(int height){super(Properties.copy(Blocks.IRON_BLOCK).strength(2).noOcclusion().lightLevel(s->15));this.height=height;registerDefaultState(stateDefinition.any().setValue(NORTH,false).setValue(EAST,false).setValue(SOUTH,false).setValue(WEST,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(NORTH,EAST,SOUTH,WEST);}
    private static BooleanProperty property(Direction d){return switch(d){case NORTH->NORTH;case EAST->EAST;case SOUTH->SOUTH;default->WEST;};}
    @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return SHAPE;}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){var s=defaultBlockState();for(var d:Direction.Plane.HORIZONTAL)s=s.setValue(property(d),c.getLevel().getBlockState(c.getClickedPos().relative(d)).is(this));return s;}
    @Override public BlockState updateShape(BlockState s,Direction d,BlockState neighbor,LevelAccessor l,BlockPos p,BlockPos n){return d.getAxis().isHorizontal()?s.setValue(property(d),neighbor.is(this)):s;}
    @Override public void animateTick(BlockState s,Level l,BlockPos p,RandomSource r){
        l.addParticle(height==8?MotionParticles.BLUE.get():MotionParticles.LIME.get(),p.getX()+r.nextDouble(),p.getY()+.14,p.getZ()+r.nextDouble(),0,.04+r.nextDouble()*.035,0);
    }
    @SubscribeEvent public static void jump(LivingEvent.LivingJumpEvent event){
        var entity=event.getEntity();var pos=BlockPos.containing(entity.getX(),entity.getY()-.04,entity.getZ());
        if(!(entity.level().getBlockState(pos).getBlock() instanceof JumpPadBlock pad)||Math.abs(entity.getY()-(pos.getY()+.125))>.08)return;
        var motion=entity.getDeltaMovement();entity.setDeltaMovement(motion.x,MotionRules.jumpVelocity(pad.height),motion.z);
        entity.fallDistance=0;entity.getPersistentData().putBoolean("GunGangPadJump",true);
    }
    @SubscribeEvent public static void fall(LivingFallEvent event){
        if(event.getEntity().getPersistentData().getBoolean("GunGangPadJump")){event.setCanceled(true);event.getEntity().getPersistentData().remove("GunGangPadJump");}
    }
    @SubscribeEvent public static void landed(LivingEvent.LivingTickEvent event){
        var e=event.getEntity();if(e.getPersistentData().getBoolean("GunGangPadJump")&&(e.onGround()&&e.getDeltaMovement().y<=0||e.isInWater()||e.isFallFlying()))e.getPersistentData().remove("GunGangPadJump");
    }
}
