/**
 * <b>CrystalGUI: the seven fastutil types Taffy used, reimplemented — so the dependency can go.</b>
 *
 * <p>Upstream Taffy names seven fastutil types across two files, {@code GridComputer} and
 * {@code TaffyTree}. fastutil is one artifact of 12,795 classes and 58 MB, and a jar that shades it
 * pays all of it: measured in CrystalGUI's merged jar it was <b>19.65 MB of 31.20</b>, 12,808 of
 * 15,892 entries — 63% of a mod, for seven collections. On MC 1.20.x the game supplies fastutil and
 * a mod can simply use it; on 1.7.10 there is none, and an unrelocated copy is a split package
 * against Minecraft's own module on Forge and NeoForge, which is fatal before a mod class loads.
 * Shading a relocated copy was the only way to serve all four loaders, and this removes the need.</p>
 *
 * <p>Same names, same signatures, same package-relative shape as the originals, so the two files that
 * use them changed <b>only their import lines</b> and all ~80 call sites are untouched — which is
 * what keeps the next upstream diff readable. See {@code MODIFICATIONS.md}.</p>
 *
 * <pre>{@code
 * FloatList sizes = new FloatArrayList();
 * sizes.add(12.5f);
 * float first = sizes.getFloat(0);
 * for (float size : sizes) { ... }          // Iterable<Float>, so enhanced-for unboxes
 *
 * Int2FloatMap limits = new Int2FloatOpenHashMap();
 * limits.defaultReturnValue(Float.NaN);      // what get() answers for an absent key
 * limits.put(3, 40f);
 * }</pre>
 *
 * <h2>These are not general-purpose collections</h2>
 *
 * <p>Each carries exactly the methods Taffy calls and nothing else — no {@code java.util.List} or
 * {@code Map} implementation, no {@code remove} where Taffy never removes. That is deliberate: an
 * unimplemented method is a <em>compile error</em> at the call site that adds it, which is the right
 * moment to decide whether the semantics being assumed are the ones written here. Do not treat this
 * package as a fastutil substitute anywhere else.</p>
 *
 * <p>The one behaviour easy to get wrong when extending them: {@link
 * dev.vfyjxf.taffy.collection.Int2FloatMap#defaultReturnValue(float)} sets what {@code get} answers
 * for a key that is absent, and it defaults to {@code 0f} — not {@code NaN}. Taffy's fit-content
 * limits rely on setting it to {@code NaN} explicitly.</p>
 */
package dev.vfyjxf.taffy.collection;
