/*
 * Copyright (c) 2015, Haiyang Li.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.landawn.abacus.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.stream.BaseStream;
import java.util.stream.Collector;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * This class is a sequential, stateful and immutable stream implementation.
 *
 * @author HaiYang Li
 *
 * @param <T>
 */
final class ArrayStream<T> implements Stream<T>, BaseStream<T, Stream<T>> {
    private final T[] values;
    private final int fromIndex;
    private final int toIndex;
    private final boolean sorted;
    private final List<Runnable> closeHandlers;

    ArrayStream(T[] values) {
        this(values, null);
    }

    ArrayStream(T[] values, List<Runnable> closeHandlers) {
        this(values, 0, values.length, closeHandlers);
    }

    ArrayStream(T[] values, boolean sorted, List<Runnable> closeHandlers) {
        this(values, 0, values.length, sorted, closeHandlers);
    }

    ArrayStream(T[] values, int fromIndex, int toIndex) {
        this(values, fromIndex, toIndex, null);
    }

    ArrayStream(T[] values, int fromIndex, int toIndex, List<Runnable> closeHandlers) {
        this(values, fromIndex, toIndex, false, closeHandlers);
    }

    ArrayStream(T[] values, int fromIndex, int toIndex, boolean sorted, List<Runnable> closeHandlers) {
        if (fromIndex < 0 || toIndex < fromIndex || toIndex > values.length) {
            throw new IllegalArgumentException("fromIndex(" + fromIndex + ") or toIndex(" + toIndex + ") is invalid");
        }

        this.values = values;
        this.fromIndex = fromIndex;
        this.toIndex = toIndex;
        this.sorted = sorted;
        this.closeHandlers = N.isNullOrEmpty(closeHandlers) ? null : N.newArrayList(closeHandlers);
    }

    @Override
    public Iterator<T> iterator() {
        return new ArrayIterator<T>(values, fromIndex, toIndex);
    }

    @Override
    public Spliterator<T> spliterator() {
        return Spliterators.spliterator(values, fromIndex, toIndex, Spliterator.ORDERED | Spliterator.IMMUTABLE);
    }

    @Override
    public boolean isParallel() {
        return false;
    }

    @Override
    public Stream<T> sequential() {
        return this;
    }

    @Override
    public Stream<T> parallel() {
        return Arrays.stream(values, fromIndex, toIndex).parallel();
    }

    @Override
    public Stream<T> unordered() {
        return this;
    }

    @Override
    public Stream<T> onClose(Runnable closeHandler) {
        final List<Runnable> closeHandlerList = N.asList(closeHandler);

        if (N.notNullOrEmpty(this.closeHandlers)) {
            closeHandlerList.addAll(this.closeHandlers);
        }

        return new ArrayStream<T>(values, fromIndex, toIndex, closeHandlerList);
    }

    @Override
    public void close() {
        if (N.notNullOrEmpty(closeHandlers)) {
            RuntimeException ex = null;

            for (Runnable closeHandler : closeHandlers) {
                try {
                    closeHandler.run();
                } catch (RuntimeException e) {
                    if (ex == null) {
                        ex = e;
                    } else {
                        ex.addSuppressed(e);
                    }
                }
            }

            if (ex != null) {
                throw ex;
            }
        }
    }

    @Override
    public Stream<T> filter(Predicate<? super T> predicate) {
        final List<T> list = new ArrayList<T>();

        for (int i = fromIndex; i < toIndex; i++) {
            if (predicate.test(values[i])) {
                list.add(values[i]);
            }
        }

        return new ArrayStream<T>((T[]) list.toArray(), closeHandlers);
    }

    @Override
    public <R> Stream<R> map(Function<? super T, ? extends R> mapper) {
        final Object[] a = new Object[toIndex - fromIndex];

        for (int i = fromIndex, j = 0; i < toIndex; i++, j++) {
            a[j] = mapper.apply(values[i]);
        }

        return new ArrayStream<R>((R[]) a, closeHandlers);
    }

    @Override
    public IntStream mapToInt(ToIntFunction<? super T> mapper) {
        final int[] a = new int[toIndex - fromIndex];

        for (int i = fromIndex, j = 0; i < toIndex; i++, j++) {
            a[j] = mapper.applyAsInt(values[i]);
        }

        return new IntStreamImpl(a, closeHandlers);
    }

    @Override
    public LongStream mapToLong(ToLongFunction<? super T> mapper) {
        final long[] a = new long[toIndex - fromIndex];

        for (int i = fromIndex, j = 0; i < toIndex; i++, j++) {
            a[j] = mapper.applyAsLong(values[i]);
        }

        return new LongStreamImpl(a, closeHandlers);
    }

