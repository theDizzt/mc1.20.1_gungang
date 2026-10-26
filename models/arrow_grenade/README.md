# 화살분열 수류탄 (ID 79)

Model: **Athom grenade** by **BadVibes4EVER**, licensed under CC BY 4.0.
Source: https://sketchfab.com/3d-models/athom-grenade-c6e43eb37da349e2a5d91ec90c52e167
Author: https://sketchfab.com/BadVibes4EVER

Converted from the supplied `athom_grenade.zip`: baked node transforms, normalized scale,
converted glTF triangles to OBJ, and resized base-color textures to at most 2048 pixels.
Metallic/roughness and normal maps are not used by Minecraft's standard item renderer.
Original attribution is packaged in `META-INF/licenses/arrow_grenade.txt`.
Reimport with `scripts/import_arrow_grenade.py <archive>` (numpy and Pillow required).

Default gameplay: cooldown 30 seconds from throw, fixed base damage 16 per arrow,
6-block arrow travel, 6 seconds of emission starting on impact, 20 random-direction
arrows per second. Arrows stop at blocks; normal armor and team damage rules apply.
Use the offhand-use-and-return macro (default H), or right-click while holding in the main hand.
Fragments also hit bodies overlapping their spawn point, including the thrower.
Entity collision uses a 0.45-block margin around the target hitbox (vanilla arrows: 0.3).
Server settings are generated under the existing gadget configuration as `arrow_grenade`.
