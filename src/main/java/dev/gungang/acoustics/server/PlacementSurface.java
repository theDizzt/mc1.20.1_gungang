package dev.gungang.acoustics.server;

import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Placement positions name the cell above the support; the actual surface may be fractional. */
public final class PlacementSurface {
    public static double height(Level level,BlockPos above) {
        var below=above.below();var shape=level.getBlockState(below).getCollisionShape(level,below);
        return shape.isEmpty()?above.getY():below.getY()+shape.max(Direction.Axis.Y);
    }
    public static boolean supports(Level level,BlockPos above,double height) {
        var below=above.below();if(!level.hasChunkAt(below))return false;
        var shape=level.getBlockState(below).getCollisionShape(level,below);
        if(shape.isEmpty())return false;
        double top=shape.max(Direction.Axis.Y);
        if(Math.abs(below.getY()+top-height)>1e-5)return false;
        // Slabs always have a complete horizontal top, even when that face is below the block-cell boundary.
        if(level.getBlockState(below).getBlock() instanceof net.minecraft.world.level.block.SlabBlock)return true;
        // Require a flat, fully supported top, including bottom/top/double slabs.
        return !Shapes.joinIsNotEmpty(Shapes.box(0,top-.001,0,1,top,1),shape,BooleanOp.ONLY_FIRST);
    }
    public static Vec3 anchorImpact(Level level,BlockHitResult hit) {
        var state=level.getBlockState(hit.getBlockPos());
        if(state.getBlock() instanceof net.minecraft.world.level.block.SlabBlock) {
            var shape=state.getCollisionShape(level,hit.getBlockPos());var at=hit.getLocation();
            return new Vec3(net.minecraft.util.Mth.clamp(at.x,hit.getBlockPos().getX()+.001,hit.getBlockPos().getX()+.999),
                    // Float above the actual slab surface; the landing preview uses this same position.
                    hit.getBlockPos().getY()+shape.max(Direction.Axis.Y)+.1,
                    net.minecraft.util.Mth.clamp(at.z,hit.getBlockPos().getZ()+.001,hit.getBlockPos().getZ()+.999));
        }
        return hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(.03));
    }
    private PlacementSurface() { }
}
