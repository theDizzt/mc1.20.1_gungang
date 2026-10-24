package dev.gungang.acoustics.server;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Stream;

/** Bot skins kept under config/gungang/skins and streamed to clients, so no resource pack is needed. */
public final class BotSkinFiles {
    public static final String PREFIX="file:";
    public static final int MAX_BYTES=65536;
    public static Path directory(){return ServerFiles.root().resolve("skins");}
    public static boolean valid(String skin){return skin.startsWith(PREFIX)&&skin.substring(PREFIX.length()).matches("[a-z0-9_.-]+\\.png")&&!skin.contains("..");}
    public static byte[] read(String skin)throws IOException{
        if(!valid(skin))throw new IOException("파일 스킨은 file:이름.png 형식(영문 소문자·숫자·_.-)으로 입력하세요.");
        String name=skin.substring(PREFIX.length());
        Path root=directory().toAbsolutePath().normalize(),file=root.resolve(name).normalize();
        if(!root.equals(file.getParent())||!Files.isRegularFile(file))throw new IOException("config/gungang/skins/"+name+" 파일이 없습니다.");
        if(Files.size(file)>MAX_BYTES)throw new IOException("스킨 PNG는 64KB 이하여야 합니다.");
        byte[] bytes;
        try(var input=Files.newInputStream(file)){bytes=input.readNBytes(MAX_BYTES+1);}
        int[] size=MapPreviewFiles.dimensions(bytes);
        if(size[0]!=64||size[1]!=64)throw new IOException("스킨은 64×64 PNG여야 합니다. 현재 "+size[0]+"×"+size[1]);
        return bytes;
    }
    public static List<String> names(){
        try(Stream<Path> files=Files.list(directory())){
            return files.map(p->p.getFileName().toString()).filter(n->valid(PREFIX+n)).sorted().map(n->PREFIX+n).toList();
        }catch(IOException missing){return List.of();}
    }
    private BotSkinFiles(){}
}
