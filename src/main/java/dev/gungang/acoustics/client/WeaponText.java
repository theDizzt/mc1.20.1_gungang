package dev.gungang.acoustics.client;

import java.util.List;

/** Normalize only display text, never item SNBT. Keep empty paragraphs when wrapping. */
final class WeaponText {
    static List<String> lines(String text) {
        if (text == null) return List.of("");
        String normalized=text.replace("\\r\\n","\n").replace("\\n","\n").replace("\\r","\n")
                .replace("\r\n","\n").replace('\r','\n');
        return java.util.Arrays.asList(normalized.split("\n",-1));
    }
    private WeaponText() {}
}