    @Override
    public DoubleStream mapToDouble(ToDoubleFunction<? super T> mapper) {
        final double[] a = new double[toIndex - fromIndex];

        for (int i = fromIndex, j = 0; i < toIndex; i++, j++) {
            a[j] = mapper.applyAsDouble(values[i]);
        }

        return new DoubleStreamImpl(a, closeHandlers);
    }

    @Override
    public <R> Stream<R> flatMap(Function<? super T, ? extends Stream<? extends R>> mapper) {
        final List<Object[]> listOfArray = new ArrayList<>();

        int lengthOfAll = 0;
        for (int i = fromIndex; i < toIndex; i++) {
            final Object[] tmp = mapper.apply(values[i]).toArray();
            lengthOfAll += tmp.length;
            listOfArray.add(tmp);
        }

        final Object[] arrayOfAll = new Object[lengthOfAll];
        int from = 0;
        for (Object[] tmp : listOfArray) {
            N.copy(tmp, 0, arrayOfAll, from, tmp.length);
            from += tmp.length;
        }

        return new ArrayStream<R>((R[]) arrayOfAll, closeHandlers);
    }

    @Override
    public IntStream flatMapToInt(Function<? super T, ? extends IntStream> mapper) {
        final List<int[]> listOfArray = new ArrayList<>();

        int lengthOfAll = 0;
        for (int i = fromIndex; i < toIndex; i++) {
            final int[] tmp = mapper.apply(values[i]).toArray();
            lengthOfAll += tmp.length;
            listOfArray.add(tmp);
        }

        final int[] arrayOfAll = new int[lengthOfAll];
        int from = 0;
        for (int[] tmp : listOfArray) {
            N.copy(tmp, 0, arrayOfAll, from, tmp.length);
            from += tmp.length;
        }

        return new IntStreamImpl(arrayOfAll, closeHandlers);
    }

    @Override
    public LongStream flatMapToLong(Function<? super T, ? extends LongStream> mapper) {
        final List<long[]> listOfArray = new ArrayList<>();

        int lengthOfAll = 0;
        for (int i = fromIndex; i < toIndex; i++) {
            final long[] tmp = mapper.apply(values[i]).toArray();
            lengthOfAll += tmp.length;
            listOfArray.add(tmp);
        }

        final long[] arrayOfAll = new long[lengthOfAll];
        int from = 0;
        for (long[] tmp : listOfArray) {
            N.copy(tmp, 0, arrayOfAll, from, tmp.length);
            from += tmp.length;
        }

        return new LongStreamImpl(arrayOfAll, closeHandlers);
    }

    @Override
    public DoubleStream flatMapToDouble(Function<? super T, ? extends DoubleStream> mapper) {
        final List<double[]> listOfArray = new ArrayList<>();

        int lengthOfAll = 0;
        for (int i = fromIndex; i < toIndex; i++) {
            final double[] tmp = mapper.apply(values[i]).toArray();
            lengthOfAll += tmp.length;
            listOfArray.add(tmp);
        }

        final double[] arrayOfAll = new double[lengthOfAll];
        int from = 0;
        for (double[] tmp : listOfArray) {
            N.copy(tmp, 0, arrayOfAll, from, tmp.length);
            from += tmp.length;
        }

        return new DoubleStreamImpl(arrayOfAll, closeHandlers);
    }

    @Override
    public Stream<T> distinct() {
        return new ArrayStream<T>(N.removeDuplicates(values, fromIndex, toIndex, sorted), closeHandlers);
    }

    @Override
    public Stream<T> sorted() {
        if (sorted) {
            return new ArrayStream<T>(values, fromIndex, toIndex, sorted, closeHandlers);
        }

        final T[] a = N.copyOfRange(values, fromIndex, toIndex);
        Arrays.sort(a);
        return new ArrayStream<T>(a, true, closeHandlers);
    }

    @Override
    public Stream<T> sorted(Comparator<? super T> comparator) {
        final T[] a = N.copyOfRange(values, fromIndex, toIndex);
        Arrays.sort(a, comparator);
        return new ArrayStream<T>(a, closeHandlers);
    }

    @Override
    public Stream<T> peek(Consumer<? super T> action) {
        for (int i = fromIndex; i < toIndex; i++) {
            action.accept(values[i]);
        }

        return new ArrayStream<T>(values, fromIndex, toIndex, closeHandlers);
    }

