package dev.gungang.acoustics.server;

import com.google.gson.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;

/** Typed, validated edits of existing server configuration, with rollback on reload failure. */
public final class ConfigModify {
    private static final List<String> KINDS=List.of("map","weapon","secondary","item","block");
    private record Target(String kind,String id,Path path,JsonObject original,JsonObject effective,Map<String,Class<?>> fields){}
    @FunctionalInterface private interface Work{void run()throws Exception;}
    private static int run(CommandSourceStack source,Work work){
        try{work.run();return 1;}catch(Exception ex){source.sendFailure(Component.literal("설정 편집 실패: "+ex.getMessage()));return 0;}
    }
    public static void register(LiteralArgumentBuilder<CommandSourceStack> root){
        var modify=Commands.literal("modify").requires(s->s.hasPermission(2)).executes(c->{say(c.getSource(),"/gungang modify <map|weapon|secondary|item|block> [ID] [항목] [값] · 생략하면 목록/현재값 조회");return 1;});
        for(String kind:KINDS)modify.then(Commands.literal(kind)
                .executes(c->run(c.getSource(),()->list(c.getSource(),kind)))
                .then(Commands.argument("id",StringArgumentType.word()).suggests((c,b)->suggestIds(kind,b))
                        .executes(c->run(c.getSource(),()->show(c.getSource(),target(kind,StringArgumentType.getString(c,"id")))))
                        .then(Commands.argument("field",StringArgumentType.word()).suggests((c,b)->suggestFields(kind,c,b))
                                .executes(c->run(c.getSource(),()->get(c.getSource(),target(kind,StringArgumentType.getString(c,"id")),StringArgumentType.getString(c,"field"))))
                                .then(Commands.argument("value",StringArgumentType.greedyString()).suggests((c,b)->suggestValue(kind,c,b))
                                        .executes(c->run(c.getSource(),()->edit(c.getSource(),kind,StringArgumentType.getString(c,"id"),StringArgumentType.getString(c,"field"),StringArgumentType.getString(c,"value"))))))));
        root.then(modify);
    }
    private static void say(CommandSourceStack source,String text){source.sendSuccess(()->Component.literal(text),false);}
    private static Map<String,Object> settings(String kind){
        var result=new TreeMap<String,Object>();
        if(kind.equals("item")){
            result.put("landmine",GadgetConfig.mine);result.put("recovery_potion",GadgetConfig.recovery);result.put("adrenaline",GadgetConfig.adrenaline);result.put("barrier",GadgetConfig.barrier);
            result.put("molotov",ExtraGadgetConfig.molotov);result.put("orbital_strike",ExtraGadgetConfig.orbital);result.put("life_support",ExtraGadgetConfig.life);result.put("jetpack",ExtraGadgetConfig.jet);result.put("infrared_scanner",ExtraGadgetConfig.scanner);result.put("shield",ExtraGadgetConfig.shield);
        }else if(kind.equals("block")){
            result.put("small_health_pack",GadgetConfig.smallHealthPack);result.put("large_health_pack",GadgetConfig.largeHealthPack);
            result.put("uxo",GimmickConfig.uxo);result.put("gas_barrel",GimmickConfig.gas);result.put("puddle",GimmickConfig.puddle);result.put("acid",GimmickConfig.acid);result.put("nozzle",GimmickConfig.nozzle);result.put("acid_nozzle",GimmickConfig.acidNozzle);result.putAll(GimmickConfig.walls);
        }
        return result;
    }
    private static Path directory(String kind){return ServerFiles.root().resolve(switch(kind){case "map"->"server/maps";case "weapon"->"server/weapons";case "secondary"->"server/sub_weapons";default->"item";}).toAbsolutePath().normalize();}
    private static JsonObject read(Path path)throws Exception{
        if(Files.size(path)>65536)throw new IllegalArgumentException("64 KiB를 넘는 설정 파일: "+path.getFileName());
        return JsonParser.parseString(Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private static Map<String,Path> ids(String kind)throws Exception{
        var result=new TreeMap<String,Path>();
        if(kind.equals("item")||kind.equals("block")){settings(kind).keySet().forEach(id->result.put(id,directory(kind).resolve(id+".json")));return result;}
        if(!Files.exists(directory(kind)))return result;
        try(var files=Files.list(directory(kind))){for(var path:files.filter(p->Files.isRegularFile(p)&&p.getFileName().toString().endsWith(".json")).sorted().toList()){
            var json=read(path);if(!json.has("id"))throw new IllegalArgumentException("ID가 없는 파일: "+path.getFileName());String id=json.get("id").getAsString();
            if(result.putIfAbsent(id,path)!=null)throw new IllegalArgumentException("중복 ID: "+id);
        }}return result;
    }
    private static void list(CommandSourceStack source,String kind)throws Exception{
        var values=ids(kind);say(source,kind+" 설정 ID ("+values.size()+"개)");
        var keys=new ArrayList<>(values.keySet());for(int i=0;i<keys.size();i+=10)say(source,String.join(", ",keys.subList(i,Math.min(i+10,keys.size()))));
        if(keys.isEmpty())say(source,"등록된 설정이 없습니다.");
    }
    private static Target target(String kind,String id)throws Exception{
        if(!id.matches("[a-z0-9_-]{1,64}"))throw new IllegalArgumentException("잘못된 설정 ID");
        Path path=ids(kind).get(id);if(path==null)throw new IllegalArgumentException("해당 ID가 없습니다. /gungang modify "+kind+" 로 확인하세요.");
        JsonObject original=Files.exists(path)?read(path):new JsonObject();Object model;JsonObject effective;
        if(kind.equals("item")||kind.equals("block")){
            var defaults=settings(kind).get(id);effective=ServerFiles.GSON.toJsonTree(defaults).getAsJsonObject();original.entrySet().forEach(e->effective.add(e.getKey(),e.getValue().deepCopy()));model=defaults;
        }else{
            model=kind.equals("map")?ServerFiles.GSON.fromJson(original,ArenaMap.class):ServerFiles.GSON.fromJson(original,Loadouts.Weapon.class);
            effective=ServerFiles.GSON.toJsonTree(model).getAsJsonObject();
        }
        var fields=new TreeMap<String,Class<?>>();fields(model.getClass(),effective,"",fields,0);fields.remove("id");
        // Executable command hooks remain in the JSON editor, not in this value editor.
        fields.remove("command");fields.keySet().removeIf(key->key.startsWith("onPrepare.")||key.startsWith("onStart.")||key.startsWith("onEnd."));
        if(model instanceof ArenaMap map&&map.bomb()!=null){
            fields.keySet().removeIf(key->key.startsWith("alphaSpawn.")||key.startsWith("bravoSpawn.")||key.startsWith("alphaArea.")||key.startsWith("bravoArea.")
                    ||map.bomb().areaA()!=null&&key.startsWith("bomb.pointA.")||map.bomb().areaB()!=null&&key.startsWith("bomb.pointB."));
        }
        if(kind.equals("secondary")&&(id.equals("98")||id.equals("99"))){
            var defaults=BombEquipment.example(id.equals("99"));for(String key:List.of("plantSeconds","fuseSeconds","defuseSeconds"))if(defaults.has(key)){
                fields.put(key,Double.class);effective.add(key,original.has(key)?original.get(key).deepCopy():defaults.get(key));
            }
        }
        return new Target(kind,id,path,original,effective,fields);
    }
    private static void fields(Type type,JsonElement value,String prefix,Map<String,Class<?>> out,int depth){
        if(depth>12)return;
        if(type instanceof ParameterizedType generic&&generic.getRawType()==List.class){
            if(value!=null&&value.isJsonArray())for(int i=0;i<value.getAsJsonArray().size();i++)fields(generic.getActualTypeArguments()[0],value.getAsJsonArray().get(i),prefix+"."+i,out,depth+1);return;
        }
        if(!(type instanceof Class<?> cls))return;
        if(cls.isRecord()){
            // Optional mode objects must be created by the map editor or a map template first.
            if(depth>0&&(value==null||value.isJsonNull()))return;
            for(var field:cls.getRecordComponents()){
                String key=field.getName();fields(field.getGenericType(),value!=null&&value.isJsonObject()?value.getAsJsonObject().get(key):null,prefix.isEmpty()?key:prefix+"."+key,out,depth+1);
            }
        }else if(cls==String.class||cls==boolean.class||cls==Boolean.class||Number.class.isAssignableFrom(cls)||cls.isPrimitive())out.put(prefix,cls);
    }
    private static void show(CommandSourceStack source,Target target){
        say(source,target.kind+" "+target.id+" · "+target.path.getFileName()+" · 항목을 입력하면 현재값 조회, 이어서 값을 입력하면 저장");
        var keys=new ArrayList<>(target.fields.keySet());for(int i=0;i<keys.size();i+=8)say(source,String.join(", ",keys.subList(i,Math.min(i+8,keys.size()))));
    }
    static JsonElement value(JsonElement root,String field){
        JsonElement node=root;for(String part:field.split("\\.")){
            if(node==null||node.isJsonNull())return JsonNull.INSTANCE;
            node=node.isJsonArray()?node.getAsJsonArray().get(Integer.parseInt(part)):node.getAsJsonObject().get(part);
        }return node==null?JsonNull.INSTANCE:node;
    }
    private static Class<?> fieldType(Target target,String field){var type=target.fields.get(field);if(type==null)throw new IllegalArgumentException("지원하지 않는 항목입니다. 항목 이름을 TAB으로 확인하세요.");return type;}
    private static void get(CommandSourceStack source,Target target,String field){fieldType(target,field);say(source,target.kind+" "+target.id+" "+field+" = "+value(target.effective,field));}
    static JsonPrimitive parse(String text,Class<?> type){
        if(type==String.class){
            String result=text;
            if(text.startsWith("\"")){var parsed=JsonParser.parseString(text);if(!parsed.isJsonPrimitive()||!parsed.getAsJsonPrimitive().isString())throw new IllegalArgumentException("문자열을 입력하세요.");result=parsed.getAsString();}
            if(result.chars().anyMatch(c->c==0))throw new IllegalArgumentException("문자열에 NUL 문자를 사용할 수 없습니다.");return new JsonPrimitive(result);
        }
        if(type==boolean.class||type==Boolean.class){if(!text.equals("true")&&!text.equals("false"))throw new IllegalArgumentException("true 또는 false를 입력하세요.");return new JsonPrimitive(Boolean.parseBoolean(text));}
        try{
            var number=new java.math.BigDecimal(text);
            if(type==int.class||type==Integer.class)return new JsonPrimitive(number.intValueExact());
            if(type==long.class||type==Long.class)return new JsonPrimitive(number.longValueExact());
            double n=number.doubleValue();if(!Double.isFinite(n)||(type==float.class||type==Float.class)&&!Float.isFinite((float)n))throw new NumberFormatException();return new JsonPrimitive(number);
        }catch(ArithmeticException|NumberFormatException ex){throw new IllegalArgumentException("유효한 "+((type==int.class||type==Integer.class)?"정수":"숫자")+"를 입력하세요.");}
    }
    static void set(JsonObject original,JsonObject effective,String field,JsonElement replacement){
        String[] parts=field.split("\\.");JsonElement node=original,defaults=effective;
        for(int i=0;i<parts.length-1;i++){
            String part=parts[i];JsonElement next;
            if(node.isJsonArray()){int index=Integer.parseInt(part);next=node.getAsJsonArray().get(index);defaults=defaults.getAsJsonArray().get(index);}
            else{var obj=node.getAsJsonObject();defaults=defaults.getAsJsonObject().get(part);next=obj.get(part);
                if(next==null||next.isJsonNull()){next=defaults.deepCopy();obj.add(part,next);}}
            node=next;
        }
        String last=parts[parts.length-1];if(node.isJsonArray())node.getAsJsonArray().set(Integer.parseInt(last),replacement);else node.getAsJsonObject().add(last,replacement);
        if(field.equals("itemNbt"))original.remove("nbt");
        if(field.equals("previewImage"))original.remove("preview");
    }
    private static void validate(Target target,JsonObject json)throws Exception{
        if(target.kind.equals("map"))ServerFiles.GSON.fromJson(json,ArenaMap.class).validate();
        else if(target.kind.equals("weapon")||target.kind.equals("secondary")){
            ServerFiles.GSON.fromJson(json,Loadouts.Weapon.class).validate(target.kind.equals("weapon"));
            if(target.kind.equals("secondary"))for(String key:List.of("plantSeconds","fuseSeconds","defuseSeconds"))if(json.has(key))GadgetConfig.bounded(json.get(key).getAsDouble(),.05,key.equals("fuseSeconds")?3600:300);
        }else GadgetConfig.validate(target.id,json);
    }
    static void edit(CommandSourceStack source,String kind,String id,String field,String text)throws Exception{
        LobbyManager.requireMapEditing();var target=target(kind,id);var parsed=parse(text,fieldType(target,field));var candidate=target.original.deepCopy();set(candidate,target.effective,field,parsed);validate(target,candidate);
        String after=ServerFiles.GSON.toJson(candidate)+"\n";if(after.getBytes(StandardCharsets.UTF_8).length>65536)throw new IllegalArgumentException("설정 파일은 64 KiB 이하여야 합니다.");
        if(value(target.effective,field).equals(parsed)){say(source,"이미 같은 값입니다.");return;}
        String before=Files.exists(target.path)?Files.readString(target.path,StandardCharsets.UTF_8):null;
        AreaEditor.atomicWrite(target.path,after);
        try{if(kind.equals("map"))LobbyManager.refreshEditedMaps();else LobbyManager.refreshModifiedSettings();}
        catch(Exception failure){
            if(before==null)Files.deleteIfExists(target.path);else AreaEditor.atomicWrite(target.path,before);
            try{if(kind.equals("map"))LobbyManager.refreshEditedMaps();else LobbyManager.refreshModifiedSettings();}catch(Exception rollback){failure.addSuppressed(rollback);}
            throw failure;
        }
        say(source,"저장 완료: "+kind+" "+id+" · "+field+" = "+parsed+" ("+target.path.getFileName()+")");
        if(field.startsWith("specs."))say(source,"specs는 무기 선택 화면의 표시용 정보입니다. 실제 총기 성능은 총기팩에서 설정합니다.");
        if(kind.equals("weapon")||kind.equals("secondary"))say(source,"변경된 장비 설정은 다음 장비 지급/재선택부터 적용됩니다. 내장 아이템 효과·쿨타임은 modify item 설정이 우선합니다.");
    }
    private static CompletableFuture<Suggestions> suggestIds(String kind,SuggestionsBuilder b){try{return SharedSuggestionProvider.suggest(ids(kind).keySet(),b);}catch(Exception ignored){return b.buildFuture();}}
    private static CompletableFuture<Suggestions> suggestFields(String kind,CommandContext<CommandSourceStack> c,SuggestionsBuilder b){try{return SharedSuggestionProvider.suggest(target(kind,StringArgumentType.getString(c,"id")).fields.keySet(),b);}catch(Exception ignored){return b.buildFuture();}}
    private static CompletableFuture<Suggestions> suggestValue(String kind,CommandContext<CommandSourceStack> c,SuggestionsBuilder b){
        try{var target=target(kind,StringArgumentType.getString(c,"id"));String field=StringArgumentType.getString(c,"field");Class<?> type=fieldType(target,field);
            if(type==boolean.class||type==Boolean.class)return SharedSuggestionProvider.suggest(List.of("true","false"),b);
            var current=value(target.effective,field);if(!current.isJsonNull())return SharedSuggestionProvider.suggest(List.of(current.toString()),b);
        }catch(Exception ignored){}return b.buildFuture();
    }
    private ConfigModify(){}
}
