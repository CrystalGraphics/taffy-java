package dev.vfyjxf.taffy;

import static org.junit.Assert.assertEquals;

import dev.vfyjxf.taffy.geometry.TaffySize;
import dev.vfyjxf.taffy.style.AvailableSpace;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.geometry.TaffyPoint;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.FlexWrap;
import dev.vfyjxf.taffy.geometry.TaffyRect;
import dev.vfyjxf.taffy.style.LengthPercentageAuto;
import dev.vfyjxf.taffy.style.Overflow;
import dev.vfyjxf.taffy.style.TaffyPosition;
import dev.vfyjxf.taffy.style.TaffyDimension;
import dev.vfyjxf.taffy.style.TaffyStyle;
import dev.vfyjxf.taffy.tree.NodeId;
import dev.vfyjxf.taffy.tree.TaffyTree;
import org.junit.Test;

/**
 * <b>CrystalGUI's</b> regression test: a PERCENTAGE minimum must not decide its ancestor's intrinsic size.
 *
 * <p>CSS resolves a percentage against the containing block, and while that block's size is still being
 * computed there is nothing to resolve against — so during an intrinsic pass the percentage behaves as
 * {@code auto}. Resolving it against the <em>outer</em> available space instead makes the minimum a
 * self-fulfilling one: the child claims the whole of what is available, the shrink-to-fit ancestor takes
 * that as its content width, and the percentage is then satisfied by the size it caused.</p>
 *
 * <p>Found through a compositor: every window on CrystalGUI's desktop came out exactly as wide as the
 * work area whatever was in it, because a window's content slot is a scroll container whose viewport
 * carries {@code min-width: 100%} — there so a caller's own {@code width: 100%} row cannot collapse. The
 * window still looked like a window, and a full-width one centres at x=0, which is where an unplaced one
 * sits anyway; it surfaced only in the cascade, which cannot step a window that already spans the area.</p>
 */
public class TaffyPercentMinIntrinsicTest {

    private static final float OUTER = 800f;
    private static final float CONTENT = 300f;

    private static TaffyStyle column() {
        TaffyStyle style = new TaffyStyle();
        style.flexDirection = FlexDirection.COLUMN;
        return style;
    }

    /** A box with a definite size, the thing that ought to decide the answer. */
    private static TaffyStyle fixed(float width, float height) {
        TaffyStyle style = column();
        style.size = new TaffySize<>(TaffyDimension.length(width), TaffyDimension.length(height));
        return style;
    }

    /** A column whose minimum is stated as a percentage of whatever contains it. */
    private static TaffyStyle percentMinWidth() {
        TaffyStyle style = column();
        style.minSize = new TaffySize<>(TaffyDimension.percent(1f), TaffyDimension.length(0f));
        return style;
    }

    /**
     * <b>The report.</b> An auto-width column measured at MAX_CONTENT takes its content's width, not the
     * width that happened to be available outside it.
     */
    @Test
    public void aPercentageMinimumDoesNotWidenAnAutoSizedAncestor() {
        TaffyTree tree = new TaffyTree();
        NodeId content = tree.newLeaf(fixed(CONTENT, 200f));
        NodeId slot = tree.newWithChildren(percentMinWidth(), content);
        // FLEX_START, or the container's own `stretch` widens it legitimately and the test proves
        // nothing. This is what a window is: out of flow, sized by what is in it.
        TaffyStyle shrink = column();
        shrink.alignSelf = AlignItems.FLEX_START;
        NodeId shrinkToFit = tree.newWithChildren(shrink, slot);
        NodeId outer = tree.newWithChildren(fixed(OUTER, 500f), shrinkToFit);

        tree.computeLayout(outer, new TaffySize<>(
                AvailableSpace.definite(OUTER), AvailableSpace.definite(500f)));

        assertEquals("the percentage minimum resolved against the OUTER box and widened its ancestor",
                CONTENT, tree.getLayout(shrinkToFit).size().width, 0.01f);
    }

    /**
     * <b>And the same, through a SCROLL CONTAINER.</b>
     *
     * <p>The shape that was actually shipping: neither declaration causes it alone — a scroll container
     * whose content is 300 wide measures 300, and so does a percentage minimum on an ordinary child —
     * but a percentage minimum <em>inside</em> a scroll container resolves against the space available
     * outside it, and the container then reports that as its own content width.</p>
     */
    @Test
    public void aPercentageMinimumInsideAScrollContainerDoesNotWidenItEither() {
        TaffyTree tree = new TaffyTree();
        NodeId content = tree.newLeaf(fixed(CONTENT, 200f));
        NodeId slot = tree.newWithChildren(percentMinWidth(), content);

        TaffyStyle scroller = column();
        scroller.overflow = new TaffyPoint<>(Overflow.SCROLL, Overflow.SCROLL);
        scroller.alignSelf = AlignItems.FLEX_START;
        NodeId scrollContainer = tree.newWithChildren(scroller, slot);
        NodeId outer = tree.newWithChildren(fixed(OUTER, 500f), scrollContainer);

        tree.computeLayout(outer, new TaffySize<>(
                AvailableSpace.definite(OUTER), AvailableSpace.definite(500f)));

        assertEquals("a percentage minimum inside a scroll container widened it to the outer width",
                CONTENT, tree.getLayout(scrollContainer).size().width, 0.01f);
    }

