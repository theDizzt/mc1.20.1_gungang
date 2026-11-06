package dev.gungang.acoustics.server;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * A TaCZ gun-pack data file ({@code data/<ns>/data/guns/<id>_data.json}) edited in place: only the numeric values
 * are replaced, so the pack author's comments, ordering and formatting survive a live patch.
 */
public final class TaczGunFile {
    /** One primitive value in the file: its dotted path, raw source text and source span. */
    public record Value(String path, String text, int start, int end) {
        public boolean number() { return text.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"); }
    }
    /** Patchable TaCZ fields with their Korean label, integer flag and accepted range. */
    public record Field(String path, String label, boolean integer, double min, double max) { }
    public static final List<Field> FIELDS = List.of(
            new Field("bullet.damage", "기본 피해", false, 0, 10000),
            new Field("rpm", "연사 속도 (RPM)", true, 1, 6000),
            new Field("ammo_amount", "탄창 용량", true, 1, Integer.MAX_VALUE),
            new Field("bullet.bullet_amount", "발당 탄환 수 (산탄)", true, 1, 256),
            new Field("bullet.pierce", "관통 수", true, 0, 1000),
            new Field("bullet.speed", "탄속", false, 0, 10000),
            new Field("bullet.life", "탄 수명 (초)", false, 0, 3600),
            new Field("bullet.extra_damage.head_shot_multiplier", "헤드샷 배수", false, 0, 100),
            new Field("bullet.extra_damage.armor_ignore", "방어 관통률 (0~1)", false, 0, 1),
            new Field("weight", "무게 (kg, 1kg당 이동속도 -1.5%)", false, -100, 1000),
            new Field("movement_speed.base", "이동속도 보정 · 기본", false, -1, 5),
            new Field("movement_speed.aim", "이동속도 보정 · 조준", false, -1, 5),
            new Field("movement_speed.reload", "이동속도 보정 · 재장전", false, -1, 5),
            new Field("aim_time", "조준 시간 (초)", false, 0, 60),
            new Field("draw_time", "꺼내기 시간 (초)", false, 0, 60),
            new Field("put_away_time", "넣기 시간 (초)", false, 0, 60),
            new Field("sprint_time", "달리기 후 사격 지연 (초)", false, 0, 60),
            new Field("reload.feed.empty", "재장전 · 탄 없음 (초)", false, 0, 120),
            new Field("reload.feed.tactical", "재장전 · 전술 (초)", false, 0, 120),
            new Field("reload.cooldown.empty", "재장전 종료 · 탄 없음 (초)", false, 0, 120),
            new Field("reload.cooldown.tactical", "재장전 종료 · 전술 (초)", false, 0, 120),
            new Field("inaccuracy.stand", "탄퍼짐 · 서기", false, 0, 100),
            new Field("inaccuracy.move", "탄퍼짐 · 이동", false, 0, 100),
            new Field("inaccuracy.sneak", "탄퍼짐 · 웅크리기", false, 0, 100),
            new Field("inaccuracy.lie", "탄퍼짐 · 엎드리기", false, 0, 100),
            new Field("inaccuracy.aim", "탄퍼짐 · 조준", false, 0, 100));
    private static final String ADJUST = "bullet.extra_damage.damage_adjust.";
    private static final int LIMIT = 1 << 20, ADJUST_ROWS = 40;

    private final Path path;
    private final String source;
    private final Map<String, Value> values;
    private TaczGunFile(Path path, String source) {
        this.path = path; this.source = source; this.values = scan(source);
    }
    public Path path() { return path; }
    public Map<String, Value> values() { return values; }

