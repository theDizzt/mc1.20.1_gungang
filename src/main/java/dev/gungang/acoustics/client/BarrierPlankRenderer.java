package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.gungang.acoustics.server.BarrierPlankBlock;
import dev.gungang.acoustics.server.GimmickBlock;
import dev.gungang.acoustics.server.GimmickEntity;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * {@link BarrierPlankBlock} walls. Planks touching along the wall's direction or above/below (same axis) form one wall,
 * broken ones included: the boards are laid out from the wall's top-left corner (repeating every 3 blocks across and
 * down), and one no-entry sign, as large as the wall's shorter side, is centred on its bounding rectangle. Each intact
 * block draws only its own share, so a broken block leaves a hole and nothing else moves. Every other gimmick block
 * entity is skipped.
 */
public final class BarrierPlankRenderer implements BlockEntityRenderer<GimmickEntity> {
    private static final ResourceLocation BOARDS = new ResourceLocation("gungang", "textures/entity/barrier_plank_boards.png");
    private static final ResourceLocation SIGN = new ResourceLocation("gungang", "textures/entity/barrier_sign.png");
    /** Blocks one texture spans, half the wall's thickness, and how far the sign stands off each face. */
    private static final float PERIOD = 3, HALF = (float) BarrierPlankBlock.THICKNESS / 2, SIGN_OFFSET = .01f;
    /** Largest wall laid out as one (bigger ones are split into separately laid-out pieces). */
    private static final int MAX_WALL = 512;

    /** A wall's bounding rectangle: along-wall start of its first and last block, lowest and highest y. */
    record Layout(int minA, int maxA, int minY, int maxY) {
        int width() { return maxA - minA + 1; }
        int height() { return maxY - minY + 1; }
    }
    private static final Map<BlockPos, Layout> LAYOUTS = new HashMap<>();
    private static long layoutTick = Long.MIN_VALUE;

