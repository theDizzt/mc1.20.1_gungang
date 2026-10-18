package dev.gungang.acoustics.server;

import com.google.gson.*;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/** Operator-only selection editor. JSON fields not being edited are preserved verbatim as data. */
public final class AreaEditor {
    private static final List<String> TYPES=List.of("map","alpha","bravo","deathzone","capture","attack","defense","bomb_a","bomb_b","escort","escort_alpha","escort_bravo","normandy","normandy_spawn","attack_launch","spectator");
    private record Change(String file,String before,String after){}
    @FunctionalInterface private interface Work{void run() throws Exception;}
    private static int run(CommandSourceStack source,Work action){
        try{action.run();return 1;}catch(Exception ex){source.sendFailure(Component.literal("영역 편집 실패: "+ex.getMessage()));return 0;}
    }
    public static void register(LiteralArgumentBuilder<CommandSourceStack> root){
        var area=Commands.literal("area").requires(s->s.hasPermission(2)).executes(c->{c.getSource().sendSuccess(()->Component.literal("/gungang area list [페이지] | wand | pos1/pos2 [x y z] | save <맵ID> <종류> [번호] | undo | info | clear | enable <맵ID> true/false"),false);return 1;});
        area.then(Commands.literal("list").executes(c->run(c.getSource(),()->list(c.getSource(),1)))
                .then(Commands.argument("page",IntegerArgumentType.integer(1)).executes(c->run(c.getSource(),()->list(c.getSource(),IntegerArgumentType.getInteger(c,"page"))))));
        area.then(Commands.literal("wand").executes(c->run(c.getSource(),()->{
            var p=c.getSource().getPlayerOrException();if(!p.getInventory().add(new ItemStack(GadgetItems.SPATIAL_WAND.get())))throw new IllegalStateException("인벤토리에 빈칸이 필요합니다.");
            p.inventoryMenu.broadcastChanges();p.sendSystemMessage(Component.literal("공간마법사: 좌클릭=첫 점, 우클릭=둘째 점. /gungang area save <맵ID> <종류>"));
        })));
        for(boolean first:new boolean[]{true,false})area.then(Commands.literal(first?"pos1":"pos2")
                .executes(c->run(c.getSource(),()->{var p=c.getSource().getPlayerOrException();SpatialWand.select(p,p.blockPosition(),first);}))
                .then(Commands.argument("position",BlockPosArgument.blockPos()).executes(c->run(c.getSource(),()->SpatialWand.select(c.getSource().getPlayerOrException(),BlockPosArgument.getBlockPos(c,"position"),first)))));
        area.then(Commands.literal("info").executes(c->run(c.getSource(),()->{var s=SpatialWand.selection(c.getSource().getPlayerOrException());c.getSource().sendSuccess(()->Component.literal(s.dimension()+" | "+s.first()+" → "+s.second()+" | "+SpatialWand.size(s.bounds())),false);})));
        area.then(Commands.literal("clear").executes(c->run(c.getSource(),()->{SpatialWand.clear(c.getSource().getPlayerOrException());c.getSource().sendSuccess(()->Component.literal("선택 영역을 지웠습니다. 저장된 맵은 유지됩니다."),false);})));
        area.then(Commands.literal("undo").executes(c->run(c.getSource(),()->undo(c.getSource().getPlayerOrException()))));
        area.then(Commands.literal("save").then(Commands.argument("map",StringArgumentType.word()).suggests(AreaEditor::suggestMaps)
                .then(Commands.argument("type",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(TYPES,b))
                        .executes(c->run(c.getSource(),()->save(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"map"),StringArgumentType.getString(c,"type"),0)))
                        .then(Commands.argument("index",IntegerArgumentType.integer(1,128)).executes(c->run(c.getSource(),()->save(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"map"),StringArgumentType.getString(c,"type"),IntegerArgumentType.getInteger(c,"index"))))))));
        area.then(Commands.literal("enable").then(Commands.argument("map",StringArgumentType.word()).suggests(AreaEditor::suggestMaps)
                .then(Commands.argument("enabled",BoolArgumentType.bool()).executes(c->run(c.getSource(),()->{
                    var p=c.getSource().getPlayerOrException();LobbyManager.requireMapEditing();Path path=find(StringArgumentType.getString(c,"map"));
                    if(!Files.exists(path))throw new IllegalArgumentException("먼저 영역을 저장하여 맵을 만드세요.");
                    String before=read(path);var json=JsonParser.parseString(before).getAsJsonObject();json.addProperty("enabled",BoolArgumentType.getBool(c,"enabled"));commit(p,path,before,json);
                })))));
        root.then(area);
    }
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestMaps(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c,com.mojang.brigadier.suggestion.SuggestionsBuilder b){
        try(var paths=Files.list(directory())){for(var p:paths.filter(AreaEditor::mapFile).toList()){try{b.suggest(JsonParser.parseString(read(p)).getAsJsonObject().get("id").getAsString());}catch(Exception ignored){}}}catch(Exception ignored){}
        return b.buildFuture();
    }
    private static void list(CommandSourceStack source,int page)throws Exception{
        List<Path> maps;
        if(!Files.exists(directory()))maps=List.of();
        else try(var paths=Files.list(directory())){maps=paths.filter(AreaEditor::mapFile).sorted().toList();}
        int pages=Math.max(1,(maps.size()+9)/10);
        if(page>pages)throw new IllegalArgumentException("페이지 범위: 1~"+pages);
        source.sendSuccess(()->Component.literal("맵 목록 (비활성 포함) · "+maps.size()+"개 · "+page+"/"+pages+" 페이지"),false);
        if(maps.isEmpty()){source.sendSuccess(()->Component.literal("등록된 맵 JSON 파일이 없습니다."),false);return;}
        for(var path:maps.subList((page-1)*10,Math.min(page*10,maps.size()))){
            Component line;
            try{
                var map=ServerFiles.GSON.fromJson(read(path),ArenaMap.class);
                if(map==null)throw new IllegalArgumentException("Empty map");map.validate();
                line=Component.literal(map.id()+" | "+map.name()+" | "+(map.enabled()?"활성":"비활성")+" | "+path.getFileName());
            }catch(Exception invalid){line=Component.literal("[설정 오류] "+path.getFileName()+" — JSON 내용을 확인하세요.");}
            final Component result=line;source.sendSuccess(()->result,false);
        }
        if(page<pages)source.sendSuccess(()->Component.literal("다음 페이지: /gungang area list "+(page+1)),false);
    }
    private static Path directory(){return ServerFiles.root().resolve("server/maps").toAbsolutePath().normalize();}
    private static boolean mapFile(Path p){return Files.isRegularFile(p)&&p.getFileName().toString().endsWith(".json");}
    private static Path find(String id)throws Exception{
        if(!id.matches("[a-z0-9_-]{1,48}"))throw new IllegalArgumentException("맵 ID는 영문 소문자/숫자/_/- 1~48자입니다.");
        Files.createDirectories(directory());Path found=null;
        try(var paths=Files.list(directory())){for(var p:paths.filter(AreaEditor::mapFile).toList()){
            var json=JsonParser.parseString(read(p)).getAsJsonObject();
            if(json.has("id")&&id.equals(json.get("id").getAsString())){if(found!=null)throw new IllegalArgumentException("중복 맵 ID: "+id);found=p;}
        }}
        if(found!=null)return found;Path path=directory().resolve(id+".json");
        if(Files.exists(path))throw new IllegalArgumentException("같은 파일명에 다른 ID의 맵이 있습니다.");return path;
    }
    private static String read(Path path)throws Exception{if(Files.size(path)>65536)throw new IllegalArgumentException("맵 파일은 64 KiB 이하만 지원합니다.");return Files.readString(path,StandardCharsets.UTF_8);}
    static JsonObject box(MapPreviewArea b,boolean inclusive){
        var j=new JsonObject();j.addProperty("minX",b.minX());j.addProperty("minY",b.minY());j.addProperty("minZ",b.minZ());
        j.addProperty("maxX",b.maxX()+(inclusive?0:1));j.addProperty("maxY",b.maxY()+(inclusive?0:1));j.addProperty("maxZ",b.maxZ()+(inclusive?0:1));return j;
    }
    private static JsonObject spawn(double x,double y,double z,float yaw,float pitch){return ServerFiles.GSON.toJsonTree(new ArenaMap.Spawn(x,y,z,yaw,pitch)).getAsJsonObject();}
    private static JsonObject safeSpawn(ServerPlayer p,MapPreviewArea b){
        b.validate();
        long volume=((long)b.maxX()-b.minX()+1)*((long)b.maxY()-b.minY()+1)*((long)b.maxZ()-b.minZ()+1);
        if(volume>2_000_000||b.maxY()-b.minY()<1)throw new IllegalArgumentException("스폰 영역은 높이 2블록 이상, 200만 블록 이하로 선택하세요.");
        var level=p.serverLevel();double cx=(b.minX()+b.maxX()+1.0)/2,cz=(b.minZ()+b.maxZ()+1.0)/2,best=Double.POSITIVE_INFINITY;BlockPos chosen=null;
        for(var pos:BlockPos.betweenClosed(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY()-1,b.maxZ())){
            double score=Math.pow(pos.getX()+.5-cx,2)+Math.pow(pos.getZ()+.5-cz,2)+Math.abs(pos.getY()-p.getY())*.001;
            if(score>=best||!level.hasChunkAt(pos)||!level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),net.minecraft.core.Direction.UP))continue;
            if(!level.getFluidState(pos).isEmpty()||!level.getFluidState(pos.above()).isEmpty())continue;
            if(!level.noCollision(new AABB(pos.getX()+.2,pos.getY(),pos.getZ()+.2,pos.getX()+.8,pos.getY()+1.8,pos.getZ()+.8)))continue;
            chosen=pos.immutable();best=score;
        }
        if(chosen==null)throw new IllegalArgumentException("선택 영역 안에 바닥과 머리 위 2칸이 확보된 스폰 위치가 없습니다. 발 위치부터 천장까지 선택하세요.");
        return spawn(chosen.getX()+.5,chosen.getY(),chosen.getZ()+.5,p.getYRot(),0);
    }
    private static JsonObject required(JsonObject map,String key){if(!map.has(key)||!map.get(key).isJsonObject())throw new IllegalArgumentException(key+" 설정이 있는 맵에서 사용하세요.");return map.getAsJsonObject(key);}
    static JsonObject fresh(String id,ServerPlayer p){
        var json=ServerFiles.GSON.toJsonTree(ArenaMap.example()).getAsJsonObject();json.addProperty("id",id);json.addProperty("name",id);json.addProperty("description","공간마법사로 생성한 맵");json.addProperty("dimension",p.level().dimension().location().toString());json.remove("capture");
        var at=spawn(p.getX(),p.getY(),p.getZ(),p.getYRot(),0);json.add("alphaSpawn",at.deepCopy());json.add("bravoSpawn",at.deepCopy());json.add("spectatorSpawn",at.deepCopy());
        var pos=p.blockPosition();var bounds=box(new MapPreviewArea(pos.getX()-2,pos.getY(),pos.getZ()-2,pos.getX()+2,pos.getY()+3,pos.getZ()+2),false);json.add("alphaArea",bounds.deepCopy());json.add("bravoArea",bounds.deepCopy());return json;
    }
    static void save(ServerPlayer p,String id,String type,int index)throws Exception{
        LobbyManager.requireMapEditing();if(!TYPES.contains(type))throw new IllegalArgumentException("지원하지 않는 종류: "+type);
        boolean indexed=Set.of("deathzone","escort_alpha","escort_bravo","normandy","normandy_spawn").contains(type);
        if(index!=0&&!indexed)throw new IllegalArgumentException(type+"에는 번호를 사용하지 않습니다.");
        var sel=SpatialWand.selection(p);var b=sel.bounds();Path path=find(id);String before=Files.exists(path)?read(path):null;
        var json=before==null?fresh(id,p):JsonParser.parseString(before).getAsJsonObject();
        if(!sel.dimension().equals(json.get("dimension").getAsString()))throw new IllegalArgumentException("맵과 선택 영역의 차원이 다릅니다.");
        switch(type){
            case "map" -> {b.validate();json.add("previewArea",box(b,true));json.add("spectatorSpawn",spawn((b.minX()+b.maxX()+1.0)/2,Math.min(p.level().getMaxBuildHeight()-2,b.maxY()+3),(b.minZ()+b.maxZ()+1.0)/2,p.getYRot(),45));}
            case "spectator" -> json.add("spectatorSpawn",spawn((b.minX()+b.maxX()+1.0)/2,(b.minY()+b.maxY()+1.0)/2,(b.minZ()+b.maxZ()+1.0)/2,p.getYRot(),p.getXRot()));
            case "alpha","bravo","attack","defense" -> {
                boolean bomb=json.has("bomb")&&!json.get("bomb").isJsonNull();String team=type;JsonObject target=json;
                if(type.equals("attack")||type.equals("defense")||bomb){target=required(json,"bomb");team=type.equals("alpha")?"attack":type.equals("bravo")?"defense":type;}
                target.add(team+"Area",box(b,false));target.add(team+"Spawn",safeSpawn(p,b));
            }
            case "deathzone" -> {var zones=json.has("deathZones")&&!json.get("deathZones").isJsonNull()?json.getAsJsonArray("deathZones"):new JsonArray();
                if(index==0||index==zones.size()+1)zones.add(box(b,false));else if(index<=zones.size())zones.set(index-1,box(b,false));else throw new IllegalArgumentException("데스존 번호는 기존 번호 또는 다음 번호여야 합니다.");json.add("deathZones",zones);}
            case "capture" -> {var cap=json.has("capture")&&!json.get("capture").isJsonNull()?json.getAsJsonObject("capture"):new JsonObject();
                if(!cap.has("name")){cap.addProperty("name","중앙 거점");cap.addProperty("winningScore",100);cap.addProperty("captureStepTicks",2);cap.addProperty("scoreIntervalTicks",30);}cap.add("area",box(b,false));json.add("capture",cap);}
            case "bomb_a","bomb_b" -> {b.validate();required(json,"bomb").add(type.equals("bomb_a")?"areaA":"areaB",box(b,true));}
            case "escort" -> {var rules=json.has("escort")&&!json.get("escort").isJsonNull()?json.getAsJsonObject("escort"):new JsonObject();rules.add("scanArea",box(b,true));json.add("escort",rules);if(before==null)json.addProperty("timeLimitSeconds",240);}
            case "escort_alpha","escort_bravo" -> {
                if(index<1||index>3)throw new IllegalArgumentException("체크포인트 번호 1~3을 지정하세요.");var escort=required(json,"escort");
                var points=escort.has("respawns")?escort.getAsJsonArray("respawns"):new JsonArray();JsonObject point=null;
                for(var entry:points)if(entry.getAsJsonObject().get("checkpoint").getAsInt()==index)point=entry.getAsJsonObject();
                if(point==null){point=new JsonObject();point.addProperty("checkpoint",index);points.add(point);}point.add(type.equals("escort_alpha")?"alpha":"bravo",safeSpawn(p,b));escort.add("respawns",points);
            }
            case "normandy","normandy_spawn" -> {
                var points=required(json,"normandy").getAsJsonArray("points");if(index<1||index>points.size())throw new IllegalArgumentException("거점 번호 1~4를 지정하세요.");var point=points.get(index-1).getAsJsonObject();
                if(type.equals("normandy_spawn"))point.add("attackSpawn",safeSpawn(p,b));else{
                    double radius=Math.min(b.maxX()-b.minX()+1.0,b.maxZ()-b.minZ()+1.0)/2;if(radius<1||radius>32)throw new IllegalArgumentException("노르망디 거점 반경은 1~32블록입니다.");
                    point.add("center",spawn((b.minX()+b.maxX()+1.0)/2,(b.minY()+b.maxY()+1.0)/2,(b.minZ()+b.maxZ()+1.0)/2,0,0));point.addProperty("radius",radius);
                }
            }
            case "attack_launch" -> required(json,"normandy").add("attackLaunch",safeSpawn(p,b));
            default -> throw new IllegalArgumentException(type);
        }
        commit(p,path,before,json);
        if(before==null)p.sendSystemMessage(Component.literal("새 맵은 비활성 상태입니다. 양 팀 스폰을 설정한 뒤 /gungang area enable "+id+" true 로 활성화하세요."));
    }
    private static Path historyPath(UUID id){return ServerFiles.root().resolve("editor-undo").resolve(id+".json");}
    private static List<Change> history(UUID id)throws Exception{
        var path=historyPath(id);if(!Files.exists(path))return new ArrayList<>();
        if(Files.size(path)>4_000_000)throw new IllegalStateException("되돌리기 기록이 너무 큽니다.");
        Change[] changes=ServerFiles.GSON.fromJson(Files.readString(path,StandardCharsets.UTF_8),Change[].class);
        return changes==null?new ArrayList<>():new ArrayList<>(Arrays.asList(changes));
    }
    static void atomicWrite(Path path,String text)throws Exception{
        Files.createDirectories(path.getParent());var temp=Files.createTempFile(path.getParent(),".area-",".tmp");
        try{Files.writeString(temp,text,StandardCharsets.UTF_8);try{Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temp);}
    }
    private static void restore(Path path,String before)throws Exception{if(before==null)Files.deleteIfExists(path);else atomicWrite(path,before);}
    private static void commit(ServerPlayer p,Path path,String before,JsonObject json)throws Exception{
        ServerFiles.GSON.fromJson(json,ArenaMap.class).validate();String after=ServerFiles.GSON.toJson(json)+"\n";
        if(after.getBytes(StandardCharsets.UTF_8).length>65536)throw new IllegalArgumentException("맵 파일은 64 KiB 이하만 지원합니다.");
        if(before!=null&&JsonParser.parseString(before).equals(json)){p.sendSystemMessage(Component.literal("이미 같은 설정입니다."));return;}
        var changes=history(p.getUUID());changes.add(new Change(path.getFileName().toString(),before,after));while(changes.size()>20)changes.remove(0);
        atomicWrite(path,after);
        try{LobbyManager.refreshEditedMaps();atomicWrite(historyPath(p.getUUID()),ServerFiles.GSON.toJson(changes));}
        catch(Exception failure){restore(path,before);try{LobbyManager.refreshEditedMaps();}catch(Exception secondary){failure.addSuppressed(secondary);}throw failure;}
        p.sendSystemMessage(Component.literal("맵 저장 완료: "+path.getFileName()+" | /gungang area undo 로 되돌리기"));
    }
    static void undo(ServerPlayer p)throws Exception{
        LobbyManager.requireMapEditing();var changes=history(p.getUUID());if(changes.isEmpty())throw new IllegalArgumentException("되돌릴 저장 기록이 없습니다.");var change=changes.get(changes.size()-1);
        Path path=directory().resolve(change.file).normalize();if(!path.getParent().equals(directory())||!path.getFileName().toString().endsWith(".json"))throw new IllegalArgumentException("잘못된 되돌리기 파일입니다.");
        if(!Files.exists(path)||!read(path).equals(change.after))throw new IllegalStateException("저장 후 다른 수정이 발견되어 덮어쓰지 않았습니다. 현재 파일을 확인하세요.");
        if(change.before!=null)ServerFiles.GSON.fromJson(change.before,ArenaMap.class).validate();
        restore(path,change.before);changes.remove(changes.size()-1);
        try{LobbyManager.refreshEditedMaps();atomicWrite(historyPath(p.getUUID()),ServerFiles.GSON.toJson(changes));}
        catch(Exception failure){atomicWrite(path,change.after);try{LobbyManager.refreshEditedMaps();}catch(Exception secondary){failure.addSuppressed(secondary);}throw failure;}
        p.sendSystemMessage(Component.literal("이전 상태로 되돌렸습니다: "+change.file));
    }
    private AreaEditor(){}
}
