package dev.gungang.acoustics.client;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeathNoticeTest {
    @Test void distinguishesKillsFromEnvironmentalDeaths() {
        DeathNotice kill = DeathNotice.parse(Component.translatable("death.attack.player",
                Component.literal("Victim"), Component.literal("Killer")));
        assertEquals("Victim", kill.victim().getString());
        assertEquals("Killer", kill.killer().getString());
        DeathNotice fall = DeathNotice.parse(Component.translatable("death.fell.accident.generic", Component.literal("Victim")));
        assertNull(fall.killer());
    }

    @Test void bedLinkIsNotAKillerAndNestedMessagesAreRecognized() {
        Component bed = Component.translatable("death.attack.badRespawnPoint.message",
                Component.literal("Victim"), Component.literal("[Intentional Game Design]"));
        assertNull(DeathNotice.parse(bed).killer());
        assertEquals("Victim", DeathNotice.parse(Component.empty().append(bed)).victim().getString());
        assertNull(DeathNotice.parse(Component.literal("ordinary chat")));
        assertNull(DeathNotice.parse(Component.translatable("death.attack.generic")));
    }

    @Test void itemDeathsUseTheAttackerInsteadOfTheWeapon() {
        DeathNotice kill = DeathNotice.parse(Component.translatable("death.attack.player.item",
                Component.literal("Victim"), Component.literal("Killer"), Component.literal("Rifle")));
        assertEquals("Killer", kill.killer().getString());
    }
}
