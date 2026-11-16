package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * 37 glue gun in first person: held at the hip like a TaCZ pistol, slid to the middle of the view while zoomed (the
 * body's top just under the crosshair), with a kick back and up on every shot. Replaces the vanilla arm transform; the
 * item model's own first-person display transform still applies after it.
 */
public final class GlueGunHand implements IClientItemExtensions {
    /** Where vanilla holds an item (right hand) and where the zoomed gun sits: centred, raised and nearer the eye. */
    private static final float HIP_X=.56f,HIP_Y=-.52f,HIP_Z=-.72f,AIM_X=0,AIM_Y=-.37f,AIM_Z=-.5f;
    /** The model's first-person display turns it 4 degrees toward the crosshair; zoomed it points straight ahead. */
    private static final float DISPLAY_YAW=4;

    @Override public boolean applyForgeHandTransform(PoseStack pose,LocalPlayer player,HumanoidArm arm,ItemStack stack,float partial,float equip,float swing){
        float side=arm==HumanoidArm.RIGHT?1:-1;
        float aim=GlueGunInput.aim(partial),kick=GlueGunInput.recoil();
        float x=Mth.lerp(aim,HIP_X*side,AIM_X),y=Mth.lerp(aim,HIP_Y,AIM_Y)+equip*-.6f,z=Mth.lerp(aim,HIP_Z,AIM_Z);
        // Recoil: back toward the eye and muzzle up; smaller while aiming so the sight picture stays usable.
        float strength=1-.5f*aim;
        pose.translate(x,y+.02f*kick*strength,z+.09f*kick*strength);
        pose.mulPose(Axis.XP.rotationDegrees(9*kick*strength));
        pose.mulPose(Axis.YP.rotationDegrees(-DISPLAY_YAW*side*aim));
        return true;
    }
}
