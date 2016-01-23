/*
 * Copyright (c) 2015, Haiyang Li. All rights reserved.
 */

package com.landawn.abacus.util.function;

import com.landawn.abacus.util.N;

/**
 *
 * @since 0.8
 * 
 * @author Haiyang Li
 * 
 * @see java.util.function.Consumer
 */
public interface IndexedIntConsumer {
    /**
     * Performs this operation on the given argument
     *     
     * @param t
     * @param idx
     */
    void accept(int t, int idx);

    default IndexedIntConsumer andThen(IndexedIntConsumer after) {
        N.requireNonNull(after);
        return (int t, int idx) -> {
            accept(t, idx);
            after.accept(t, idx);
        };
    }
}