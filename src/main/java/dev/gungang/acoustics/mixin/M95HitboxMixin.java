package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.M95;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Enlarge TaCZ's corrected hitbox after its ally/ignored-target filters, preserving native direct hits. */
@Pseudo
@Mixin(targets="com.tacz.guns.util.EntityUtil",remap=false)
public abstract class M95HitboxMixin {
    @Redirect(method="getHitResult",at=@At(value="INVOKE",target="Lnet/minecraft/world/phys/AABB;clip(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Ljava/util/Optional;",remap=true),require=1)
    private static java.util.Optional<Vec3> gungang$expandedHit(AABB box,Vec3 from,Vec3 to,Projectile bullet,Entity target,Vec3 start,Vec3 end){
        var hit=box.clip(from,to);double padding=M95.nativeTick(bullet)?M95.hitPadding():0;
        return hit.isPresent()||padding<=0?hit:box.inflate(padding).clip(from,to);
    }
    @Redirect(method={"findEntityOnPath","findEntitiesOnPath"},at=@At(value="INVOKE",target="Lnet/minecraft/world/phys/AABB;inflate(D)Lnet/minecraft/world/phys/AABB;",remap=true),require=1)
    private static AABB gungang$searchBounds(AABB box,double radius){
        return box.inflate(Math.max(radius,M95.hitPadding()+.1));
    }
}
