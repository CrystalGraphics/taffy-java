package dev.vfyjxf.taffy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import dev.vfyjxf.taffy.geometry.FloatSize;
import dev.vfyjxf.taffy.geometry.TaffySize;
import dev.vfyjxf.taffy.style.AvailableSpace;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.FlexWrap;
import dev.vfyjxf.taffy.style.TaffyDimension;
import dev.vfyjxf.taffy.style.TaffyStyle;
import dev.vfyjxf.taffy.tree.NodeId;
import dev.vfyjxf.taffy.tree.TaffyTree;
import org.junit.Test;

/**
 * <b>CrystalGUI's</b> regression test for modification #1 — the deleted {@code isWrap ? NaN} branch in
 * {@code FlexboxComputer.determineHypotheticalCrossSize}. See {@code taffy/MODIFICATIONS.md}.
 *
 * <p>Upstream measured an item's cross size against the <em>container's</em> inner main size instead of
 * the item's own flex-resolved one whenever the container wrapped. Two separate things broke, and
 * neither announces itself:</p>
 *
 * <ul>
 *   <li>a <b>measured leaf</b> reported a height for a width it was never given — its width stayed
 *       correct and only its height was short, so the symptom is clipped text;</li>
 *   <li>an <b>aspect-ratio item</b>, which is the case the branch was written for, <em>collapsed</em> —
 *       a single square in a wrapping row came out zero-high.</li>
 * </ul>
 *
 * <p>The governing invariant, and what most of these assertions are: <b>a row whose items all fit on one
 * line must lay out identically whether or not it is allowed to wrap.</b> Upstream failed that for every
 * shape whose cross size had to be measured. {@link #wrappingStillWraps()} is the counter-assertion —
 * without it, a "fix" that disabled wrapping altogether would pass everything above it.</p>
 */
public class TaffyWrapMeasureTest {

    private static final float ROW_WIDTH = 200f;
    /** Advance of the "text" every measured leaf here holds, on one line. */
    private static final float TEXT_ADVANCE = 300f;
    private static final float LINE_HEIGHT = 10f;

    /** What each measure call was told, so a failure names the number that arrived, not just the result. */
    private final List<Float> widthsMeasuredAt = new ArrayList<>();

    // ---------------------------------------------------------------- fixtures

    private static TaffyStyle row(FlexWrap wrap) {
        TaffyStyle style = new TaffyStyle();
        style.flexDirection = FlexDirection.ROW;
        style.flexWrap = wrap;
        style.size = new TaffySize<>(TaffyDimension.length(ROW_WIDTH), TaffyDimension.AUTO);
        return style;
    }

    /** min-width: 0 — without it CSS's automatic minimum floors an item at its min-content size. */
    private static TaffySize<TaffyDimension> noAutomaticMinimum() {
        return new TaffySize<>(TaffyDimension.length(0), TaffyDimension.AUTO);
    }

    private static TaffyStyle grower() {
        TaffyStyle style = new TaffyStyle();
        style.flexGrow = 1f;
        style.flexBasis = TaffyDimension.length(0);
        style.minSize = noAutomaticMinimum();
        return style;
    }

    /** Wraps {@link #TEXT_ADVANCE} of text at whatever width it is handed, and records that width. */
    private FloatSize measureText(FloatSize known, TaffySize<AvailableSpace> available) {
        float width = known.width;
        if (Float.isNaN(width)) {
            width = available.width.isDefinite() ? available.width.getValue() : TEXT_ADVANCE;
        }
        widthsMeasuredAt.add(width);
        float lines = Math.max(1f, (float) Math.ceil(TEXT_ADVANCE / Math.max(1f, width)));
        return new FloatSize(width, lines * LINE_HEIGHT);
    }

    private static void layOut(TaffyTree tree, NodeId root) {
        tree.computeLayout(root, new TaffySize<>(AvailableSpace.definite(ROW_WIDTH), AvailableSpace.MAX_CONTENT));
    }

    // ---------------------------------------------------------------- a measured leaf

