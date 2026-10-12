package dev.gungang.acoustics.server;

import net.minecraft.ChatFormatting;

/** RGB colors shared by server configuration and client snapshots. */
public record TeamOptions(String alphaColor, String bravoColor, Integer respawnSeconds, DeathmatchRules deathmatch,ExperienceRules experience) {
    public TeamOptions(String alphaColor,String bravoColor,Integer respawnSeconds) {this(alphaColor,bravoColor,respawnSeconds,DeathmatchRules.defaults(),ExperienceRules.defaults());}
    public static TeamOptions defaults() { return new TeamOptions("#FF5555", "#5555FF", 5); }
    public TeamOptions {
        if(deathmatch==null)deathmatch=DeathmatchRules.defaults();
        if(experience==null)experience=ExperienceRules.defaults();
        alphaColor = checked(alphaColor, "#FF5555");
        bravoColor = checked(bravoColor, "#5555FF");
        if (respawnSeconds == null) respawnSeconds = 5;
        if (respawnSeconds < 1 || respawnSeconds > 300) throw new IllegalArgumentException("respawnSeconds must be between 1 and 300");
    }
    private static String checked(String value, String fallback) {
        if (value == null) return fallback;
        if (!value.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Team colors must use #RRGGBB: " + value);
        return value;
    }
    public int color(String team) {
        return 0xFF000000 | Integer.parseInt(team.equals("ALPHA") ? alphaColor.substring(1) : bravoColor.substring(1), 16);
    }
    public ChatFormatting formatting(String team) {
        int rgb = color(team), best = Integer.MAX_VALUE;
        ChatFormatting nearest = ChatFormatting.WHITE;
        for (var candidate : ChatFormatting.values()) {
            if (candidate.getColor() == null) continue;
            int other = candidate.getColor();
            int r = (rgb >> 16 & 255) - (other >> 16 & 255);
            int g = (rgb >> 8 & 255) - (other >> 8 & 255);
            int b = (rgb & 255) - (other & 255);
            int distance = r*r + g*g + b*b;
            if (distance < best) { best = distance; nearest = candidate; }
        }
        return nearest;
    }
}
