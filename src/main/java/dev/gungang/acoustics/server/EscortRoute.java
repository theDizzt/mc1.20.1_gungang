package dev.gungang.acoustics.server;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class EscortRoute {
    public final List<BlockPos> markers;
    public final List<Vec3> samples=new ArrayList<>();
    private final List<Vec3> tangents=new ArrayList<>();
    public final List<Double> lengths=new ArrayList<>(),checkpoints=new ArrayList<>();
    public double length;
    private EscortRoute(List<BlockPos> markers){this.markers=List.copyOf(markers);}
    public static EscortRoute scan(ServerLevel level,EscortRules rules){
        rules.validate();var a=rules.scanArea();var found=new ArrayList<BlockPos>();
        for(var p:BlockPos.betweenClosed((int)Math.floor(a.minX()),(int)Math.floor(a.minY()),(int)Math.floor(a.minZ()),(int)Math.floor(a.maxX()),(int)Math.floor(a.maxY()),(int)Math.floor(a.maxZ())))
            if(level.getBlockState(p).getBlock() instanceof EscortBlocks.Marker)found.add(p.immutable());
        return build(level,found,rules.linkDistance());
    }
    public static EscortRoute build(ServerLevel level,List<BlockPos> found,double link){
        if(found.size()<3||found.size()>512)throw new IllegalArgumentException("호위 경로: 지정 블록 3~512개가 필요합니다.");
        var starts=found.stream().filter(p->kind(level,p)==1).toList();var ends=found.stream().filter(p->kind(level,p)==2).toList();
        long count=found.stream().filter(p->kind(level,p)==3).count();
        if(starts.size()!=1||ends.size()!=1||count<1||count>3)throw new IllegalArgumentException("호위 경로: 시작·도착 각 1개, 중간거점 1~3개가 필요합니다.");
        var ordered=new ArrayList<BlockPos>();var left=new HashSet<>(found);BlockPos current=starts.get(0);ordered.add(current);left.remove(current);
        while(!left.isEmpty()){
            final BlockPos at=current;var candidates=left.stream().sorted(Comparator.comparingDouble(p->p.distSqr(at))).toList();
            var next=candidates.get(0);double d=next.distSqr(current);
            if(d>link*link)throw new IllegalArgumentException("호위 경로가 끊겼습니다: "+current);
            if(candidates.size()>1&&Math.abs(candidates.get(1).distSqr(current)-d)<.01)throw new IllegalArgumentException("호위 경로가 모호합니다. 다음 포인트까지 거리를 다르게 배치하세요: "+current);
            if(kind(level,current)==2)throw new IllegalArgumentException("도착점 뒤에 연결되지 않은 지정 블록이 있습니다.");
            if(Math.abs(next.getY()-current.getY())>Math.sqrt(Math.pow(next.getX()-current.getX(),2)+Math.pow(next.getZ()-current.getZ(),2))*.6)throw new IllegalArgumentException("호위 경사가 너무 가파릅니다 (최대 0.6).");
            ordered.add(next);left.remove(next);current=next;
        }
        if(!current.equals(ends.get(0)))throw new IllegalArgumentException("호위 경로의 마지막은 도착점이어야 합니다.");
        var r=new EscortRoute(ordered);r.samples.add(center(ordered.get(0)));r.lengths.add(0d);
        var curve=new EscortCurve(ordered.stream().map(EscortRoute::center).toList());r.tangents.add(curve.direction(0,0));
        for(int i=0;i<ordered.size()-1;i++){
            int steps=Math.max(16,(int)Math.ceil(center(ordered.get(i)).distanceTo(center(ordered.get(i+1)))*16));
            for(int s=1;s<=steps;s++){
                double t=(double)s/steps;Vec3 v=curve.position(i,t);r.tangents.add(curve.direction(i,t));
                r.length+=v.distanceTo(r.samples.get(r.samples.size()-1));r.samples.add(v);r.lengths.add(r.length);
            }
            if(kind(level,ordered.get(i+1))==3)r.checkpoints.add(r.length);
        }
        return r;
    }
    public Vec3 at(double distance){
        distance=Math.max(0,Math.min(length,distance));int i=Collections.binarySearch(lengths,distance);if(i>=0)return samples.get(i);i=-i-1;if(i==0)return samples.get(0);
        return samples.get(i-1).lerp(samples.get(i),(distance-lengths.get(i-1))/(lengths.get(i)-lengths.get(i-1)));
    }
    public Vec3 direction(double distance){
        distance=Math.max(0,Math.min(length,distance));int i=Collections.binarySearch(lengths,distance);if(i>=0)return tangents.get(i);i=-i-1;if(i==0)return tangents.get(0);
        return tangents.get(i-1).lerp(tangents.get(i),(distance-lengths.get(i-1))/(lengths.get(i)-lengths.get(i-1))).normalize();
    }
    public static int kind(ServerLevel l,BlockPos p){return l.getBlockState(p).getBlock() instanceof EscortBlocks.Marker m?m.kind:-1;}
    private static Vec3 center(BlockPos p){return new Vec3(p.getX()+.5,p.getY()+.05,p.getZ()+.5);}
}
