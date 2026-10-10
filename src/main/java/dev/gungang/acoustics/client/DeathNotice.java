package dev.gungang.acoustics.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.contents.TranslatableContents;

public record DeathNotice(Component victim, Component killer) {
    public static DeathNotice parse(Component text) {
        if (text.getContents() instanceof TranslatableContents translated
                && translated.getKey().startsWith("death.")) {
            Object[] args = translated.getArgs();
            if (args.length > 0) {
                Component victim = component(args[0]);
                Component killer = null;
                if (args.length > 1) {
                    Component candidate = component(args[1]);
                    HoverEvent hover = candidate.getStyle().getHoverEvent();
                    // Do not mistake a bed link, location or weapon for a killer.
                    if (hover != null && hover.getValue(HoverEvent.Action.SHOW_ENTITY) != null) killer = candidate;
                    else if (translated.getKey().endsWith(".player") || translated.getKey().endsWith(".item")
                            || translated.getKey().equals("death.attack.player")
                            || translated.getKey().equals("death.attack.mob")
                            || translated.getKey().equals("death.attack.arrow")
                            || translated.getKey().equals("death.attack.trident")) killer = candidate;
                }
                return new DeathNotice(victim, killer);
            }
        }
        for (Component sibling : text.getSiblings()) {
            DeathNotice notice = parse(sibling);
            if (notice != null) return notice;
        }
        return null;
    }

    private static Component component(Object value) {
        return value instanceof Component text ? text.copy() : Component.literal(String.valueOf(value));
    }
}
