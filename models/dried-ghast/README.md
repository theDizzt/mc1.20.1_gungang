# Dried ghast reference assets

The `gungang:dried_ghast` block uses Minecraft Java Edition 1.21.6's final
hydration stage (3): a 10 × 10 × 10 body with six flat side/back tentacles.
The geometry, UVs, GUI transform and seven PNG textures come from Mojang's
official release client; only resource namespaces and the model parent are
adapted for Forge 1.20.1. These Minecraft assets belong to Mojang/Microsoft.

Source: https://piston-data.mojang.com/v1/objects/740a125b83dd3447feaa3c5e891ead7fbb21ae28/client.jar

Release notes: https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-6

Reimport from a locally downloaded release client:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/import_dried_ghast.ps1 -ClientJar path/to/client-1.21.6.jar
```

`make_prop139_resources.ps1` intentionally preserves these imported assets.
The existing 10 × 10 × 10 body collision shape and decorative, non-hatching
behavior are unchanged. The flat tentacles are decorative, matching the model.
