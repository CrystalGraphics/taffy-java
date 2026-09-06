# Modifications to `dev.vfyjxf:taffy`

MIT requires a statement of what was changed when a modified copy is distributed. This is that
statement. Upstream is <https://github.com/vfyjxf/taffy-java> (a pure-Java port of Rust
[Taffy](https://github.com/DioxusLabs/taffy)); the vendored baseline is the published sources of
**1.1.4**, unmodified except where listed below.

Every change is a diff against that baseline, kept small and annotated in place with a
`// CrystalGUI:` comment so it survives a future re-vendor. **Do not reformat these files** — a
whitespace pass would make the next upstream diff unreadable, which is the whole reason a vendored
dependency is worth less than a pinned one.

## Why vendored at all

The engine's leaf-measurement path (`MeasureFunc`) is unusable under `flex-wrap: wrap`, and that is
not a configuration we can avoid: it is the only way a row of widgets reflows. The defect is one line
inside the flexbox algorithm, so it can only be fixed here. `plan/engine-rewrite.md` D3 records the
decision; spike S1 is the measurement that justified it.

The package name is deliberately **left as `dev.vfyjxf.taffy`**. `mc1710` already declares Taffy as
`shadowImplementation`, which relocates it under `com.crystalgui.shadow.` in the shipped jar, so
another mod shipping stock 1.1.4 cannot win a classloader race against this fork — the hazard that
would otherwise make renaming compulsory. Keeping the name leaves 165 call sites across `core/` and
the harness untouched.

---

## 1. `tree/FlexboxComputer.determineHypotheticalCrossSize` — the `isWrap ? NaN` branch, deleted

Upstream line ~1469 read:

```java
float knownMain = isWrap ? NaN : (isRow ? item.targetSize.width : item.targetSize.height);
```

so an item's hypothetical **cross** size was measured against the *container's* inner main size
rather than its own flex-resolved main size, whenever the container had `flex-wrap: wrap`. The
`nowrap` path was correct. It is now unconditional, which is what upstream Rust Taffy does.

### The governing invariant

**A row whose items all fit on one line must lay out identically whether or not it is allowed to
wrap.** Upstream failed that for every shape whose cross size had to be *measured*, and only for
those — an item with a definite cross size never takes this path at all, which is why the defect
survived: it is invisible for every fixed-size widget in a layout.

### Two separate things broke, and neither announces itself

**A measured leaf reported a height for a width it was never given.** Measured on a 200px row holding
two `flex-grow: 1` leaves, each wrapping 300px of text:

| container | measure func told | leaf box | text needs |
|---|---|---|---|
| `nowrap` | `known.width = 100` → 3 lines | 100 × **30** | 30 ✓ |
| `wrap` (before) | `known.width = NaN`, falls back to available **200** → 2 lines | 100 × **20** | 30 ✗ |
| `wrap` (after) | `known.width = 100` → 3 lines | 100 × **30** | 30 ✓ |

The leaf's **width is correct either way** and only its height is short, so nothing about the box
looks wrong — the last line is simply clipped. That is what makes it expensive to find from a
screenshot.

**And aspect-ratio items — the case the branch was written for — collapsed.** Its comment claimed it
"prevents AR from inflating the hypothetical cross based on flex growth, keeping the container cross
based on pre-flex-growth AR values (matching browser behavior)". It does neither. Row heights for a
200px wrapping row, measured:

| shape (`aspect-ratio: 1`) | `nowrap` | `wrap` before | `wrap` after |
|---|---|---|---|
| one square, `flex-basis: 20` | 20 | **0** | 20 |
| one square, `flex-grow: 1` | 200 | **0** | 200 |
| two squares, `flex-grow: 1` | 100 | **0** | 100 |
| three fixed 80px squares (genuinely two lines) | 67 | 160 | 160 |
| a fixed-width square beside a plain item | 40 | 40 | 40 |

Four of five wrap shapes came out **zero-high**. The last row is the one that was never affected: a
definite `width` makes the cross definite through `maybeApplyAspectRatio`, so the measure path is
skipped. The two-line case is **unchanged** by the deletion — the fix does not stop wrapping working.

### Why deleting is right, rather than narrowing to aspect-ratio items

That narrowing was written first and rejected on the measurements above: it fixes the measured leaf
and leaves every aspect-ratio row zero-high, because those items *do* have aspect ratios and so keep
the branch. Three independent reasons agree on deletion:

1. **CSS Flexbox §9.4 step 7** determines the hypothetical cross size "by performing layout with the
   **used main size**" — the flex-resolved one. `resolveFlexibleLengths` runs immediately before this
   call (`FlexboxComputer` lines 281 and 287), so `targetSize` *is* the used main size.
2. **Upstream Rust Taffy** passes `child.target_size` unconditionally; there is no branch there.
3. **Measured**, per the tables above: after deletion every single-line shape agrees with `nowrap`,
   and the genuinely-wrapping shape is untouched.

### Covered by

`TaffyWrapMeasureTest` in this module — the measured-leaf case (asserting the *width handed to the
measure function*, not only the height that came out), the aspect-ratio case, and
`wrappingStillWraps()` as the counter-assertion, because every other assertion is of the form "wrap
must equal nowrap" and a fix that simply stopped wrapping would satisfy all of them. Mutation-checked:
restoring the `isWrap ? NaN` fails the first two and passes the third.

---

## Known, not done: fastutil

Taffy pulls **fastutil 8.5.12** for seven imports — `IntList`/`IntArrayList`,
`FloatList`/`FloatArrayList`, `Int2FloatMap`/`Int2FloatOpenHashMap`, `Long2ObjectOpenHashMap`. Because
`mc1710` shades it, the shipped mod jar carries **12,808 relocated fastutil entries, ~22 MB of a 48 MB
jar** — roughly half the download, for seven types.

Replacing them with primitive collections of our own is a large and cheap win now that the port is
ours. It is deliberately **not** done here: spike S1 is meant to stay small, and swapping a hash map
implementation inside a layout engine is a change that wants its own tests (`Int2FloatOpenHashMap`'s
default return value is the sort of thing that is silent when wrong). Recorded so it is not
rediscovered.