    /** The data file of a TaCZ gun id such as {@code rfp:m2hb}, searched in the server's unpacked gun packs. */
    public static TaczGunFile find(String gunId) throws java.io.IOException {
        if (gunId == null || !gunId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("TaCZ 총기 ID가 없는 무기입니다.");
        String ns = gunId.substring(0, gunId.indexOf(':')), id = gunId.substring(gunId.indexOf(':') + 1);
        Path packs = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("tacz").toAbsolutePath().normalize();
        if (!Files.isDirectory(packs)) throw new IllegalArgumentException("서버 tacz 폴더를 찾을 수 없습니다.");
        List<Path> roots;
        try (var list = Files.list(packs)) { roots = list.filter(Files::isDirectory).sorted().toList(); }
        for (Path pack : roots) {
            Path index = pack.resolve("data").resolve(ns).resolve("index/guns").resolve(id + ".json");
            if (!Files.isRegularFile(index)) continue;
            String data = ns + ":" + id + "_data";
            try {
                var json = JsonParser.parseString(read(index)).getAsJsonObject();
                if (json.has("data")) data = json.get("data").getAsString();
            } catch (RuntimeException ignored) { }
            if (!data.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) continue;
            String dns = data.substring(0, data.indexOf(':')), did = data.substring(data.indexOf(':') + 1);
            for (Path candidate : List.of(pack.resolve("data").resolve(dns).resolve("data/guns").resolve(did + ".json"),
                    pack.resolve("data").resolve(dns).resolve("data").resolve(did + ".json"))) {
                Path file = candidate.toAbsolutePath().normalize();
                if (file.startsWith(packs) && Files.isRegularFile(file)) return new TaczGunFile(file, read(file));
            }
        }
        throw new IllegalArgumentException("압축되지 않은 총기팩에서 " + gunId + " 데이터 파일을 찾을 수 없습니다.");
    }
    public static TaczGunFile parse(Path path, String source) { return new TaczGunFile(path, source); }
    private static String read(Path path) throws java.io.IOException {
        if (Files.size(path) > LIMIT) throw new IllegalArgumentException("총기 데이터 파일이 너무 큽니다: " + path.getFileName());
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /** Fields shown in the patch screen: known numeric settings, then each distance band of the damage curve. */
    public Map<String, Field> editable() {
        var result = new LinkedHashMap<String, Field>();
        for (var field : FIELDS) { var v = values.get(field.path()); if (v != null && v.number()) result.put(field.path(), field); }
        for (int i = 0; i < ADJUST_ROWS; i++) {
            var damage = values.get(ADJUST + i + ".damage");
            if (damage == null) break;
            if (!damage.number()) continue;
            var distance = values.get(ADJUST + i + ".distance");
            String reach = distance == null ? "?" : distance.number() ? distance.text() + "블록까지" : "그 이후";
            result.put(damage.path(), new Field(damage.path(), "거리별 피해 · " + reach, false, 0, 10000));
        }
        return result;
    }
    /** The file text with the given values replaced; every value is checked against its field before anything is written. */
    public String patched(Map<String, String> changes) {
        var fields = editable();
        var edits = new ArrayList<Value>();
        var replacement = new HashMap<Value, String>();
        for (var change : changes.entrySet()) {
            var field = fields.get(change.getKey());
            if (field == null) throw new IllegalArgumentException("수정할 수 없는 TaCZ 항목: " + change.getKey());
            String text = change.getValue().strip();
            double number;
            try {
                var exact = new java.math.BigDecimal(text);
                if (field.integer()) exact.intValueExact();
                number = exact.doubleValue();
            } catch (ArithmeticException | NumberFormatException invalid) {
                throw new IllegalArgumentException(field.label() + ": " + (field.integer() ? "정수" : "숫자") + "를 입력하세요.");
            }
            if (!Double.isFinite(number) || number < field.min() || number > field.max())
                throw new IllegalArgumentException(field.label() + ": " + format(field.min()) + "~" + format(field.max()) + " 범위로 입력하세요.");
            var value = values.get(field.path());
            if (new java.math.BigDecimal(value.text()).compareTo(new java.math.BigDecimal(text)) == 0) continue; // 0.0 stays 0.0
            edits.add(value); replacement.put(value, canonical(text));
        }
        edits.sort(Comparator.comparingInt(Value::start).reversed());
        var out = new StringBuilder(source);
        for (var value : edits) out.replace(value.start(), value.end(), replacement.get(value));
        String result = out.toString();
        try { JsonParser.parseString(result); } // the edited file must still parse the way TaCZ reads it
        catch (RuntimeException malformed) { throw new IllegalArgumentException("수정 후 총기 데이터 JSON이 올바르지 않습니다: " + malformed.getMessage()); }
        return result;
    }
    private static String canonical(String text) { return new java.math.BigDecimal(text).stripTrailingZeros().toPlainString(); }
    static String format(double value) { return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value); }

    // ---- Scanner: lenient JSON (comments, trailing commas) with source spans ----------------------------------

    static Map<String, Value> scan(String text) {
        var out = new LinkedHashMap<String, Value>();
        var scanner = new Scanner(text, out);
        scanner.skip();
        if (scanner.pos < text.length()) scanner.value("", 0);
        return out;
    }
    private static final class Scanner {
        final String s; final Map<String, Value> out; int pos;
        Scanner(String s, Map<String, Value> out) { this.s = s; this.out = out; }
        void skip() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (Character.isWhitespace(c) || c == '﻿') pos++;
                else if (s.startsWith("//", pos) || c == '#') { while (pos < s.length() && s.charAt(pos) != '\n') pos++; }
                else if (s.startsWith("/*", pos)) { int end = s.indexOf("*/", pos + 2); pos = end < 0 ? s.length() : end + 2; }
                else return;
            }
        }
        IllegalArgumentException error() { return new IllegalArgumentException("총기 데이터 JSON을 읽을 수 없습니다 (위치 " + pos + ")."); }
        void expect(char c) { skip(); if (pos >= s.length() || s.charAt(pos) != c) throw error(); pos++; }
        static String join(String prefix, String key) { return prefix.isEmpty() ? key : prefix + "." + key; }
        void value(String path, int depth) {
            if (depth > 32) throw error();
            skip();
            if (pos >= s.length()) throw error();
            char c = s.charAt(pos);
            if (c == '{') {
                pos++;
                while (true) {
                    skip();
                    if (pos < s.length() && s.charAt(pos) == '}') { pos++; return; }
                    String key = key();
                    expect(':');
                    value(join(path, key), depth + 1);
                    skip();
                    if (pos < s.length() && s.charAt(pos) == ',') { pos++; continue; }
                    expect('}'); return;
                }
            }
            if (c == '[') {
                pos++;
                for (int i = 0; ; i++) {
                    skip();
                    if (pos < s.length() && s.charAt(pos) == ']') { pos++; return; }
                    value(join(path, Integer.toString(i)), depth + 1);
                    skip();
                    if (pos < s.length() && s.charAt(pos) == ',') { pos++; continue; }
                    expect(']'); return;
                }
            }
            int start = pos;
            if (c == '"' || c == '\'') string(); else bare();
            if (pos == start) throw error();
            out.put(path, new Value(path, s.substring(start, pos), start, pos));
        }
        String key() {
            skip();
            int start = pos;
            if (pos < s.length() && (s.charAt(pos) == '"' || s.charAt(pos) == '\'')) {
                string();
                return JsonParser.parseString(s.substring(start, pos).replace('\'', '"')).getAsString();
            }
            bare();
            if (pos == start) throw error();
            return s.substring(start, pos);
        }
        void string() {
            char quote = s.charAt(pos++);
            while (pos < s.length()) {
                char c = s.charAt(pos++);
                if (c == '\\') pos++;
                else if (c == quote) return;
            }
            throw error();
        }
        void bare() {
            while (pos < s.length() && ",:{}[]/#".indexOf(s.charAt(pos)) < 0 && !Character.isWhitespace(s.charAt(pos))) pos++;
        }
    }
}
