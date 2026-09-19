package dev.vfyjxf.taffy;

import static org.junit.Assert.assertEquals;

import dev.vfyjxf.taffy.geometry.TaffySize;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.AvailableSpace;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyDimension;
import dev.vfyjxf.taffy.style.TaffyStyle;
import dev.vfyjxf.taffy.tree.NodeId;
import dev.vfyjxf.taffy.tree.TaffyTree;
import org.junit.Test;

/**
 * <b>CrystalGUI's</b> regression test: {@code max-width: max-content} caps a flex item at its natural width.
 *
 * <p>An equal share of a row that stops at each item's natural width is {@code flex: 1 1 0} plus that cap, and it is
 * what a squeezed tab strip is. Upstream resolved the keyword to no maximum at all, so the items stretched to fill
 * the row whatever they held.</p>
 */
public class TaffyMaxContentMaxSizeTest {

    private static TaffyStyle row(float width) {
        TaffyStyle style = new TaffyStyle();
        style.flexDirection = FlexDirection.ROW;
        style.alignItems = AlignItems.FLEX_START;
        style.size = new TaffySize<>(TaffyDimension.length(width), TaffyDimension.length(20f));
        return style;
    }

    /** An equal share, capped at the content's width: {@code flex: 1 1 0; max-width: max-content}. */
    private static TaffyStyle share() {
        TaffyStyle style = new TaffyStyle();
        style.flexGrow = 1f;
        style.flexShrink = 1f;
        style.flexBasis = TaffyDimension.length(0f);
        // ZERO, as CrystalGUI states it: CSS's automatic minimum would stop an item at its content.
        style.minSize = new TaffySize<>(TaffyDimension.length(0f), TaffyDimension.auto());
        style.maxSize = new TaffySize<>(TaffyDimension.maxContent(), TaffyDimension.auto());
        return style;
    }

    private static TaffyStyle fixed(float width) {
        TaffyStyle style = new TaffyStyle();
        style.size = new TaffySize<>(TaffyDimension.length(width), TaffyDimension.length(10f));
        return style;
    }

    private static float[] layout(float rowWidth, float... contents) {
        TaffyTree tree = new TaffyTree();
        NodeId[] items = new NodeId[contents.length];
        for (int i = 0; i < contents.length; i++) items[i] = tree.newWithChildren(share(), tree.newLeaf(fixed(contents[i])));
        NodeId row = tree.newWithChildren(row(rowWidth), items);
        tree.computeLayout(row, new TaffySize<>(AvailableSpace.definite(rowWidth), AvailableSpace.definite(20f)));
        float[] widths = new float[items.length];
        for (int i = 0; i < items.length; i++) widths[i] = tree.getLayout(items[i]).size().width;
        return widths;
    }

    /** With room to spare every item stops at its content, rather than stretching to a third of the row. */
    @Test
    public void withRoomEachItemIsItsNaturalWidth() {
        float[] widths = layout(600f, 50f, 80f, 60f);
        assertEquals(50f, widths[0], 0.01f);
        assertEquals(80f, widths[1], 0.01f);
        assertEquals(60f, widths[2], 0.01f);
    }

    /** Without it the items share the row equally, below their caps. */
    @Test
    public void withoutRoomTheItemsShareTheRowEqually() {
        float[] widths = layout(120f, 50f, 80f, 60f);
        assertEquals(40f, widths[0], 0.01f);
        assertEquals(40f, widths[1], 0.01f);
        assertEquals(40f, widths[2], 0.01f);
    }
}