    /**
     * <b>The shape that was actually shipping.</b> A window is OUT OF FLOW and takes its content's
     * width; its content slot is a scroll container; the viewport inside that carries the percentage
     * minimum. None of the three causes this alone.
     */
    @Test
    public void anOutOfFlowAncestorOfAScrollContainerTakesItsContentsWidth() {
        TaffyTree tree = new TaffyTree();
        NodeId content = tree.newLeaf(fixed(CONTENT, 200f));
        NodeId slot = tree.newWithChildren(percentMinWidth(), content);

        TaffyStyle scroller = column();
        scroller.overflow = new TaffyPoint<>(Overflow.SCROLL, Overflow.SCROLL);
        NodeId scrollContainer = tree.newWithChildren(scroller, slot);

        // The window: absolutely positioned, one inset written, no width of its own.
        TaffyStyle window = column();
        window.position = TaffyPosition.ABSOLUTE;
        window.inset = new TaffyRect<>(LengthPercentageAuto.length(0f), LengthPercentageAuto.AUTO,
                LengthPercentageAuto.length(0f), LengthPercentageAuto.AUTO);
        NodeId frame = tree.newWithChildren(window, scrollContainer);
        NodeId outer = tree.newWithChildren(fixed(OUTER, 500f), frame);

        tree.computeLayout(outer, new TaffySize<>(
                AvailableSpace.definite(OUTER), AvailableSpace.definite(500f)));

        assertEquals("the window took the whole work area instead of its content's width",
                CONTENT, tree.getLayout(frame).size().width, 0.01f);
    }

    /**
     * <b>And shrink-to-fit is still BOUNDED.</b>
     *
     * <p>The counter-assertion to measuring intrinsically: CSS shrink-to-fit is
     * {@code min(max(min-content, available), max-content)}, so content that <em>can</em> shrink must
     * come down to what the containing block leaves rather than staying at its max-content width. A fix
     * that measured at max-content and stopped there would pass everything above and let a wide document
     * push its window off the desktop.</p>
     *
     * <p>The content is a WRAPPING row of four 400px items, so the three widths are genuinely different —
     * max-content 1600, min-content 400, available 800. Rigid content would prove nothing: CSS lets that
     * overflow, so an assertion on it would be pinning a clamp CSS does not have.</p>
     */
    @Test
    public void shrinkToFitStillDoesNotExceedTheContainingBlock() {
        TaffyTree tree = new TaffyTree();
        TaffyStyle wrapping = column();
        wrapping.flexDirection = FlexDirection.ROW;
        wrapping.flexWrap = FlexWrap.WRAP;
        NodeId row = tree.newWithChildren(wrapping,
                tree.newLeaf(fixed(400f, 100f)), tree.newLeaf(fixed(400f, 100f)),
                tree.newLeaf(fixed(400f, 100f)), tree.newLeaf(fixed(400f, 100f)));

        TaffyStyle window = column();
        window.position = TaffyPosition.ABSOLUTE;
        window.inset = new TaffyRect<>(LengthPercentageAuto.length(0f), LengthPercentageAuto.AUTO,
                LengthPercentageAuto.length(0f), LengthPercentageAuto.AUTO);
        NodeId frame = tree.newWithChildren(window, row);
        NodeId outer = tree.newWithChildren(fixed(OUTER, 500f), frame);

        tree.computeLayout(outer, new TaffySize<>(
                AvailableSpace.definite(OUTER), AvailableSpace.definite(500f)));

        assertEquals("content twice the width of the block carried the box out past it",
                OUTER, tree.getLayout(frame).size().width, 0.01f);
    }

    /**
     * And content that CANNOT shrink still overflows, which is what CSS does.
     *
     * <p>The bound is {@code max(min-content, available)}, not {@code available}: squeezing a box below
     * its min-content is the one thing the formula exists to prevent, and it is invisible in every
     * assertion above. The editor's fold chip is the real case — its own inset leaves it almost no room,
     * so a plain clamp to available drew it at a third of its width at high zoom.</p>
     */
    @Test
    public void andContentThatCannotShrinkIsNotSqueezedBelowIt() {
        TaffyTree tree = new TaffyTree();
        NodeId content = tree.newLeaf(fixed(OUTER * 2f, 200f));

        TaffyStyle window = column();
        window.position = TaffyPosition.ABSOLUTE;
        window.inset = new TaffyRect<>(LengthPercentageAuto.length(0f), LengthPercentageAuto.AUTO,
                LengthPercentageAuto.length(0f), LengthPercentageAuto.AUTO);
        NodeId frame = tree.newWithChildren(window, content);
        NodeId outer = tree.newWithChildren(fixed(OUTER, 500f), frame);

        tree.computeLayout(outer, new TaffySize<>(
                AvailableSpace.definite(OUTER), AvailableSpace.definite(500f)));

        assertEquals("a box that cannot shrink was squeezed below its min-content width",
                OUTER * 2f, tree.getLayout(frame).size().width, 0.01f);
    }

    /**
     * And it still binds once there IS something to resolve against.
     *
     * <p>The counter-assertion: a fix that simply ignored percentage minima everywhere would pass the
     * test above and silently drop the behaviour the declaration exists for — filling a parent whose
     * width is known when the content is narrower than it.</p>
     */
    @Test
    public void andStillFillsAParentWhoseWidthIsKnown() {
        TaffyTree tree = new TaffyTree();
        NodeId content = tree.newLeaf(fixed(CONTENT, 200f));
        NodeId slot = tree.newWithChildren(percentMinWidth(), content);
        NodeId definite = tree.newWithChildren(fixed(OUTER, 500f), slot);

        tree.computeLayout(definite, new TaffySize<>(
                AvailableSpace.definite(OUTER), AvailableSpace.definite(500f)));

        assertEquals("a minimum against a KNOWN width must still fill it",
                OUTER, tree.getLayout(slot).size().width, 0.01f);
    }
}
