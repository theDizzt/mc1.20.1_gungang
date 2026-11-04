package dev.gungang.acoustics.client;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.gungang.acoustics.server.PropEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EntityType;
final class PropObjectRenderer {
    static boolean render(PropEntity prop,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        var mc=Minecraft.getInstance();
        if(prop.item().equals("minecraft:armor_stand")){
            var stand=stand(prop);float yaw=net.minecraft.util.Mth.rotLerp(partial,prop.yRotO,prop.getYRot());
            stand.setPos(prop.position());stand.xOld=stand.getX();stand.yOld=stand.getY();stand.zOld=stand.getZ();
            stand.setYRot(yaw);stand.yRotO=yaw;stand.yBodyRot=yaw;stand.yBodyRotO=yaw;stand.yHeadRot=yaw;stand.yHeadRotO=yaw;
            mc.getEntityRenderDispatcher().render(stand,0,0,0,yaw,partial,pose,buffers,light);return true;
        }
        if(prop.item().equals("minecraft:minecart")||prop.item().equals("minecraft:tnt_minecart")||prop.item().equals("minecraft:end_crystal")){
            net.minecraft.world.entity.Entity object=prop.item().equals("minecraft:minecart")?EntityType.MINECART.create(prop.level()):prop.item().equals("minecraft:tnt_minecart")?EntityType.TNT_MINECART.create(prop.level()):EntityType.END_CRYSTAL.create(prop.level());
            object.setPos(prop.position());object.xOld=object.getX();object.yOld=object.getY();object.zOld=object.getZ();object.setId(prop.getId());object.tickCount=prop.tickCount;
            if(object instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal crystal){crystal.time=prop.tickCount;crystal.setShowBottom(true);}
            // Vanilla crystal origin is one block above its base; props are anchored at floor height.
            boolean crystal=prop.item().equals("minecraft:end_crystal");
            float yaw=net.minecraft.util.Mth.rotLerp(partial,prop.yRotO,prop.getYRot())+90;
            mc.getEntityRenderDispatcher().render(object,0,crystal?1:0,0,crystal?0:yaw,partial,pose,buffers,crystal?LightTexture.pack(15,LightTexture.sky(light)):light);return true;
        }
        String item=prop.item();
        if(!item.equals("gungang:mannequin")&&!item.equals("gungang:eunbi_doll")&&!item.equals("gungang:hanbi_doll"))return false;
        // 184 / 191: the dolls wear their own fixed skins; the mannequin a seeker's.
        boolean doll=!item.equals("gungang:mannequin");
        var info=doll||mc.getConnection()==null?null:mc.getConnection().getPlayerInfo(prop.skin());boolean slim=info!=null&&info.getModelName().equals("slim");
        var texture=doll?new net.minecraft.resources.ResourceLocation("gungang","textures/entity/doll/"+(item.equals("gungang:eunbi_doll")?"eunbi":"hanbi")+".png"):MatchHud.skin(prop.skin().toString());
        pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees(180-net.minecraft.util.Mth.rotLerp(partial,prop.yRotO,prop.getYRot())));pose.scale(-1,-1,1);pose.translate(0,-1.501,0);
        var root=figure(slim);root.getAllParts().forEach(net.minecraft.client.model.geom.ModelPart::resetPose);
        var head=root.getChild("head");var leftArm=root.getChild("left_arm");var rightArm=root.getChild("right_arm");var leftLeg=root.getChild("left_leg");var rightLeg=root.getChild("right_leg");
        int mode=prop.mode();leftArm.zRot=mode==1?-1.57f:mode==2?-.8f:0;rightArm.zRot=mode==1?1.57f:mode==2?.8f:0;
        if(mode==3){leftArm.xRot=-2.6f;rightArm.xRot=-2.6f;}
        if(mode==4){leftLeg.xRot=-1.5f;rightLeg.xRot=-1.5f;leftArm.xRot=-.7f;rightArm.xRot=-.7f;}
        if(mode==5){rightArm.zRot=2.5f;head.zRot=.25f;}
        // Each outer layer (hat, jacket, sleeves, trousers) is a child of its limb, so it follows the pose.
        root.render(pose,buffers.getBuffer(RenderType.entityTranslucent(texture)),light,OverlayTexture.NO_OVERLAY);pose.popPose();return true;
    }
    /**
     * The mannequin and dolls' own player-shaped model, built here rather than through PlayerModel/HumanoidModel: other
     * client mods (Armourer's Workshop, gun animation mods) hook those classes for real players, and drawing a skin
     * through them twisted the torso edge-on and dropped parts of the second skin layer.
     */
    private static net.minecraft.client.model.geom.ModelPart wide,slimFigure;
    private static net.minecraft.client.model.geom.ModelPart figure(boolean slim){
        if(slim)return slimFigure==null?slimFigure=bake(true):slimFigure;
        return wide==null?wide=bake(false):wide;
    }
    private static net.minecraft.client.model.geom.ModelPart bake(boolean slim){
        var mesh=new net.minecraft.client.model.geom.builders.MeshDefinition();var root=mesh.getRoot();
        var none=net.minecraft.client.model.geom.builders.CubeDeformation.NONE;var thin=new net.minecraft.client.model.geom.builders.CubeDeformation(.25f);
        var head=root.addOrReplaceChild("head",box(0,0,-4,-8,-4,8,8,8,none),net.minecraft.client.model.geom.PartPose.ZERO);
        head.addOrReplaceChild("hat",box(32,0,-4,-8,-4,8,8,8,new net.minecraft.client.model.geom.builders.CubeDeformation(.5f)),net.minecraft.client.model.geom.PartPose.ZERO);
        var body=root.addOrReplaceChild("body",box(16,16,-4,0,-2,8,12,4,none),net.minecraft.client.model.geom.PartPose.ZERO);
        body.addOrReplaceChild("jacket",box(16,32,-4,0,-2,8,12,4,thin),net.minecraft.client.model.geom.PartPose.ZERO);
        int arm=slim?3:4;float shoulder=slim?2.5f:2;
        var right=root.addOrReplaceChild("right_arm",box(40,16,slim?-2:-3,-2,-2,arm,12,4,none),net.minecraft.client.model.geom.PartPose.offset(-5,shoulder,0));
        right.addOrReplaceChild("right_sleeve",box(40,32,slim?-2:-3,-2,-2,arm,12,4,thin),net.minecraft.client.model.geom.PartPose.ZERO);
        var left=root.addOrReplaceChild("left_arm",box(32,48,-1,-2,-2,arm,12,4,none),net.minecraft.client.model.geom.PartPose.offset(5,shoulder,0));
        left.addOrReplaceChild("left_sleeve",box(48,48,-1,-2,-2,arm,12,4,thin),net.minecraft.client.model.geom.PartPose.ZERO);
        var rightLeg=root.addOrReplaceChild("right_leg",box(0,16,-2,0,-2,4,12,4,none),net.minecraft.client.model.geom.PartPose.offset(-1.9f,12,0));
        rightLeg.addOrReplaceChild("right_pants",box(0,32,-2,0,-2,4,12,4,thin),net.minecraft.client.model.geom.PartPose.ZERO);
        var leftLeg=root.addOrReplaceChild("left_leg",box(16,48,-2,0,-2,4,12,4,none),net.minecraft.client.model.geom.PartPose.offset(1.9f,12,0));
        leftLeg.addOrReplaceChild("left_pants",box(0,48,-2,0,-2,4,12,4,thin),net.minecraft.client.model.geom.PartPose.ZERO);
        return net.minecraft.client.model.geom.builders.LayerDefinition.create(mesh,64,64).bakeRoot();
    }
    private static net.minecraft.client.model.geom.builders.CubeListBuilder box(int u,int v,float x,float y,float z,int w,int h,int d,net.minecraft.client.model.geom.builders.CubeDeformation grow){
        return net.minecraft.client.model.geom.builders.CubeListBuilder.create().texOffs(u,v).addBox(x,y,z,w,h,d,grow);
    }
    /** 176: one client armour stand per disguise, rebuilt when its armour (TEXT) or pose (MODE) changes. */
    private static final java.util.Map<Integer,net.minecraft.world.entity.decoration.ArmorStand> STANDS=new java.util.HashMap<>();
    private static final java.util.Map<Integer,String> STAND_KEYS=new java.util.HashMap<>();
    /** Head, body, left arm, right arm, left leg, right leg (x, y, z degrees) for each pose. */
    private static final float[][] POSES={
        {0,0,0, 0,0,0, -10,0,-10, -15,0,10, -1,0,-1, 1,0,1},
        {-10,20,0, 0,0,0, -10,0,-10, -140,10,20, -1,0,-1, 1,0,1},
        {15,0,0, 0,0,0, -110,30,0, -110,-30,0, 0,0,0, 0,0,0},
        {-15,0,0, 0,0,0, -180,0,-15, -180,0,15, 0,0,-8, 0,0,8},
        {0,0,15, 0,0,5, 20,0,-60, -90,0,40, -40,0,0, 30,0,0},
        {10,-25,0, 5,0,0, -60,40,0, -50,-30,0, 15,0,-5, -20,0,5}};
    private static net.minecraft.world.entity.decoration.ArmorStand stand(PropEntity prop){
        String key=prop.text()+"|"+prop.mode();var stand=STANDS.get(prop.getId());
        if(stand!=null&&key.equals(STAND_KEYS.get(prop.getId()))&&stand.level()==prop.level())return stand;
        if(STANDS.size()>64){STANDS.clear();STAND_KEYS.clear();}
        stand=EntityType.ARMOR_STAND.create(prop.level());
        var tag=new net.minecraft.nbt.CompoundTag();tag.putBoolean("ShowArms",true);
        // ArmorItems run feet, legs, chest, head; the server sends head, chest, legs, feet.
        String[] ids=(prop.text()+",,,,").split(",",-1);var armor=new net.minecraft.nbt.ListTag();
        for(int i=3;i>=0;i--){var item=new net.minecraft.nbt.CompoundTag();if(!ids[i].isEmpty()){item.putString("id",ids[i]);item.putByte("Count",(byte)1);}armor.add(item);}
        tag.put("ArmorItems",armor);
        float[] p=POSES[Math.floorMod(prop.mode(),POSES.length)];var poseTag=new net.minecraft.nbt.CompoundTag();String[] parts={"Head","Body","LeftArm","RightArm","LeftLeg","RightLeg"};
        for(int i=0;i<parts.length;i++){var list=new net.minecraft.nbt.ListTag();for(int j=0;j<3;j++)list.add(net.minecraft.nbt.FloatTag.valueOf(p[i*3+j]));poseTag.put(parts[i],list);}
        tag.put("Pose",poseTag);stand.load(tag);
        STANDS.put(prop.getId(),stand);STAND_KEYS.put(prop.getId(),key);return stand;
    }
    private PropObjectRenderer(){}
}
