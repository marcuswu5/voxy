package me.cortex.voxy.common.util;

/**
 * Runtime support for the Java Vector API (jdk.incubator.vector).
 * If the module is not present, all operations fall back to scalar implementations.
 */
public final class VectorSupport {

    /**
     * True if jdk.incubator.vector is available at runtime (e.g. JVM started with --add-modules jdk.incubator.vector).
     */
    public static final boolean VECTOR_AVAILABLE;

    private static volatile Lvl0InsertStrategy cachedLvl0Strategy;

    static {
        boolean available = false;
        try {
            Class.forName("jdk.incubator.vector.LongVector");
            available = true;
        } catch (Throwable ignored) {
        }
        VECTOR_AVAILABLE = available;
    }

    private VectorSupport() {
    }

    /**
     * Returns the strategy for the level-0 insert loop (vectorized when available, else scalar).
     */
    public static Lvl0InsertStrategy getLvl0InsertStrategy() {
        Lvl0InsertStrategy s = cachedLvl0Strategy;
        if (s == null) {
            synchronized (VectorSupport.class) {
                s = cachedLvl0Strategy;
                if (s == null) {
                    if (VECTOR_AVAILABLE) {
                        try {
                            Class<?> cl = Class.forName("me.cortex.voxy.common.util.VectorLvl0InsertStrategy");
                            s = (Lvl0InsertStrategy) cl.getConstructor().newInstance();
                        } catch (Throwable t) {
                            s = ScalarLvl0InsertStrategy.INSTANCE;
                        }
                    } else {
                        s = ScalarLvl0InsertStrategy.INSTANCE;
                    }
                    cachedLvl0Strategy = s;
                }
            }
        }
        return s;
    }

    /**
     * Fills arr[from..to) with zero. Uses LongVector when available, otherwise scalar.
     */
    public static void fillZero(long[] arr, int from, int to) {
        if (from >= to) return;
        if (VECTOR_AVAILABLE) {
            try {
                Class<?> ops = Class.forName("me.cortex.voxy.common.util.VectorOps");
                ops.getMethod("fillZero", long[].class, int.class, int.class).invoke(null, arr, from, to);
                return;
            } catch (Throwable t) {
                // fallback to scalar
            }
        }
        for (int i = from; i < to; i++) {
            arr[i] = 0L;
        }
    }
}
