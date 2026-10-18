package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.PropEntity;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Disguises collide with their real block shapes, so others can stand on them instead of walking through. */
@Mixin(Entity.class)
public abstract class PropCollisionMixin {
    @Redirect(method="collide",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;getEntityCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private List<VoxelShape> gungang$propCollisions(Level level,Entity entity,AABB box) {
        var shapes=level.getEntityCollisions(entity,box);
        var props=PropEntity.collisions(level,entity,box);
        if(props.isEmpty())return shapes;
        var merged=new java.util.ArrayList<VoxelShape>(shapes);merged.addAll(props);return merged;
    }
}