    /** Two equal measured leaves in the row; answers the height they settled at. */
    private float measuredLeafHeightIn(FlexWrap wrap) {
        widthsMeasuredAt.clear();
        TaffyTree tree = new TaffyTree();
        NodeId first = tree.newLeafWithMeasure(grower(), this::measureText);
        NodeId second = tree.newLeafWithMeasure(grower(), this::measureText);
        NodeId root = tree.newWithChildren(row(wrap), first, second);
        layOut(tree, root);

        assertEquals("the leaf's MAIN size is right in both cases -- that is what hides this defect",
                100f, tree.getLayout(first).size().width, 0.01f);
        return tree.getLayout(first).size().height;
    }

    @Test
    public void aWrappingContainerMeasuresALeafAtItsOwnWidth() {
        // Control. The nowrap path was never broken: 300px of text at 100px a line is three lines.
        float nowrap = measuredLeafHeightIn(FlexWrap.NO_WRAP);
        assertEquals("control", 30f, nowrap, 0.01f);

        float wrapped = measuredLeafHeightIn(FlexWrap.WRAP);

        // Asserted on the INPUT as well as the output: a height that comes out right for the wrong
        // reason is exactly what this test exists to catch.
        for (float width : widthsMeasuredAt) {
            assertEquals("a wrapping container must measure a leaf at ITS width, never the container's",
                    100f, width, 0.01f);
        }
        assertEquals("wrapping must not change what a leaf measures to", nowrap, wrapped, 0.01f);
    }

    // ---------------------------------------------------------------- an aspect-ratio item

    /** One square item in the row; answers the row's resulting height. */
    private float squareRowHeight(FlexWrap wrap, boolean grow) {
        TaffyTree tree = new TaffyTree();
        TaffyStyle square = new TaffyStyle();
        square.aspectRatio = 1f;
        square.flexGrow = grow ? 1f : 0f;
        square.flexBasis = TaffyDimension.length(20f);
        square.minSize = noAutomaticMinimum();

        NodeId root = tree.newWithChildren(row(wrap), tree.newLeaf(square));
        layOut(tree, root);
        return tree.getLayout(root).size().height;
    }

    @Test
    public void aWrappingContainerDoesNotCollapseAnAspectRatioItem() {
        // The branch that broke this was written FOR aspect-ratio items, and this is what it did to
        // them: upstream answered 0 for both shapes.
        assertEquals("a 20px square must make the row 20 high whether or not the row may wrap",
                squareRowHeight(FlexWrap.NO_WRAP, false), squareRowHeight(FlexWrap.WRAP, false), 0.01f);
        assertEquals("...and a grown square, whose used main size is the whole row",
                squareRowHeight(FlexWrap.NO_WRAP, true), squareRowHeight(FlexWrap.WRAP, true), 0.01f);

        // Pinned absolutely too, so a regression that broke BOTH sides equally cannot pass the pair above.
        assertEquals(20f, squareRowHeight(FlexWrap.WRAP, false), 0.01f);
        assertEquals(200f, squareRowHeight(FlexWrap.WRAP, true), 0.01f);
    }

    // ---------------------------------------------------------------- the counter-assertion

    @Test
    public void wrappingStillWraps() {
        // Three 80px squares in a 200px row: two on the first line, one on the second, so 160 high.
        // Every assertion above is of the form "wrap must equal nowrap", which a fix that simply
        // stopped wrapping would satisfy completely.
        TaffyTree tree = new TaffyTree();
        List<NodeId> items = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            TaffyStyle square = new TaffyStyle();
            square.aspectRatio = 1f;
            square.size = new TaffySize<>(TaffyDimension.length(80f), TaffyDimension.AUTO);
            square.minSize = noAutomaticMinimum();
            items.add(tree.newLeaf(square));
        }
        NodeId root = tree.newWithChildren(row(FlexWrap.WRAP), items.toArray(new NodeId[0]));
        layOut(tree, root);

        assertEquals("two lines of 80px squares", 160f, tree.getLayout(root).size().height, 0.01f);
        assertTrue("the third square must be on the second line",
                tree.getLayout(items.get(2)).location().y > 0f);
    }
}
