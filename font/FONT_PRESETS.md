# UI font presets

The Fonts options page cycles between GUNGANG (default), Galmuri11 Bold and
External TTF. Save reloads resources. External file selection is only used by
the External preset; invalid files fall back to GUNGANG. Disabling custom fonts
restores the game/resource-pack font.

## GUNGANG (`gungang_hangul.ttf`)

Built from `gungang.ttf` (Latin, digits, symbols; 2048 units/em) and
`Mulmaru.ttf` (Hangul and everything GUNGANG lacks; 192 units/em):

- GUNGANG glyphs are kept unchanged; Mulmaru only fills missing codepoints.
- Mulmaru is scaled by 1592 / 176 = 9.04545 so its Hangul ink height (11 px)
  equals the GUNGANG capital A (1592 units, 7 GUNGANG pixels).
- Hangul (syllables and jamo) is lifted onto the baseline so it spans 0..1592
  exactly like A. Other Mulmaru glyphs keep Mulmaru's baseline.
- Letter spacing: Mulmaru's 1 px trailing gap is replaced by GUNGANG's gap
  (advance 1364 - ink width 1137 = 227 units).
- Line metrics are GUNGANG's (hhea 1592/-455); the Windows clip box is widened
  to cover all ink.

At Minecraft TTF size 9 the STB pixel scale is 9 / (1592 + 455), so one GUNGANG
pixel is one GUI pixel and both A and Hangul are 7 GUI pixels high. Hangul
strokes are thinner than Latin ones because a scaled Mulmaru pixel is about
145 units versus GUNGANG's 227; matching the heights makes this unavoidable.

Mulmaru is SIL OFL 1.1, so the merged font is distributed under OFL 1.1 with
both copyright notices kept in its name table.

## Galmuri11 Bold

Size 9, 1200 units/em, hhea 1200/-200. It is also the missing-glyph fallback
for GUNGANG and external fonts.

## History

u216-u327 had a MaruMinya preset (size 9 x 6/7 with a 0.35 GUI-pixel ink pass)
and Galmuri11 Bold as default. u329 removed MaruMinya and renamed the stored
value `GALMURI_LEGACY` to `GALMURI`, so old configs start on GUNGANG.

External TTFs retain size 9. Timer and dedicated numeric fonts are unchanged.
