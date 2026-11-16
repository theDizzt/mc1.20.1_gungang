package dev.gungang.acoustics.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Built-in weapon definitions ({@code /gungang/defaults/<file>}) written once when no definition holds their ID yet. */
final class DefaultWeapons {
    static void ensure(Path directory,String id,String file)throws IOException{
        try(var files=Files.list(directory)){
            for(var path:files.filter(p->p.getFileName().toString().endsWith(".json")).toList())try{
                var json=com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                if(json.has("id")&&json.get("id").getAsString().equals(id))return;
            }catch(RuntimeException invalid){/* The normal catalog reader reports this file. */}
        }
        try(var input=DefaultWeapons.class.getResourceAsStream("/gungang/defaults/"+file)){
            if(input==null)throw new IOException("Missing weapon definition "+file);
            ConfigLayout.writeNew(directory.resolve(file),new String(input.readAllBytes(),StandardCharsets.UTF_8));
        }
    }
    private DefaultWeapons(){}
}