    public BarrierPlankRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public void render(GimmickEntity entity, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var state = entity.getBlockState();
        if (!(state.getBlock() instanceof BarrierPlankBlock) || !state.getValue(GimmickBlock.ACTIVE) || entity.getLevel() == null) return;
        var level = entity.getLevel();
        var pos = entity.getBlockPos();
        var axis = state.getValue(BarrierPlankBlock.AXIS);
        var wall = layout(level, pos, axis, level.getGameTime());
        int a = BarrierPlankBlock.start(axis, pos), y = pos.getY();
        Direction along = BarrierPlankBlock.along(axis);

        pose.pushPose();
        pose.translate(.5, 0, .5);
        if (axis == Direction.Axis.Z) pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90));
        var m = pose.last().pose(); var n = pose.last().normal();

        // Boards: u from the wall's left end, v from its top, both wrapping every PERIOD blocks.
        float u0 = (float) Math.floorMod(a - wall.minA(), (int) PERIOD) / PERIOD, u1 = u0 + 1 / PERIOD;
        float v0 = (float) Math.floorMod(wall.maxY() - y, (int) PERIOD) / PERIOD, v1 = v0 + 1 / PERIOD;
        var vc = buffers.getBuffer(RenderType.entityCutoutNoCull(BOARDS));
        // Front (-z) and back (+z) faces: v1 at the bottom edge, v0 at the top.
        quad(vc, m, n, light, -.5f, 0, -HALF, .5f, 0, -HALF, .5f, 1, -HALF, -.5f, 1, -HALF, u0, v1, u1, v0, 0, 0, -1);
        quad(vc, m, n, light, .5f, 0, HALF, -.5f, 0, HALF, -.5f, 1, HALF, .5f, 1, HALF, u1, v1, u0, v0, 0, 0, 1);
        // Edges only where the wall ends or a neighbour is broken: a thin strip cut from the boards.
        float t = HALF * 2 / PERIOD;
        if (!intact(level, pos.above(), axis)) quad(vc, m, n, light, -.5f, 1, -HALF, .5f, 1, -HALF, .5f, 1, HALF, -.5f, 1, HALF, u0, v0, u1, v0 + t, 0, 1, 0);
        if (!intact(level, pos.below(), axis)) quad(vc, m, n, light, -.5f, 0, HALF, .5f, 0, HALF, .5f, 0, -HALF, -.5f, 0, -HALF, u0, v1 - t, u1, v1, 0, -1, 0);
        if (!intact(level, pos.relative(along), axis)) quad(vc, m, n, light, .5f, 0, -HALF, .5f, 0, HALF, .5f, 1, HALF, .5f, 1, -HALF, u1 - t, v1, u1, v0, 1, 0, 0);
        if (!intact(level, pos.relative(along.getOpposite()), axis)) quad(vc, m, n, light, -.5f, 0, HALF, -.5f, 0, -HALF, -.5f, 1, -HALF, -.5f, 1, HALF, u0, v1, u0 + t, v0, -1, 0, 0);

        // The sign: a square as large as the wall's shorter side, centred; this block draws the part over its cell.
        float size = Math.min(wall.width(), wall.height());
        float left = (wall.minA() + wall.maxA() + 1) / 2f - size / 2, bottom = (wall.minY() + wall.maxY() + 1) / 2f - size / 2;
        float x0 = Math.max(a, left), x1 = Math.min(a + 1, left + size), y0 = Math.max(y, bottom), y1 = Math.min(y + 1, bottom + size);
        if (x1 > x0 && y1 > y0) {
            float su0 = (x0 - left) / size, su1 = (x1 - left) / size, sv0 = 1 - (y1 - bottom) / size, sv1 = 1 - (y0 - bottom) / size;
            float lx0 = x0 - a - .5f, lx1 = x1 - a - .5f, ly0 = y0 - y, ly1 = y1 - y, f = -HALF - SIGN_OFFSET, b = HALF + SIGN_OFFSET;
            var sign = buffers.getBuffer(RenderType.entityCutoutNoCull(SIGN));
            quad(sign, m, n, light, lx0, ly0, f, lx1, ly0, f, lx1, ly1, f, lx0, ly1, f, su0, sv1, su1, sv0, 0, 0, -1);
            // Seen from behind the sign reads mirrored, like the deployed barrier's.
            quad(sign, m, n, light, lx1, ly0, b, lx0, ly0, b, lx0, ly1, b, lx1, ly1, b, su1, sv1, su0, sv0, 0, 0, 1);
        }
        pose.popPose();
    }

    /** An intact plank of the same wall direction at {@code pos}. */
    private static boolean intact(BlockGetter level, BlockPos pos, Direction.Axis axis) {
        var s = level.getBlockState(pos);
        return s.getBlock() instanceof BarrierPlankBlock && s.getValue(BarrierPlankBlock.AXIS) == axis && s.getValue(GimmickBlock.ACTIVE);
    }
    private static boolean member(BlockState s, Direction.Axis axis) {
        return s.getBlock() instanceof BarrierPlankBlock && s.getValue(BarrierPlankBlock.AXIS) == axis;
    }
    /** The wall {@code pos} belongs to, found once per tick for the whole wall (block changes show up next tick). */
    static Layout layout(BlockGetter level, BlockPos pos, Direction.Axis axis, long tick) {
        if (tick != layoutTick) { LAYOUTS.clear(); layoutTick = tick; }
        var known = LAYOUTS.get(pos);
        if (known != null) return known;
        var members = new java.util.ArrayList<BlockPos>();
        var seen = new java.util.HashSet<BlockPos>();
        var queue = new ArrayDeque<BlockPos>();
        queue.add(pos.immutable()); seen.add(pos.immutable());
        Direction along = BarrierPlankBlock.along(axis);
        int minA = Integer.MAX_VALUE, maxA = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        while (!queue.isEmpty() && members.size() < MAX_WALL) {
            var at = queue.poll();
            members.add(at);
            int a = BarrierPlankBlock.start(axis, at);
            minA = Math.min(minA, a); maxA = Math.max(maxA, a); minY = Math.min(minY, at.getY()); maxY = Math.max(maxY, at.getY());
            for (var d : new Direction[]{along, along.getOpposite(), Direction.UP, Direction.DOWN}) {
                var next = at.relative(d);
                if (seen.add(next) && member(level.getBlockState(next), axis)) queue.add(next);
            }
        }
        var wall = new Layout(minA, maxA, minY, maxY);
        for (var at : members) LAYOUTS.put(at, wall);
        return wall;
    }

    /** Quad drawn without culling: u0 at corners 0 and 3, u1 at 1 and 2; v0 at corners 0 and 1, v1 at 2 and 3. */
    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, int light,
                             float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3,
                             float u0, float v0, float u1, float v1, float nx, float ny, float nz) {
        vertex(vc, m, n, light, x0, y0, z0, u0, v0, nx, ny, nz); vertex(vc, m, n, light, x1, y1, z1, u1, v0, nx, ny, nz);
        vertex(vc, m, n, light, x2, y2, z2, u1, v1, nx, ny, nz); vertex(vc, m, n, light, x3, y3, z3, u0, v1, nx, ny, nz);
    }
    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, int light, float x, float y, float z, float u, float v, float nx, float ny, float nz) {
        vc.vertex(m, x, y, z).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
    }
}
