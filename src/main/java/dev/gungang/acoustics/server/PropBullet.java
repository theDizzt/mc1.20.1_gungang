package dev.gungang.acoustics.server;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Thin scenery is penetrable only by a prop-hunt seeker's issued gun. Disguise entities stay hittable. */
public final class PropBullet {
    public static java.util.function.Predicate<Entity> clientSeeker=entity->false;

    public static boolean penetrates(Entity entity){
        if(!(entity instanceof Projectile bullet)||!entity.getClass().getName().equals("com.tacz.guns.entity.EntityKineticBullet"))return false;
        if(!(bullet.getOwner() instanceof LivingEntity shooter))return false;
        if(!PropAwn.issued(shooter.getMainHandItem())&&!PropAwn.issued(shooter.getOffhandItem()))return false;
        if(entity.level().isClientSide)return clientSeeker.test(shooter);
        var match=MatchManager.realPropHunt();
        return match!=null&&match.game.phase()==PropHuntGame.Phase.HUNTING
                &&match.participant(shooter.getUUID())&&!match.hider(shooter.getUUID());
    }

    /** Retain thick pieces; thin rails/panes do not shield a thick post or another wall behind them. */
    public static VoxelShape blockingShape(VoxelShape shape){
        if(shape.isEmpty())return shape;
        var pieces=shape.toAabbs();
        VoxelShape result=Shapes.empty();boolean changed=false;
        for(var box:pieces){
            if(Math.min(box.getXsize(),Math.min(box.getYsize(),box.getZsize()))<.5)changed=true;
            else result=Shapes.or(result,Shapes.create(box));
        }
        return changed?result:shape;
    }
    private PropBullet(){}
}
