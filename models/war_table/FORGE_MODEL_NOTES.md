# Combat Table: 3×1×3 asset models

Orientation (view from above; Minecraft north is -Z):

```text
             WEST      CENTER      EAST
NORTH        NW        NC          NE
CENTER       CW        CC          CE
SOUTH        SW        SC          SE
```

Each model lives in `assets/combattable/models/block/` and references the embedded atlas at `assets/combattable/textures/block/combat_table_atlas.png`.
The default exported 3×3 model is centered at (0,0,0) in generic Blockbench coordinates, with x,z spanning -24 to +24 and y 0 to 16 (16 model units = 1 Minecraft block).

This download supplies **model assets only**. A Forge 1.20.1 mod still requires block registration, its nine part blockstates, 3×3 placement/removal logic, and collision/selection shapes. The nine resource JSON models are visual slices, not a working mod on their own.

All geometry fits a 48×16×48 envelope. Rotating the assembled structure is handled by blockstates or custom Java placement code.
