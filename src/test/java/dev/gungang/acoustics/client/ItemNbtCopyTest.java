package dev.gungang.acoustics.client;

import com.google.gson.JsonParser;
import java.nio.file.*;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemNbtCopyTest {
    @Test void amkJsonEscapesDecodeBeforeSnbtParsing() throws Exception {
        try(var stream=getClass().getResourceAsStream("/nbt/amk.json")) {
            assertNotNull(stream);
            var json=JsonParser.parseString(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            String snbt=json.get("itemNbt").getAsString();
            assertTrue(snbt.startsWith("{"));
            assertFalse(snbt.contains("\\\""));
            var stack=TagParser.parseTag(snbt);
            assertEquals("tacz:modern_kinetic_gun",stack.getString("id"));
            assertEquals("tacz:ak47",stack.getCompound("tag").getString("GunId"));
            assertEquals("tacz:sight_okp7",stack.getCompound("tag").getCompound("AttachmentSCOPE").getCompound("tag").getString("AttachmentId"));
        }
    }
    @Test void realServerTaczItemsPreserveAllNestedNbtThroughClipboardText() throws Exception {
        var examples=JsonParser.parseString(Files.readString(Path.of("docs/server-examples/reference-weapons.json"))).getAsJsonArray();
        assertEquals(3,examples.size());
        boolean testedAttachment=false;
        for(var example:examples) {
            var original=TagParser.parseTag(example.getAsJsonObject().get("itemNbt").getAsString());
            var restored=TagParser.parseTag(original.toString());
            assertEquals(original,restored); // Includes NBT value types, not just displayed strings.
            assertEquals("tacz:modern_kinetic_gun",restored.getString("id"));
            var tag=restored.getCompound("tag");
            assertTrue(tag.contains("GunId"));assertTrue(tag.contains("GunFireMode"));
            assertTrue(tag.contains("GunCurrentAmmoCount"));assertTrue(tag.contains("HasBulletInBarrel"));
            if(tag.contains("AttachmentSCOPE")) {
                assertEquals("tacz:scope_hamr",tag.getCompound("AttachmentSCOPE").getCompound("tag").getString("AttachmentId"));
                testedAttachment=true;
            }
        }
        assertTrue(testedAttachment);
    }
    @Test void unknownAddonTagsAndTypedArraysAreNotFiltered() throws Exception {
        var tag=TagParser.parseTag("{id:'tacz:modern_kinetic_gun',Count:1b,tag:{custom:{tuning:[1.5f,2.5f],skin:'addon:custom',bytes:[B;1b,-1b],longs:[L;1L,2L]}},ForgeCaps:{'addon:state':{value:7s}}}");
        assertEquals(tag,TagParser.parseTag(tag.toString()));
    }
}
