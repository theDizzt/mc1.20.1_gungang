# UI font presets

The Fonts options page cycles between MaruMinya, Galmuri11 Bold
(default), and External TTF. Save reloads resources. External file selection is
only used by the External preset; invalid files fall back to MaruMinya.
Disabling custom fonts restores the game/resource-pack font.

Both bundled fonts use 1200 units/em and representative Hangul ink bounds of
1100 units. Galmuri hhea ascent/descent is 1200/-200; MaruMinya is 1200/0.
At Minecraft TTF size 9, their Hangul ink heights are therefore 7.0714 and
8.25 GUI pixels. MaruMinya uses size 9 × 1200/1400 = 7.7142857 (85.714%).
This preserves the legacy Hangul height and advance without scaling widgets.
The existing configurable [0, 1] shift remains unchanged.

MaruMinya's Latin glyphs are intrinsically narrower and shorter: A has
500×900 ink units versus Galmuri's 800×1100. A uniform scale cannot match
both scripts simultaneously; Hangul is the UI sizing reference. No horizontal
stretch is applied. Timer and dedicated numeric fonts are unchanged.

The MaruMinya preset adds a 0.35 GUI-pixel horizontal ink pass to default-font
text (including its shadow). This compensates its light single-pixel strokes
after downscaling, without changing text advances, wrapping or widget sizes.
It does not enable Minecraft's full 1-pixel bold spacing. Legacy, external and
dedicated HUD number/timer fonts do not receive this pass.

External TTFs retain size 9. Galmuri remains the missing-glyph fallback for
MaruMinya and external fonts. Existing external filenames are retained but
require selecting External TTF after this update.
