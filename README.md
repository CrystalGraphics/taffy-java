# taffy-java — CrystalGraphics fork

A fork of **[vfyjxf/taffy-java](https://github.com/vfyjxf/taffy-java)**, a pure-Java port of Rust
[Taffy](https://github.com/DioxusLabs/taffy) (Flexbox, Grid and Block layout).

This is the layout engine behind [CrystalGUI](https://github.com/CrystalGraphics/CrystalGUI), carried
here as a fork rather than consumed as a Maven artifact so that defects in it can be fixed. MIT, like
upstream — see [`LICENSE`](LICENSE), which is upstream's own notice, unchanged.

**[`MODIFICATIONS.md`](MODIFICATIONS.md) is the list of what differs from upstream**, and the statement
of changes MIT requires. Read it before touching anything here.

## What is different from upstream

The vendored baseline is the published sources of **`dev.vfyjxf:taffy:1.1.4`**, unmodified except for
what `MODIFICATIONS.md` records. Today that is one change: the deletion of a branch in
`FlexboxComputer.determineHypotheticalCrossSize` that measured a flex item's cross size against its
**container's** main size rather than its own, whenever the container had `flex-wrap: wrap`.

Two separate things broke, and neither announces itself:

- a leaf with a `MeasureFunc` reported a height for a width it was never given — its *width* stayed
  correct, so the symptom is clipped text rather than a box that looks wrong;
- an item with an `aspect-ratio` — the case the branch was written for — **collapsed to zero height**.

`TaffyWrapMeasureTest` covers both, plus the counter-assertion that wrapping still wraps.

## Working on it

**This repository does not build on its own, by design.** It carries a `build.gradle.kts` and no
settings file or wrapper: it is a git submodule of
[CrystalGUI](https://github.com/CrystalGraphics/CrystalGUI), included from that project's
`settings.gradle.kts` as `:taffy`, exactly as `gl-debug-harness` is. So:

```bash
git clone --recursive https://github.com/CrystalGraphics/CrystalGUI
cd CrystalGUI
./gradlew :taffy:test        # this module's regression tests
```

Upstream's own build, tests and `gentest` tooling are deliberately not carried here — that tooling
generates a conformance suite against Rust Taffy's fixtures, which is upstream's job and not something
a consumer fork should be re-running. What this fork tests is what this fork changed.

**Keep changes minimal and annotate them in place with a `// CrystalGUI:` comment.** Do not reformat
these files — a whitespace pass makes the next diff against upstream unreadable, which is the whole
reason a vendored dependency is worth less than a pinned one.
