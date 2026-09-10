package dev.vfyjxf.taffy.collection;

/**
 * CrystalGUI: an {@code int}-keyed map of {@code float}, replacing
 * {@code it.unimi.dsi.fastutil.ints.Int2FloatMap}.
 *
 * @see dev.vfyjxf.taffy.collection
 */
public interface Int2FloatMap {

    /**
     * What {@link #get(int)} answers for a key that is not present. <b>Defaults to {@code 0f}</b>, so
     * a caller that cannot distinguish a stored zero from an absent key must set this — Taffy's
     * fit-content limits set it to {@code NaN}.
     */
    void defaultReturnValue(float value);

    /** @return the mapped value, or {@link #defaultReturnValue(float)}'s if the key is absent */
    float get(int key);

    /** @return the value previously mapped, or the default-return value if there was none */
    float put(int key, float value);
}
