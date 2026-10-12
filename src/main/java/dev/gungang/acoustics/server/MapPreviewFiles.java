package dev.gungang.acoustics.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.*;

/** Bounded PNG files only; client requests map IDs, never arbitrary server paths. */
public final class MapPreviewFiles {
    // Leave room for the channel packet's map ID and framing below Minecraft's 1 MiB payload limit.
    public static final int MAX_BYTES=1_000_000,MAX_EDGE=2048;
    public static boolean validName(String name){return name!=null&&name.length()<=128&&name.matches("[\\p{L}\\p{N}_ .-]+\\.[pP][nN][gG]")&&!name.contains("..");}
    public static int[] dimensions(byte[] bytes)throws IOException {
        if(bytes.length<33||bytes.length>MAX_BYTES)throw new IOException("PNG size must be at most 1 MB (1,000,000 bytes)");
        var buffer=ByteBuffer.wrap(bytes);
        if(buffer.getLong()!=0x89504e470d0a1a0aL||buffer.getInt()!=13||buffer.getInt()!=0x49484452)throw new IOException("Invalid PNG header");
        int w=buffer.getInt(),h=buffer.getInt();
        if(w<1||h<1||w>MAX_EDGE||h>MAX_EDGE)throw new IOException("PNG dimensions must be at most 2048 x 2048");
        return new int[]{w,h};
    }
    public static byte[] read(Path directory,String name)throws IOException {
        if(!validName(name))throw new IOException("Invalid preview filename");
        Path root=directory.toRealPath(),file=root.resolve(name).toRealPath();
        if(!file.getParent().equals(root)||!Files.isRegularFile(file)||Files.size(file)>MAX_BYTES)throw new IOException("Invalid preview file");
        byte[] bytes;
        try(var input=Files.newInputStream(file)){bytes=input.readNBytes(MAX_BYTES+1);}
        dimensions(bytes);return bytes;
    }
}
