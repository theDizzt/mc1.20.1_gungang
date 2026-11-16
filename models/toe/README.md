# 종료의화신: 발가락 (39)

Source: user-provided `mecha-boost.zip`, `source/model.gltf` and `textures/gltf_embedded_0.png`.

`scripts/import_toe_model.mjs` imports only meshes **3, 4, 5**: shoe shell, trim and sole from one foot. Mesh 2 (bare leg), the other leg, head, torso and arms are excluded. Character pose transforms are removed so the shoe follows each Minecraft leg independently. The generated OBJ keeps the original UVs and texture.

The item model is hidden in both hands and rendered on both feet in third person. Its GUI model remains visible in weapon selection. The spike mesh is generated separately by `ToeTrailRenderer` and uses the owner's two effect colours.
