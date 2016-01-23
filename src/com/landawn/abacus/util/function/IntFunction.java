/*
 * Copyright (c) 2015, Haiyang Li. All rights reserved.
 */

package com.landawn.abacus.util.function;

/**
 * 
 * @param <R>
 * @since 0.8
 * 
 * @author Haiyang Li
 * 
 * @see java.util.function.IntFunction
 */
// public interface IntFunction<R> {
public interface IntFunction<R> extends java.util.function.IntFunction<R> {
    @Override
    R apply(int value);
}