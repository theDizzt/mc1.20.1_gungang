package dev.gungang.acoustics.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Keep retired definitions as editable backups outside the active category directories. */
final class LoadoutRetirements {
    static void migrate(Path root)throws IOException{
        try(var files=Files.list(root.resolve("sub_weapons"))){
            for(var file:files.filter(p->p.getFileName().toString().endsWith(".json")).toList()){
                var json=definition(file);
                if(json==null||!json.has("id")||!java.util.Set.of("83","89").contains(json.get("id").getAsString()))continue;
                var archive=root.resolve("dummy/sub_weapons");Files.createDirectories(archive);
                var target=archive.resolve(file.getFileName());
                for(int suffix=1;Files.exists(target);suffix++)target=archive.resolve(file.getFileName()+".backup-"+suffix);
                Files.move(file,target);
            }
        }
        try(var files=Files.list(root.resolve("weapons"))){
            for(var file:files.filter(p->p.getFileName().toString().endsWith(".json")).toList()){
                var json=definition(file);
                if(json==null||!json.has("id")||!json.get("id").getAsString().equals("39")
                        ||!json.has("itemNbt")||!json.get("itemNbt").getAsString().contains("gungang:magic_book"))continue;
                json.addProperty("id","37");
                Files.writeString(file,ServerFiles.GSON.toJson(json));
                var target=file.resolveSibling("37_magic-book.json");
                if(!file.equals(target)&&!Files.exists(target))Files.move(file,target);
            }
        }
    }
    private static com.google.gson.JsonObject definition(Path file)throws IOException{
        if(!Files.isRegularFile(file)||Files.size(file)>65536)return null;
        try{
            var json=ServerFiles.GSON.fromJson(Files.readString(file),com.google.gson.JsonObject.class);
            if(json==null||!json.has("id")||!json.get("id").isJsonPrimitive()
                    ||json.has("itemNbt")&&!json.get("itemNbt").isJsonPrimitive())return null;
            return json;
        }catch(RuntimeException invalid){return null;} // The normal loader reports malformed definitions.
    }
    private LoadoutRetirements(){}
}