    @Override
    public Stream<T> limit(long maxSize) {
        if (maxSize >= toIndex - fromIndex) {
            return new ArrayStream<T>(values, fromIndex, toIndex, closeHandlers);
        } else {
            return new ArrayStream<T>(values, fromIndex, (int) (fromIndex + maxSize), closeHandlers);
        }
    }

    @Override
    public Stream<T> skip(long n) {
        if (n >= toIndex - fromIndex) {
            return new ArrayStream<T>((T[]) N.EMPTY_OBJECT_ARRAY, closeHandlers);
        } else {
            return new ArrayStream<T>(values, (int) (fromIndex + n), toIndex, closeHandlers);
        }
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        for (int i = fromIndex; i < toIndex; i++) {
            action.accept(values[i]);
        }
    }

    @Override
    public void forEachOrdered(Consumer<? super T> action) {
        for (int i = fromIndex; i < toIndex; i++) {
            action.accept(values[i]);
        }
    }

    @Override
    public Object[] toArray() {
        return N.copyOfRange(values, fromIndex, toIndex);
    }

    @Override
    public <A> A[] toArray(IntFunction<A[]> generator) {
        final A[] a = generator.apply(toIndex - fromIndex);

        for (int i = fromIndex, j = 0; i < toIndex; i++, j++) {
            a[j] = (A) values[i];
        }

        return a;
    }

    @Override
    public T reduce(T identity, BinaryOperator<T> accumulator) {
        T result = identity;

        for (int i = fromIndex; i < toIndex; i++) {
            result = accumulator.apply(result, values[i]);
        }

        return result;
    }

    @Override
    public Optional<T> reduce(BinaryOperator<T> accumulator) {
        if (count() == 0) {
            Optional.empty();
        }

        T result = values[fromIndex];

        for (int i = fromIndex + 1; i < toIndex; i++) {
            result = accumulator.apply(result, values[i]);
        }

        return Optional.of(result);
    }

    @Override
    public <U> U reduce(U identity, BiFunction<U, ? super T, U> accumulator, BinaryOperator<U> combiner) {
        U result = identity;

        for (int i = fromIndex; i < toIndex; i++) {
            result = accumulator.apply(result, values[i]);
        }

        return result;
    }

    @Override
    public <R> R collect(Supplier<R> supplier, BiConsumer<R, ? super T> accumulator, BiConsumer<R, R> combiner) {
        final R result = supplier.get();

        for (int i = fromIndex; i < toIndex; i++) {
            accumulator.accept(result, values[i]);
        }

        return result;
    }

    @Override
    public <R, A> R collect(Collector<? super T, A, R> collector) {
        final A container = collector.supplier().get();
        final BiConsumer<A, ? super T> accumulator = collector.accumulator();

        for (int i = fromIndex; i < toIndex; i++) {
            accumulator.accept(container, values[i]);
        }

        return collector.finisher().apply(container);
    }

    @Override
    public Optional<T> min(Comparator<? super T> comparator) {
        if (count() == 0) {
            return Optional.empty();
        }

        return Optional.of(N.min(values, fromIndex, toIndex, comparator));
    }

    @Override
    public Optional<T> max(Comparator<? super T> comparator) {
        if (count() == 0) {
            return Optional.empty();
        }

        return Optional.of(N.max(values, fromIndex, toIndex, comparator));
    }

    @Override
    public long count() {
        return toIndex - fromIndex;
    }

    @Override
    public boolean anyMatch(Predicate<? super T> predicate) {
        for (int i = fromIndex; i < toIndex; i++) {
            if (predicate.test(values[i])) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean allMatch(Predicate<? super T> predicate) {
        for (int i = fromIndex; i < toIndex; i++) {
            if (predicate.test(values[i]) == false) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean noneMatch(Predicate<? super T> predicate) {
        for (int i = fromIndex; i < toIndex; i++) {
            if (predicate.test(values[i])) {
                return false;
            }
        }

        return true;
    }

    @Override
    public Optional<T> findFirst() {
        return count() == 0 ? (Optional<T>) Optional.empty() : Optional.of(values[fromIndex]);
    }

    @Override
    public Optional<T> findAny() {
        return count() == 0 ? (Optional<T>) Optional.empty() : Optional.of(values[fromIndex]);
    }

    static final class ArrayIterator<T> implements Iterator<T> {
        private final T[] a;
        private final int toIndex;
        private int idx = 0;

        ArrayIterator(T[] a, int fromIndex, int toIndex) {
            this.a = a;
            this.toIndex = toIndex;
            idx = fromIndex;
        }

        @Override
        public boolean hasNext() {
            return idx < toIndex;
        }

        @Override
        public T next() {
            return a[idx++];
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("remove");
        }
    }
}