package dev.gungang.acoustics.server;

/** Axis-aligned map region; intersects the player's body, including thin floor traps. */
public record DeathZone(double minX,double minY,double minZ,double maxX,double maxY,double maxZ){
    public void validate(){
        for(double v:new double[]{minX,minY,minZ,maxX,maxY,maxZ})if(!Double.isFinite(v)||Math.abs(v)>29999000)throw new IllegalArgumentException("Invalid death zone coordinates");
        if(maxX<=minX||maxY<=minY||maxZ<=minZ)throw new IllegalArgumentException("Death zone bounds must have positive volume");
    }
    public boolean intersects(double x0,double y0,double z0,double x1,double y1,double z1){
        return x1>minX&&x0<maxX&&y1>minY&&y0<maxY&&z1>minZ&&z0<maxZ;
    }
}
