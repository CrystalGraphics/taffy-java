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

## 2. fastutil, replaced — `dev.vfyjxf.taffy.collection` (2026-09-10)

Upstream pulls **fastutil 8.5.12** for seven imports across two files, `tree/GridComputer` and
`tree/TaffyTree`: `IntList`/`IntArrayList`, `FloatList`/`FloatArrayList`,
`Int2FloatMap`/`Int2FloatOpenHashMap`, `Long2ObjectOpenHashMap`. fastutil is one artifact of 12,795
classes, and a jar that shades it pays all of it — measured in CrystalGUI's merged jar, **19.65 MB of
31.20, and 12,808 of 15,892 entries**. 63% of a mod, for seven collections.

**This fork no longer depends on fastutil at all.** The seven types are reimplemented in
`dev.vfyjxf.taffy.collection` under the same names and signatures, so the two files above changed
**only their import lines** and all ~80 call sites are untouched — which is what keeps the next
upstream diff readable. `build.gradle.kts` now declares no compile dependency whatsoever. The merged
jar went to **8.44 MB**.

### What it costs a reader of this fork

The replacements carry exactly the methods Taffy calls and no more — no `java.util.List` or `Map`
implementation, and no `remove` on `Int2FloatOpenHashMap`, because Taffy never removes from one. That
is deliberate: an unimplemented method is a compile error at the call site that wants it, which is the
right moment to decide whether the assumed semantics are the ones written here.

Two behaviours are silent when wrong, and both are pinned by `CollectionsTest`:

- **`Int2FloatMap.defaultReturnValue`** is what `get` answers for an absent key, and it defaults to
  `0f`, not `NaN`. `GridComputer` sets it to `NaN` for its fit-content limits precisely so a stored
  zero is distinguishable from a missing entry.
- **`Long2ObjectOpenHashMap.remove` shifts, it does not tombstone.** Linear probing ends a chain at
  the first free slot, so blanking a slot mid-chain strands every entry behind it: they are still in
  the table, `get` returns null, `size` is right, and nothing throws. The removal is Knuth 6.4
  algorithm R in fastutil's own formulation, and the test drives it against a `java.util.HashMap` over
  200,000 random operations followed by a sweep of the whole key space — because no hand-written case
  finds a stranded entry, and a random one finds it on the first bad shift.

### Covered by

`dev.vfyjxf.taffy.collection.CollectionsTest` (12 assertions' worth, differential against the JDK),
`:core:test --tests "com.crystalgui.ui.box.*"`, and `prodSmoke` — the four installed Minecraft clients,
which lay out a full workbench through `GridComputer` and photograph it.
