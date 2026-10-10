package dev.gungang.acoustics.client;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;

public final class ExternalFonts {
    public static Path prepareDirectory(Path config) throws IOException {
        Path directory = config.resolve("gungang/fonts");
        Files.createDirectories(directory);
        Path legacy = config.resolve("fonts");
        Path marker = directory.resolve(".legacy-imported");
        if (!Files.exists(marker) && Files.isDirectory(legacy)) {
            try (var files = Files.list(legacy)) {
                for (Path source : files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ttf")).toList()) {
                    Path target = directory.resolve(source.getFileName());
                    if (!Files.exists(target)) Files.copy(source, target);
                }
            }
            Files.writeString(marker, "Existing fonts copied without deleting or overwriting files.");
        }
        return directory;
    }
    public static String filename(String value) {
        String name = value.trim();
        if (!name.isEmpty() && (!name.toLowerCase(Locale.ROOT).endsWith(".ttf")
                || name.matches(".*[\\\\/:*?\"<>|\\p{Cntrl}].*"))) {
            throw new IllegalArgumentException("Use a .ttf filename inside config/gungang/fonts");
        }
        return name;
    }

    /** Empty selection automatically picks the first TTF in filename order. */
    public static byte[] load(Path directory, String selection) throws IOException {
        Files.createDirectories(directory);
        String name = filename(selection);
        Path file;
        if (name.isEmpty()) {
            try (var files = Files.list(directory)) {
                file = files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ttf"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .findFirst().orElse(null);
            }
            if (file == null) return null;
        } else {
            file = directory.resolve(name);
        }
        byte[] data = Files.readAllBytes(file);
        try {
            Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(data));
        } catch (FontFormatException invalid) {
            throw new IOException("Invalid TTF: " + file.getFileName(), invalid);
        }
        return data;
    }

    private ExternalFonts() { }
}
