package dev.gungang.acoustics.mixin;

import dev.gungang.acoustics.server.PelletHits;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * TaCZ sends its hit message (hit marker, hit sound, hurt bob) to every player in the dimension, once per bullet: a
 * 15-pellet shotgun blast is 15 packets to everyone. Only the shooter and the one hit react to it, so it goes to them.
 */
@Pseudo
@Mixin(targets="com.tacz.guns.entity.EntityKineticBullet",remap=false)
public abstract class TaczHurtMessageMixin {
    /** {@code sendToDimension(message, hurt entity)}: the kill message, then the hit message. */
    @Redirect(method="onHitEntity(Lcom/tacz/guns/util/TacHitResult;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)V",
            at=@At(value="INVOKE",target="Lcom/tacz/guns/network/NetworkHandler;sendToDimension(Ljava/lang/Object;Lnet/minecraft/world/entity/Entity;)V"),require=0)
    private void gungang$send(Object message,Entity hurt){
        PelletHits.sendHitMessage(message,hurt,((Projectile)(Object)this).getOwner());
    }
}
