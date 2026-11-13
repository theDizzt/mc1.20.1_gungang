package dev.gungang.acoustics.server;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * A player's 4-digit tag ("#0427") shown after the nickname, so players with the same nickname can be told apart.
 * The digits are stored in the player DB ({@link PlayerRecord#tag()}), handed out once by {@link PlayerDatabase};
 * clients read them back from the Tab-list name. Drawn small in the GUNGANG font ({@code gungang_acoustics:gungang_tag}).
 */
public final class PlayerTag {
    public static final ResourceLocation FONT = new ResourceLocation("gungang_acoustics", "gungang_tag");
    /** A tag at the end of a name, as the Tab-list name carries it. */
    private static final java.util.regex.Pattern SUFFIX = java.util.regex.Pattern.compile("#([0-9]{4})$");
    static final int COUNT = 10000;

    /** The account's preferred tag: derived from the UUID, so a re-created record gets the same digits when free. */
    static int preferred(UUID id) {
        long mixed = id.getMostSignificantBits() * 0x9E3779B97F4A7C15L ^ id.getLeastSignificantBits();
        mixed ^= mixed >>> 31;
        return (int) Math.floorMod(mixed, (long) COUNT);
    }
    static String format(int value) { return String.format(Locale.ROOT, "%04d", value); }
    /**
     * A tag for a new record: the preferred one, else the next one not in {@code used}. Once all 10000 are taken,
     * tags repeat (the preferred one is returned).
     */
    static String allocate(UUID id, Set<String> used) {
        int start = preferred(id);
        for (int i = 0; i < COUNT; i++) { String tag = format((start + i) % COUNT); if (!used.contains(tag)) return tag; }
        return format(start);
    }
    /** "#0427", small and grey in the GUNGANG font; empty for a record without a tag. */
    public static MutableComponent component(String tag) {
        if (tag == null || tag.isEmpty()) return Component.empty();
        return Component.literal("#" + tag).withStyle(style -> style.withFont(FONT).withColor(ChatFormatting.GRAY));
    }
    /** {@code name} followed by the small tag. */
    public static MutableComponent named(String name, String tag) { return Component.literal(name).append(component(tag)); }
    /** The 4 digits at the end of {@code text} ("" when there are none). */
    public static String suffix(String text) { var m = SUFFIX.matcher(text); return m.find() ? m.group(1) : ""; }
    /** {@code text} without a trailing tag. */
    public static String strip(String text) { return SUFFIX.matcher(text).replaceFirst(""); }
    private PlayerTag() { }
}
