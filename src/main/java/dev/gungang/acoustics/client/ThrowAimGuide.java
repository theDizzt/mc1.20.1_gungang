package dev.gungang.acoustics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

/** Quiet, depth-tested throwing guides shared by secondary weapons. */
final class ThrowAimGuide {
    // World-distance spacing keeps dashes consistent as projectile speed changes.
    static void trail(PoseStack pose,VertexConsumer lines,Vec3 from,Vec3 to,double travelled) {
        double length=from.distanceTo(to);
        if(length<1e-6)return;
        Vec3 direction=to.subtract(from).scale(1/length);
        double cursor=0;
        while(cursor<length) {
            double phase=(travelled+cursor)%.8;
            boolean visible=phase<.22;
            double next=Math.min(length,cursor+(visible?.22-phase:.8-phase));
            if(next-cursor<1e-6)next=Math.min(length,cursor+1e-6);
            // Leave the crosshair and hand clear; ease into the trajectory.
            float alpha=(float)(.38*Math.min(1,Math.max(0,(travelled+cursor-1.2)/2)));
            if(visible&&alpha>0)line(pose,lines,from.add(direction.scale(cursor)),from.add(direction.scale(next)),.45f,.9f,.85f,alpha);
            cursor=next;
        }
    }

    static void impact(PoseStack pose,VertexConsumer lines,Vec3 point,Vec3 normal,boolean entity) {
        Vec3 n=normal.normalize();
        if(n.lengthSqr()<1e-6)n=new Vec3(0,1,0);
        Vec3 u=n.cross(Math.abs(n.y)<.9?new Vec3(0,1,0):new Vec3(1,0,0)).normalize();
        Vec3 v=n.cross(u);
        Vec3 center=point.add(n.scale(.025));
        float r=entity?1f:.65f,g=entity?.65f:1f,b=entity?.25f:.9f;
        double radius=.18;
        for(int i=0;i<24;i++) {
            double a=i*Math.PI/12,c=(i+1)*Math.PI/12;
            line(pose,lines,center.add(u.scale(Math.cos(a)*radius)).add(v.scale(Math.sin(a)*radius)),
                    center.add(u.scale(Math.cos(c)*radius)).add(v.scale(Math.sin(c)*radius)),r,g,b,.9f);
        }
        // A small cross locates the contact precisely; an X also distinguishes entity hits without color.
        Vec3 x=entity?u.add(v).normalize():u,y=entity?u.subtract(v).normalize():v;
        line(pose,lines,center.add(x.scale(-.055)),center.add(x.scale(.055)),r,g,b,1);
        line(pose,lines,center.add(y.scale(-.055)),center.add(y.scale(.055)),r,g,b,1);
    }

    private static void line(PoseStack pose,VertexConsumer lines,Vec3 a,Vec3 b,float r,float g,float blue,float alpha) {
        Vec3 n=b.subtract(a).normalize();
        lines.vertex(pose.last().pose(),(float)a.x,(float)a.y,(float)a.z).color(r,g,blue,alpha).normal(pose.last().normal(),(float)n.x,(float)n.y,(float)n.z).endVertex();
        lines.vertex(pose.last().pose(),(float)b.x,(float)b.y,(float)b.z).color(r,g,blue,alpha).normal(pose.last().normal(),(float)n.x,(float)n.y,(float)n.z).endVertex();
    }
    private ThrowAimGuide() { }
}
