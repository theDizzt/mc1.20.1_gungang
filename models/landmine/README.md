# Landmine / secondary weapon 86

Source: user-supplied `landmine.glb`. Embedded attribution: Landmine by FeelsBadMan, CC-BY-4.0. Full attribution is shipped in `META-INF/licenses/landmine.txt`.

Run `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/import_landmine.ps1` to regenerate the OBJ, MTL and original base-color texture. The importer verifies the supplied model's transform chain and preserves all 368 triangles. Normal, metallic and emissive maps are not used by the standard Forge item renderer.

The former installed pressure plate had a visible width/depth of `14/16 * 0.7 = 0.6125` blocks and a height of `1/16 * 1.2 = 0.075` blocks. The new model uses the same footprint and preserves its proportions (height `0.0755744` blocks). Its bottom is at the installation surface. The original 0.7 x 0.15 x 0.7 entity hitbox and gameplay rules are unchanged.

Item and deployed rendering share the same baked OBJ. `gradlew runClient -PgungangLandmineSmoke` checks model/texture loading and captures a same-scale comparison in `build/ui-smoke/landmine-model.png`.
