package dev.gungang.acoustics.server;

import dev.gungang.acoustics.GungangAcoustics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.zip.CRC32;
import java.util.*;
import javax.imageio.ImageIO;

/** Server-side map colors, one existing chunk per tick. Never generates new terrain.
 *  Finished maps are kept in server/maps/data/<id>.png and reused until the map JSON changes. */
@Mod.EventBusSubscriber(modid=GungangAcoustics.ID)
public final class MapPreviewGenerator {
    private static final Map<ArenaMap,byte[]> CACHE=new LinkedHashMap<>();
    private static final Map<ArenaMap,Job> JOBS=new LinkedHashMap<>();
    private record Pixel(int px,int pz,int x,int z) {}
    private static final class Job {
        final ArenaMap map;
        final ServerLevel level;
        final BufferedImage image;
        final int width,height,unit;
        final Set<UUID> viewers=new HashSet<>();
        final Iterator<Map.Entry<Long,java.util.List<Pixel>>> chunks;
        boolean missing;
        Job(ArenaMap map,ServerLevel level){
            this.map=map;this.level=level;var a=map.previewArea();
            double scale=Math.min(1,256.0/Math.max(a.maxX()-a.minX()+1,a.maxZ()-a.minZ()+1));
            width=Math.max(1,(int)Math.ceil((a.maxX()-a.minX()+1)*scale));
            height=Math.max(1,(int)Math.ceil((a.maxZ()-a.minZ()+1)*scale));
            unit=Math.max(2,(int)Math.ceil(256.0/Math.max(width,height)));
            image=new BufferedImage(Math.max(256,width*unit),height*unit+42,BufferedImage.TYPE_INT_RGB);
            var groups=new LinkedHashMap<Long,java.util.List<Pixel>>();
            for(int z=0;z<height;z++)for(int x=0;x<width;x++){
                int wx=a.minX()+(int)((x+.5)*(a.maxX()-a.minX()+1)/width);
                int wz=a.minZ()+(int)((z+.5)*(a.maxZ()-a.minZ()+1)/height);
                groups.computeIfAbsent(net.minecraft.world.level.ChunkPos.asLong(wx>>4,wz>>4),k->new ArrayList<>()).add(new Pixel(x,z,wx,wz));
            }
            chunks=groups.entrySet().iterator();
        }
        boolean step(){
            if(!chunks.hasNext())return true;
            var entry=chunks.next();var cp=new net.minecraft.world.level.ChunkPos(entry.getKey());
            var chunk=level.getChunk(cp.x,cp.z,ChunkStatus.FULL,false);
            if(chunk==null){
                // create=false only sees available chunks. Check disk before loading distant arenas,
                // so a valid saved map works even when no player is near it.
                var saved=level.getChunkSource().chunkMap.read(cp).join();
                if(saved.isPresent()){
                    String status=saved.get().getString("Status");
                    if(status.equals("minecraft:full")||status.equals("full"))chunk=level.getChunk(cp.x,cp.z,ChunkStatus.FULL,true);
                }
            }
            var a=map.previewArea();var pos=new BlockPos.MutableBlockPos();
            for(var pixel:entry.getValue()){
                int color=0x202a33;
                if(chunk==null){missing=true;color=((pixel.px+pixel.pz)/4%2==0)?0x403743:0x29232c;}
                else for(int y=Math.min(a.maxY(),level.getMaxBuildHeight()-1);y>=Math.max(a.minY(),level.getMinBuildHeight());y--){
                    pos.set(pixel.x,y,pixel.z);var state=chunk.getBlockState(pos);
                    if(state.isAir())continue;
                    var mapColor=state.getMapColor(level,pos);if(mapColor==MapColor.NONE)continue;
                    int rgb=mapColor.col;double light=.75+.25*(y-a.minY())/Math.max(1.0,a.maxY()-a.minY());
                    color=((int)(((rgb>>16)&255)*light)<<16)|((int)(((rgb>>8)&255)*light)<<8)|(int)((rgb&255)*light);break;
                }
                for(int dz=0;dz<unit;dz++)for(int dx=0;dx<unit;dx++)image.setRGB(pixel.px*unit+dx,pixel.pz*unit+dz,color);
            }
            return !chunks.hasNext();
        }
        int x(double world){return (int)Math.round(map.previewArea().u(world)*width*unit);}
        int z(double world){return (int)Math.round(map.previewArea().v(world)*height*unit);}
        void area(Graphics2D g,ArenaMap.Area a,Color color){
            g.setColor(new Color(color.getRed(),color.getGreen(),color.getBlue(),55));
            int x=x(a.minX()),z=z(a.minZ()),w=x(a.maxX())-x,h=z(a.maxZ())-z;
            g.fillRect(x,z,w,h);g.setColor(color);g.drawRect(x,z,w,h);
        }
        void marker(Graphics2D g,double wx,double wz,String label,Color color,boolean diamond){
            if(map.previewArea().u(wx)<0||map.previewArea().u(wx)>=1||map.previewArea().v(wz)<0||map.previewArea().v(wz)>=1)return;
            int x=Math.max(10,Math.min(width*unit-10,x(wx))),z=Math.max(10,Math.min(height*unit-10,z(wz)));
            g.setColor(Color.BLACK);g.fillOval(x-9,z-9,18,18);g.setColor(color);
            if(diamond)g.fillPolygon(new int[]{x,x+8,x,x-8},new int[]{z-8,z,z+8,z},4);else g.fillOval(x-8,z-8,16,16);
            g.setColor(Color.WHITE);g.drawString(label,x-g.getFontMetrics().stringWidth(label)/2,z+4);
        }
        byte[] finish() throws Exception {
            var g=image.createGraphics();g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,11));g.setClip(0,0,width*unit,height*unit);
            var options=LobbyManager.options();var red=new Color(options.color("ALPHA")&0xFFFFFF);var blue=new Color(options.color("BRAVO")&0xFFFFFF);var gold=new Color(210,155,20);var orange=new Color(240,120,20);
            area(g,map.alphaArea(),red);area(g,map.bravoArea(),blue);
            marker(g,map.alphaSpawn().x(),map.alphaSpawn().z(),"A",red,false);
            marker(g,map.bravoSpawn().x(),map.bravoSpawn().z(),"B",blue,false);
            if(map.capture()!=null){var a=map.capture().area();area(g,a,orange);marker(g,(a.minX()+a.maxX())/2,(a.minZ()+a.maxZ())/2,"C",orange,true);}
            if(map.bomb()!=null){
                var sites=new BombRules.Site[]{map.bomb().areaA(),map.bomb().areaB()};var points=new BombRules.Point[]{map.bomb().pointA(),map.bomb().pointB()};
                for(int i=0;i<2;i++){var site=sites[i];var p=points[i];
                    if(site!=null)area(g,new ArenaMap.Area(site.minX(),site.minY(),site.minZ(),site.maxX()+1,site.maxY()+1,site.maxZ()+1),gold);
                    marker(g,site==null?p.x()+.5:site.centerX(),site==null?p.z()+.5:site.centerZ(),i==0?"A":"B",gold,true);
                }
            }
            if(map.normandy()!=null){int i=1;for(var p:map.normandy().points())marker(g,p.center().x(),p.center().z(),""+i++,orange,true);}
            g.setClip(null);g.setColor(new Color(20,26,35));g.fillRect(0,height*unit,image.getWidth(),42);g.setColor(Color.WHITE);
            g.drawString(map.bomb()==null?"N ^  |  A: ALPHA / B: BRAVO":"N ^  |  A: Attack / B: Defend",5,height*unit+14);
            g.drawString((map.bomb()!=null?"Gold diamond: bomb site":"Orange diamond: capture point")+(missing?"  |  ! Unexplored":""),5,height*unit+30);g.dispose();
            var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);var png=bytes.toByteArray();MapPreviewFiles.dimensions(png);return png;
        }
    }
    // Bump when the rendering changes so old files are regenerated.
    private static final String FORMAT="2";
    private static final String KEYWORD="gungang-map",SEPARATOR=String.valueOf((char)0);
    public static Path directory(){return ServerFiles.root().resolve("server/maps/data");}
    private static Path file(String id){return directory().resolve(id+".png");}
    // Team colors are drawn into the image, so option.json changes must invalidate saved files.
    private static String teamColors(){var options=LobbyManager.options();return options.alphaColor()+","+options.bravoColor();}
    private static String fingerprint(ArenaMap map){
        try{
            var digest=MessageDigest.getInstance("SHA-256").digest((FORMAT+":"+teamColors()+":"+ServerFiles.GSON.toJson(map)).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        }catch(Exception failure){throw new IllegalStateException(failure);}
    }
    /** Stores the fingerprint as a tEXt chunk right after IHDR, so one file per map ID is enough. */
    private static byte[] tag(byte[] png,String key){
        byte[] data=(KEYWORD+SEPARATOR+key).getBytes(StandardCharsets.ISO_8859_1),type="tEXt".getBytes(StandardCharsets.ISO_8859_1);
        var crc=new CRC32();crc.update(type);crc.update(data);
        var chunk=ByteBuffer.allocate(12+data.length).putInt(data.length).put(type).put(data).putInt((int)crc.getValue()).array();
        var out=new byte[png.length+chunk.length];
        System.arraycopy(png,0,out,0,33);System.arraycopy(chunk,0,out,33,chunk.length);System.arraycopy(png,33,out,33+chunk.length,png.length-33);
        return out;
    }
    private static String key(byte[] png){
        if(png.length<45)return "";
        var buffer=ByteBuffer.wrap(png,33,png.length-33);int length=buffer.getInt();
        if(buffer.getInt()!=0x74455874||length<0||length>256||length>buffer.remaining()-4)return "";
        var text=new String(png,41,length,StandardCharsets.ISO_8859_1);
        return text.startsWith(KEYWORD+SEPARATOR)?text.substring(KEYWORD.length()+1):"";
    }
    private static byte[] load(ArenaMap map){
        var path=file(map.id());
        if(!Files.isRegularFile(path))return null;
        try{
            var png=MapPreviewFiles.read(directory(),map.id()+".png");
            return key(png).equals(fingerprint(map))?png:null;
        }catch(Exception failure){
            com.mojang.logging.LogUtils.getLogger().warn("Map data {} ignored: {}",map.id(),failure.getMessage());return null;
        }
    }
    private static void save(ArenaMap map,byte[] png){
        try{
            Files.createDirectories(directory());
            var tagged=tag(png,fingerprint(map));MapPreviewFiles.dimensions(tagged);
            var temp=directory().resolve(map.id()+".png.tmp");
            Files.write(temp,tagged);
            Files.move(temp,file(map.id()),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
        }catch(Exception failure){com.mojang.logging.LogUtils.getLogger().warn("Map data {} could not be saved",map.id(),failure);}
    }
    public static void request(ServerPlayer player,ArenaMap map){
        if(!CACHE.containsKey(map)){var saved=load(map);if(saved!=null)remember(map,saved);}
        if(CACHE.containsKey(map)){GungangNetwork.send(player,new GungangNetwork.MapPreview(map.id(),CACHE.get(map),""));return;}
        var job=JOBS.get(map);
        if(job==null){
            var level=player.server.getLevel(ResourceKey.create(Registries.DIMENSION,new ResourceLocation(map.dimension())));
            if(level==null||JOBS.size()>=8){GungangNetwork.send(player,new GungangNetwork.MapPreview(map.id(),new byte[0],"지도 생성 대기열이 가득 찼거나 차원이 없습니다. 잠시 후 다시 열어주세요."));return;}
            job=new Job(map,level);JOBS.put(map,job);
        }
        job.viewers.add(player.getUUID());
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||JOBS.isEmpty())return;
        var iterator=JOBS.entrySet().iterator();var entry=iterator.next();var job=entry.getValue();
        try{
            if(!job.step())return;
            byte[] png=job.finish();remember(entry.getKey(),png);
            // Unexplored chunks may load later; keep those maps in memory only so they are retried.
            if(!job.missing)save(job.map,png);
            for(var id:job.viewers){var player=job.level.getServer().getPlayerList().getPlayer(id);if(player!=null)GungangNetwork.send(player,new GungangNetwork.MapPreview(job.map.id(),png,""));}
        }catch(Exception failure){
            com.mojang.logging.LogUtils.getLogger().warn("Map generation failed: {}",job.map.id(),failure);
            for(var id:job.viewers){var player=job.level.getServer().getPlayerList().getPlayer(id);if(player!=null)GungangNetwork.send(player,new GungangNetwork.MapPreview(job.map.id(),new byte[0],"지도를 생성하지 못했습니다. 서버 로그를 확인하세요."));}
        }
        iterator.remove();
    }
    private static void remember(ArenaMap map,byte[] png){CACHE.put(map,png);while(CACHE.size()>8)CACHE.remove(CACHE.keySet().iterator().next());}
    public static void clear(){CACHE.clear();JOBS.clear();}
    /** Deletes saved map data (all maps when id is null) so the next preview request regenerates it. */
    public static int purge(String id)throws java.io.IOException{
        CACHE.keySet().removeIf(m->id==null||m.id().equals(id));JOBS.keySet().removeIf(m->id==null||m.id().equals(id));
        var dir=directory();if(!Files.isDirectory(dir))return 0;
        int removed=0;
        try(var paths=Files.list(dir)){
            for(var path:paths.toList()){
                String name=path.getFileName().toString();
                if(!Files.isRegularFile(path)||!(name.endsWith(".png")||name.endsWith(".png.tmp")))continue;
                if(id!=null&&!name.equals(id+".png")&&!name.equals(id+".png.tmp"))continue;
                Files.delete(path);if(name.endsWith(".png"))removed++;
            }
        }
        return removed;
    }
    @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppedEvent event){clear();}
    private MapPreviewGenerator(){}
}
