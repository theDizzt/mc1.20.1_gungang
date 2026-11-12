package dev.gungang.acoustics.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * {@code gungang:barrier_plank} (방벽 판자): the 방벽 secondary's boards as a map block. One block wide and tall, as thick
 * as the deployed barrier wall (0.36 blocks), standing along {@link #AXIS}. Neighbouring planks on the same axis form one
 * wall: the boards continue across them and a single no-entry sign is drawn as large as the wall allows, centred
 * ({@code BarrierPlankRenderer}). It is a {@link GimmickBlock} wall: bullets, melee, projectiles and explosions wear its
 * health down (block setting barrier_plank), a broken block leaves a hole while the rest of the wall keeps its look,
 * and it is restored when the map restarts.
 */
public final class BarrierPlankBlock extends GimmickBlock {
    public static final String ID = "barrier_plank";
    /** X: the wall runs east-west (thin along Z); Z: it runs north-south. */
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    /** Thickness of the deployed barrier wall (DeployableRenderer HALF 0.18 × 2), in blocks. */
    public static final double THICKNESS = .36;
    private static final double LOW = (1 - THICKNESS) / 2 * 16, HIGH = 16 - LOW;
    private static final VoxelShape ALONG_X = box(0, 0, LOW, 16, 16, HIGH), ALONG_Z = box(LOW, 0, 0, HIGH, 16, 16);

    public BarrierPlankBlock() {
        super(ID);
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.X));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { super.createBlockStateDefinition(b); b.add(AXIS); }
    @Override public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        if (!s.getValue(ACTIVE)) return Shapes.empty();
        return s.getValue(AXIS) == Direction.Axis.X ? ALONG_X : ALONG_Z;
    }
    /** Drawn by the block entity renderer (it needs the whole wall's layout), never by a baked model. */
    @Override public RenderShape getRenderShape(BlockState s) { return s.getValue(ACTIVE) ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.INVISIBLE; }
    /**
     * Facing the wall's face: it runs across the view. Placed against the side of another plank (or above/below one),
     * it continues that wall's direction instead.
     */
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) {
        var against = c.getLevel().getBlockState(c.getClickedPos().relative(c.getClickedFace().getOpposite()));
        Direction.Axis axis;
        // Clicking a plank's end or top/bottom continues it; clicking its broad face starts a wall facing the player.
        if (against.getBlock() instanceof BarrierPlankBlock && c.getClickedFace().getAxis() != across(against.getValue(AXIS))) axis = against.getValue(AXIS);
        else axis = c.getHorizontalDirection().getClockWise().getAxis();
        return defaultBlockState().setValue(AXIS, axis);
    }
    /** The horizontal axis through the wall's broad faces. */
    static Direction.Axis across(Direction.Axis axis) { return axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X; }
    /** The neighbouring cell along the wall in its +along direction (east for X, north for Z: see {@link #start}). */
    public static Direction along(Direction.Axis axis) { return axis == Direction.Axis.X ? Direction.EAST : Direction.NORTH; }
    /**
     * Where the block starts along its wall, in blocks, increasing in {@link #along} direction: x for an east-west wall,
     * -z-1 for a north-south one (so the renderer can draw both from the same layout after a 90° turn).
     */
    public static int start(Direction.Axis axis, BlockPos pos) { return axis == Direction.Axis.X ? pos.getX() : -pos.getZ() - 1; }
}
