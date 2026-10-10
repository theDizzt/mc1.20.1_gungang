package dev.gungang.acoustics.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Shared non-overwriting config file creation. */
public final class ConfigLayout {
    public static void writeNew(Path path,String text) throws IOException {
        Files.createDirectories(path.getParent());
        if(!Files.exists(path))Files.writeString(path,text,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
    }
    private ConfigLayout() {}
}
