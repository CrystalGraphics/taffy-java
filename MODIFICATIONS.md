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

## 2. `tree/FlexboxComputer.layoutAbsoluteChildren` — an unsized abspos child is measured intrinsically

Upstream measured every absolutely positioned child against a **definite** available space:

```java
LayoutOutput output = layoutComputer.performChildLayout(
    childId, knownDimensions, new FloatSize(insetRelativeWidth, insetRelativeHeight),
    new TaffySize<>(AvailableSpace.definite(containerSize.width),
                    AvailableSpace.definite(containerSize.height)),
    SizingMode.INHERENT_SIZE, new TaffyLine<>(false, false));
```

even on an axis where `knownDimensions` is NaN — i.e. exactly where the box is *shrink-to-fit* and the
measurement is supposed to be answering what its content wants. Such an axis is now measured at
`AvailableSpace.maxContent()`, and **only if that overruns** what the containing block leaves
(`shrinkToFitBound`) is it measured a second time against that bound — which is the call above,
unmodified.

### The governing invariant

**A shrink-to-fit box takes `min(max-content, available)`, and its subtree must know it is being
measured.** A definite available space says the opposite: the size is already decided. Nothing below
is ever in an intrinsic pass, so a descendant percentage resolves against the containing block — and
CSS Sizing §5 says a percentage against an indefinite containing block behaves as `auto` during
intrinsic sizing precisely to stop what happens next.

### The failure it causes

A `min-width: 100%` on any descendant becomes **self-fulfilling**: the child claims the whole of the
outer width, the shrink-to-fit ancestor takes that as its content width, and the percentage is then
satisfied by the size it caused. The declaration decides the ancestor rather than following it.

It needs **three** things at once and is invisible with any two — an out-of-flow ancestor with no
width, a scroll container under it, and a percentage minimum under that. Measured on the real tree,
with 300px of content in an 800px work area:

| shape | width |
|---|---|
| the percentage minimum on an ordinary child | 300 ✓ |
| a scroll container with no percentage minimum under it | 300 ✓ |
| both, in flow | 300 ✓ |
| both, under an out-of-flow ancestor | **800** ✗ |

Found as every window on CrystalGUI's desktop coming out exactly as wide as the work area whatever it
contained — a window's content slot is a scroll container whose viewport carries `min-width: 100%`, so
a caller's own `width: 100%` row cannot collapse. A window that spans the area still *looks* like a
window, and a full-width one centres at x=0, which is where an unplaced one sits anyway; it surfaced
only in the cascade, which cannot step a window that already fills the area.

### Why a second pass, rather than clamping the first

Shrink-to-fit is `min(max(min-content, available), max-content)`, and **all three terms are
load-bearing**. `AvailableSpace.definite(...)` used to supply the bound as a side effect of being
wrong, so removing it is what makes the bound explicit; but clamping the max-content answer to
`available` and stopping there implements `min(max-content, available)` and drops the `max(min-content,
…)`, which squeezes anything that cannot shrink. That is not hypothetical — it took the editor's fold
chip, whose own inset leaves it almost no room, to a third of its width at high zoom.

Re-measuring against the bound gets the missing term for free, because a definite available space is
the *right* answer once the box genuinely is being sized to it: what made it wrong in the first place
was being told the size was decided while it was still being computed. `shrinkToFitBound` is the
containing block less the insets and margins that are stated, and the result is taken **before** the
style's own min/max clamp so a declared `min-width` still wins — which is CSS's order.

The second pass runs only when the first overruns, so nothing that fits pays for it.

### Covered by

`TaffyPercentMinIntrinsicTest` in this module — the reported shape, the same through a scroll
container, and the three-part repro. Then three counter-assertions, because the others are all of the
form "must not be widened" and a fix that ignored percentage minima, one that measured unboundedly,
and one that clamped flat to `available` would each satisfy every one of them:

| test | what it forbids |
|---|---|
| `andStillFillsAParentWhoseWidthIsKnown` | ignoring percentage minima — one against a *known* width must still bind |
| `shrinkToFitStillDoesNotExceedTheContainingBlock` | measuring unboundedly — content that *can* shrink (a wrapping row: max-content 1600, min-content 400, available 800) must come down to the block |
| `andContentThatCannotShrinkIsNotSqueezedBelowIt` | clamping flat to `available` — the fold-chip case |

The middle one deliberately uses a wrapping row rather than a rigid box: CSS lets rigid content
overflow, so asserting on it would pin a clamp CSS does not have.

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
