package dev.vfyjxf.taffy.collection;

/**
 * CrystalGUI: a growable list of {@code int}, replacing {@code it.unimi.dsi.fastutil.ints.IntList}.
 *
 * <p>{@code Iterable<Integer>}, so an enhanced-for over it unboxes — which is what every one of
 * Taffy's call sites does. @see dev.vfyjxf.taffy.collection</p>
 */
public interface IntList extends Iterable<Integer> {

    /** Appends to the end. @return true, always — the signature upstream's call sites expect */
    boolean add(int value);

    int getInt(int index);

    /** @return the index of the first occurrence, or -1 */
    int indexOf(int value);

    boolean contains(int value);

    int size();

    boolean isEmpty();
}
