package dev.gungang.acoustics.server;

/** Inclusive block bounds; maxY can cut away the roof of an indoor arena. */
public record MapPreviewArea(int minX,int minY,int minZ,int maxX,int maxY,int maxZ) {
    public void validate() {
        if(minX < -29_999_000 || maxX > 29_999_000 || minZ < -29_999_000 || maxZ > 29_999_000
                || minX>maxX || minZ>maxZ || minY>maxY || minY < -2048 || maxY>2048
                || (long)maxX-minX>=512 || (long)maxZ-minZ>=512 || (long)maxY-minY>=512)
            throw new IllegalArgumentException("previewArea must be an ordered region of at most 512 blocks per axis");
    }
    public double u(double x){return (x-minX)/(maxX-minX+1.0);}
    public double v(double z){return (z-minZ)/(maxZ-minZ+1.0);}
}
