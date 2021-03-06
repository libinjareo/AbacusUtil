/*
 * Copyright (C) 2016 HaiYang Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.landawn.abacus.util.stream;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import com.landawn.abacus.util.FloatIterator;
import com.landawn.abacus.util.FloatList;
import com.landawn.abacus.util.FloatMatrix;
import com.landawn.abacus.util.FloatSummaryStatistics;
import com.landawn.abacus.util.Fn;
import com.landawn.abacus.util.IndexedFloat;
import com.landawn.abacus.util.Joiner;
import com.landawn.abacus.util.Multiset;
import com.landawn.abacus.util.MutableFloat;
import com.landawn.abacus.util.MutableLong;
import com.landawn.abacus.util.N;
import com.landawn.abacus.util.Nth;
import com.landawn.abacus.util.Optional;
import com.landawn.abacus.util.OptionalDouble;
import com.landawn.abacus.util.OptionalFloat;
import com.landawn.abacus.util.Pair;
import com.landawn.abacus.util.Percentage;
import com.landawn.abacus.util.Try;
import com.landawn.abacus.util.function.BiConsumer;
import com.landawn.abacus.util.function.BiPredicate;
import com.landawn.abacus.util.function.BinaryOperator;
import com.landawn.abacus.util.function.Consumer;
import com.landawn.abacus.util.function.FloatBiFunction;
import com.landawn.abacus.util.function.FloatBiPredicate;
import com.landawn.abacus.util.function.FloatConsumer;
import com.landawn.abacus.util.function.FloatFunction;
import com.landawn.abacus.util.function.FloatPredicate;
import com.landawn.abacus.util.function.FloatTriFunction;
import com.landawn.abacus.util.function.Function;
import com.landawn.abacus.util.function.ObjFloatConsumer;
import com.landawn.abacus.util.function.Predicate;
import com.landawn.abacus.util.function.Supplier;
import com.landawn.abacus.util.function.ToFloatFunction;

/**
 * This class is a sequential, stateful and immutable stream implementation.
 *
 * @since 0.8
 * 
 * @author Haiyang Li
 */
abstract class AbstractFloatStream extends FloatStream {

    AbstractFloatStream(final boolean sorted, final Collection<Runnable> closeHandlers) {
        super(sorted, closeHandlers);
    }

    @Override
    public FloatStream flattMap(final FloatFunction<float[]> mapper) {
        return flatMap(new FloatFunction<FloatStream>() {
            @Override
            public FloatStream apply(float t) {
                return FloatStream.of(mapper.apply(t));
            }
        });
    }

    @Override
    public <T> Stream<T> flattMapToObj(final FloatFunction<? extends Collection<T>> mapper) {
        return flatMapToObj(new FloatFunction<Stream<T>>() {
            @Override
            public Stream<T> apply(float t) {
                return Stream.of(mapper.apply(t));
            }
        });
    }

    @Override
    public FloatStream skip(final long n, final FloatConsumer action) {
        N.checkArgNotNegative(n, "n");

        if (n == 0) {
            return this;
        }

        final FloatPredicate filter = isParallel() ? new FloatPredicate() {
            final AtomicLong cnt = new AtomicLong(n);

            @Override
            public boolean test(float value) {
                return cnt.getAndDecrement() > 0;
            }
        } : new FloatPredicate() {
            final MutableLong cnt = MutableLong.of(n);

            @Override
            public boolean test(float value) {
                return cnt.getAndDecrement() > 0;
            }
        };

        return dropWhile(filter, action);
    }

    @Override
    public FloatStream removeIf(final FloatPredicate predicate) {
        N.checkArgNotNull(predicate);

        return filter(new FloatPredicate() {
            @Override
            public boolean test(float value) {
                return predicate.test(value) == false;
            }
        });
    }

    @Override
    public FloatStream removeIf(final FloatPredicate predicate, final FloatConsumer action) {
        N.checkArgNotNull(predicate);
        N.checkArgNotNull(predicate);

        return filter(new FloatPredicate() {
            @Override
            public boolean test(float value) {
                if (predicate.test(value)) {
                    action.accept(value);
                    return false;
                }

                return true;
            }
        });
    }

    @Override
    public FloatStream dropWhile(final FloatPredicate predicate, final FloatConsumer action) {
        N.checkArgNotNull(predicate);
        N.checkArgNotNull(action);

        return dropWhile(new FloatPredicate() {
            @Override
            public boolean test(float value) {
                if (predicate.test(value)) {
                    action.accept(value);
                    return true;
                }

                return false;
            }
        });
    }

    @Override
    public FloatStream step(final long step) {
        N.checkArgPositive(step, "step");

        if (step == 1) {
            return this;
        }

        final long skip = step - 1;
        final FloatIteratorEx iter = this.iteratorEx();

        final FloatIterator floatIterator = new FloatIteratorEx() {
            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public float nextFloat() {
                final float next = iter.nextFloat();
                iter.skip(skip);
                return next;
            }
        };

        return newStream(floatIterator, sorted);
    }

    @Override
    public Stream<FloatStream> split(final int size) {
        return splitToList(size).map(new Function<FloatList, FloatStream>() {
            @Override
            public FloatStream apply(FloatList t) {
                return new ArrayFloatStream(t.array(), 0, t.size(), sorted, null);
            }
        });
    }

    @Override
    public Stream<FloatStream> split(final FloatPredicate predicate) {
        return splitToList(predicate).map(new Function<FloatList, FloatStream>() {
            @Override
            public FloatStream apply(FloatList t) {
                return new ArrayFloatStream(t.array(), 0, t.size(), sorted, null);
            }
        });
    }

    @Override
    public Stream<FloatList> splitToList(final FloatPredicate predicate) {
        final BiPredicate<Float, Object> predicate2 = new BiPredicate<Float, Object>() {

            @Override
            public boolean test(Float t, Object u) {
                return predicate.test(t);
            }
        };

        return splitToList(null, predicate2, null);
    }

    @Override
    public <U> Stream<FloatStream> split(final U seed, final BiPredicate<? super Float, ? super U> predicate, final Consumer<? super U> seedUpdate) {
        return splitToList(seed, predicate, seedUpdate).map(new Function<FloatList, FloatStream>() {
            @Override
            public FloatStream apply(FloatList t) {
                return new ArrayFloatStream(t.array(), 0, t.size(), sorted, null);
            }
        });
    }

    @Override
    public Stream<FloatStream> sliding(final int windowSize, final int increment) {
        return slidingToList(windowSize, increment).map(new Function<FloatList, FloatStream>() {
            @Override
            public FloatStream apply(FloatList t) {
                return new ArrayFloatStream(t.array(), 0, t.size(), sorted, null);
            }
        });
    }

    @Override
    public FloatStream collapse(final FloatBiPredicate collapsible, final FloatBiFunction<Float> mergeFunction) {
        final FloatIteratorEx iter = iteratorEx();

        return newStream(new FloatIteratorEx() {
            private boolean hasNext = false;
            private float next = 0;

            @Override
            public boolean hasNext() {
                return hasNext || iter.hasNext();
            }

            @Override
            public float nextFloat() {
                float res = hasNext ? next : (next = iter.nextFloat());

                while ((hasNext = iter.hasNext())) {
                    if (collapsible.test(next, (next = iter.nextFloat()))) {
                        res = mergeFunction.apply(res, next);
                    } else {
                        break;
                    }
                }

                return res;
            }
        }, false);
    }

    @Override
    public FloatStream scan(final FloatBiFunction<Float> accumulator) {
        final FloatIteratorEx iter = iteratorEx();

        return newStream(new FloatIteratorEx() {
            private float res = 0;
            private boolean isFirst = true;

            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public float nextFloat() {
                if (isFirst) {
                    isFirst = false;
                    return (res = iter.nextFloat());
                } else {
                    return (res = accumulator.apply(res, iter.nextFloat()));
                }
            }
        }, false);
    }

    @Override
    public FloatStream scan(final float seed, final FloatBiFunction<Float> accumulator) {
        final FloatIteratorEx iter = iteratorEx();

        return newStream(new FloatIteratorEx() {
            private float res = seed;

            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public float nextFloat() {
                return (res = accumulator.apply(res, iter.nextFloat()));
            }
        }, false);
    }

    @Override
    public <K, U> Map<K, U> toMap(FloatFunction<? extends K> keyExtractor, FloatFunction<? extends U> valueMapper) {
        final Supplier<Map<K, U>> mapFactory = Fn.Suppliers.ofMap();

        return toMap(keyExtractor, valueMapper, mapFactory);
    }

    @Override
    public <K, U, M extends Map<K, U>> M toMap(FloatFunction<? extends K> keyExtractor, FloatFunction<? extends U> valueMapper, Supplier<M> mapFactory) {
        final BinaryOperator<U> mergeFunction = Fn.throwingMerger();

        return toMap(keyExtractor, valueMapper, mergeFunction, mapFactory);
    }

    @Override
    public <K, U> Map<K, U> toMap(FloatFunction<? extends K> keyExtractor, FloatFunction<? extends U> valueMapper, BinaryOperator<U> mergeFunction) {
        final Supplier<Map<K, U>> mapFactory = Fn.Suppliers.ofMap();

        return toMap(keyExtractor, valueMapper, mergeFunction, mapFactory);
    }

    @Override
    public <K, A, D> Map<K, D> toMap(FloatFunction<? extends K> classifier, Collector<Float, A, D> downstream) {
        final Supplier<Map<K, D>> mapFactory = Fn.Suppliers.ofMap();

        return toMap(classifier, downstream, mapFactory);
    }

    @Override
    public FloatMatrix toMatrix() {
        return FloatMatrix.of(toArray());
    }

    @Override
    public FloatStream distinct() {
        final Set<Object> set = new HashSet<>();

        return newStream(this.sequential().filter(new FloatPredicate() {
            @Override
            public boolean test(float value) {
                return set.add(value);
            }
        }).iteratorEx(), sorted);
    }

    @Override
    public FloatStream top(int n) {
        return top(n, FLOAT_COMPARATOR);
    }

    @Override
    public double sum() {
        final Supplier<double[]> supplier = new Supplier<double[]>() {
            @Override
            public double[] get() {
                return new double[3];
            }
        };

        final ObjFloatConsumer<double[]> accumulator = new ObjFloatConsumer<double[]>() {
            @Override
            public void accept(double[] ll, float f) {
                Collectors.sumWithCompensation(ll, f);
                ll[2] += f;
            }
        };

        final BiConsumer<double[], double[]> combiner = new BiConsumer<double[], double[]>() {
            @Override
            public void accept(double[] ll, double[] rr) {
                Collectors.sumWithCompensation(ll, rr[0]);
                Collectors.sumWithCompensation(ll, rr[1]);
                ll[2] += rr[2];
            }
        };

        final double[] summation = collect(supplier, accumulator, combiner);

        return Collectors.computeFinalSum(summation);
    }

    @Override
    public OptionalDouble average() {
        final Supplier<double[]> supplier = new Supplier<double[]>() {
            @Override
            public double[] get() {
                return new double[4];
            }
        };

        final ObjFloatConsumer<double[]> accumulator = new ObjFloatConsumer<double[]>() {
            @Override
            public void accept(double[] ll, float f) {
                ll[2]++;
                Collectors.sumWithCompensation(ll, f);
                ll[3] += f;
            }
        };

        final BiConsumer<double[], double[]> combiner = new BiConsumer<double[], double[]>() {
            @Override
            public void accept(double[] ll, double[] rr) {
                Collectors.sumWithCompensation(ll, rr[0]);
                Collectors.sumWithCompensation(ll, rr[1]);
                ll[2] += rr[2];
                ll[3] += rr[3];
            }
        };

        final double[] avg = collect(supplier, accumulator, combiner);

        return avg[2] > 0 ? OptionalDouble.of(Collectors.computeFinalSum(avg) / avg[2]) : OptionalDouble.empty();
    }

    @Override
    public OptionalFloat first() {
        final FloatIterator iter = this.iteratorEx();

        return iter.hasNext() ? OptionalFloat.of(iter.nextFloat()) : OptionalFloat.empty();
    }

    @Override
    public OptionalFloat last() {
        final FloatIterator iter = this.iteratorEx();

        if (iter.hasNext() == false) {
            return OptionalFloat.empty();
        }

        float next = iter.nextFloat();

        while (iter.hasNext()) {
            next = iter.nextFloat();
        }

        return OptionalFloat.of(next);
    }

    @Override
    public <E extends Exception> OptionalFloat findAny(final Try.FloatPredicate<E> predicate) throws E {
        return findFirst(predicate);
    }

    @Override
    public <E extends Exception, E2 extends Exception> OptionalFloat findFirstOrLast(Try.FloatPredicate<E> predicateForFirst,
            Try.FloatPredicate<E> predicateForLast) throws E, E2 {
        final FloatIteratorEx iter = iteratorEx();
        MutableFloat last = null;
        float next = 0;

        while (iter.hasNext()) {
            next = iter.nextFloat();

            if (predicateForFirst.test(next)) {
                return OptionalFloat.of(next);
            } else if (predicateForLast.test(next)) {
                if (last == null) {
                    last = MutableFloat.of(next);
                } else {
                    last.setValue(next);
                }
            }
        }

        return last == null ? OptionalFloat.empty() : OptionalFloat.of(last.value());
    }

    @Override
    public FloatStream intersection(final Collection<?> c) {
        final Multiset<?> multiset = Multiset.from(c);

        return newStream(this.sequential().filter(new FloatPredicate() {
            @Override
            public boolean test(float value) {
                return multiset.getAndRemove(value) > 0;
            }
        }).iteratorEx(), sorted);
    }

    @Override
    public FloatStream difference(final Collection<?> c) {
        final Multiset<?> multiset = Multiset.from(c);

        return newStream(this.sequential().filter(new FloatPredicate() {
            @Override
            public boolean test(float value) {
                return multiset.getAndRemove(value) < 1;
            }
        }).iteratorEx(), sorted);
    }

    @Override
    public FloatStream symmetricDifference(final Collection<Float> c) {
        final Multiset<?> multiset = Multiset.from(c);

        return newStream(this.sequential().filter(new FloatPredicate() {
            @Override
            public boolean test(float value) {
                return multiset.getAndRemove(value) < 1;
            }
        }).append(Stream.of(c).filter(new Predicate<Float>() {
            @Override
            public boolean test(Float value) {
                return multiset.getAndRemove(value) > 0;
            }
        }).mapToFloat(ToFloatFunction.UNBOX)).iteratorEx(), false);
    }

    @Override
    public Stream<FloatStream> splitAt(final int n) {
        N.checkArgNotNegative(n, "n");

        return newStream(new ObjIteratorEx<FloatStream>() {
            private FloatStream[] a = null;
            private int cursor = 0;

            @Override
            public boolean hasNext() {
                init();

                return cursor < 2;
            }

            @Override
            public FloatStream next() {
                if (hasNext() == false) {
                    throw new NoSuchElementException();
                }

                return a[cursor++];
            }

            private void init() {
                if (a == null) {
                    final FloatIterator iter = AbstractFloatStream.this.iteratorEx();
                    final FloatList list = new FloatList();

                    while (list.size() < n && iter.hasNext()) {
                        list.add(iter.nextFloat());
                    }

                    a = new FloatStream[] { new ArrayFloatStream(list.array(), 0, list.size(), sorted, null), new IteratorFloatStream(iter, sorted, null) };
                }
            }

        }, false, null);
    }

    @Override
    public Stream<FloatStream> splitBy(final FloatPredicate where) {
        N.checkArgNotNull(where);

        return newStream(new ObjIteratorEx<FloatStream>() {
            private FloatStream[] a = null;
            private int cursor = 0;

            @Override
            public boolean hasNext() {
                init();

                return cursor < 2;
            }

            @Override
            public FloatStream next() {
                if (hasNext() == false) {
                    throw new NoSuchElementException();
                }

                return a[cursor++];
            }

            private void init() {
                if (a == null) {
                    final FloatIterator iter = AbstractFloatStream.this.iteratorEx();
                    final FloatList list = new FloatList();
                    float next = 0;
                    FloatStream s = null;

                    while (iter.hasNext()) {
                        next = iter.nextFloat();

                        if (where.test(next)) {
                            list.add(next);
                        } else {
                            s = FloatStream.of(next);

                            break;
                        }
                    }

                    a = new FloatStream[] { new ArrayFloatStream(list.array(), 0, list.size(), sorted, null), new IteratorFloatStream(iter, sorted, null) };

                    if (s != null) {
                        if (sorted) {
                            a[1] = new IteratorFloatStream(a[1].prepend(s).iteratorEx(), sorted, null);
                        } else {
                            a[1] = a[1].prepend(s);
                        }
                    }
                }
            }

        }, false, null);
    }

    @Override
    public FloatStream reversed() {
        return newStream(new FloatIteratorEx() {
            private boolean initialized = false;
            private float[] aar;
            private int cursor;

            @Override
            public boolean hasNext() {
                if (initialized == false) {
                    init();
                }

                return cursor > 0;
            }

            @Override
            public float nextFloat() {
                if (initialized == false) {
                    init();
                }

                if (cursor <= 0) {
                    throw new NoSuchElementException();
                }

                return aar[--cursor];
            }

            @Override
            public long count() {
                if (initialized == false) {
                    init();
                }

                return cursor;
            }

            @Override
            public void skip(long n) {
                if (initialized == false) {
                    init();
                }

                cursor = n < cursor ? cursor - (int) n : 0;
            }

            @Override
            public float[] toArray() {
                if (initialized == false) {
                    init();
                }

                final float[] a = new float[cursor];

                for (int i = 0; i < cursor; i++) {
                    a[i] = aar[cursor - i - 1];
                }

                return a;
            }

            private void init() {
                if (initialized == false) {
                    initialized = true;
                    aar = AbstractFloatStream.this.toArray();
                    cursor = aar.length;
                }
            }
        }, false);
    }

    @Override
    public FloatStream shuffled(final Random rnd) {
        return lazyLoad(new Function<float[], float[]>() {
            @Override
            public float[] apply(final float[] a) {
                N.shuffle(a, rnd);
                return a;
            }
        }, false);
    }

    @Override
    public FloatStream rotated(final int distance) {
        return newStream(new FloatIteratorEx() {
            private boolean initialized = false;
            private float[] aar;
            private int len;
            private int start;
            private int cnt = 0;

            @Override
            public boolean hasNext() {
                if (initialized == false) {
                    init();
                }

                return cnt < len;
            }

            @Override
            public float nextFloat() {
                if (hasNext() == false) {
                    throw new NoSuchElementException();
                }

                return aar[(start + cnt++) % len];
            }

            @Override
            public long count() {
                if (initialized == false) {
                    init();
                }

                return len - cnt;
            }

            @Override
            public void skip(long n) {
                if (initialized == false) {
                    init();
                }

                cnt = n < len - cnt ? cnt + (int) n : len;
            }

            @Override
            public float[] toArray() {
                if (initialized == false) {
                    init();
                }

                final float[] a = new float[len - cnt];

                for (int i = cnt; i < len; i++) {
                    a[i - cnt] = aar[(start + i) % len];
                }

                return a;
            }

            private void init() {
                if (initialized == false) {
                    initialized = true;
                    aar = AbstractFloatStream.this.toArray();
                    len = aar.length;

                    if (len > 0) {
                        start = distance % len;

                        if (start < 0) {
                            start += len;
                        }

                        start = len - start;
                    }
                }
            }
        }, distance == 0 && sorted);
    }

    @Override
    public FloatStream sorted() {
        if (sorted) {
            return this;
        }

        return lazyLoad(new Function<float[], float[]>() {
            @Override
            public float[] apply(final float[] a) {
                if (isParallel()) {
                    N.parallelSort(a);
                } else {
                    N.sort(a);
                }

                return a;
            }
        }, true);
    }

    @Override
    public FloatStream reverseSorted() {
        return newStream(new FloatIteratorEx() {
            private boolean initialized = false;
            private float[] aar;
            private int cursor;

            @Override
            public boolean hasNext() {
                if (initialized == false) {
                    init();
                }

                return cursor > 0;
            }

            @Override
            public float nextFloat() {
                if (initialized == false) {
                    init();
                }

                if (cursor <= 0) {
                    throw new NoSuchElementException();
                }

                return aar[--cursor];
            }

            @Override
            public long count() {
                if (initialized == false) {
                    init();
                }

                return cursor;
            }

            @Override
            public void skip(long n) {
                if (initialized == false) {
                    init();
                }

                cursor = n < cursor ? cursor - (int) n : 0;
            }

            @Override
            public float[] toArray() {
                if (initialized == false) {
                    init();
                }

                final float[] a = new float[cursor];

                for (int i = 0; i < cursor; i++) {
                    a[i] = aar[cursor - i - 1];
                }

                return a;
            }

            private void init() {
                if (initialized == false) {
                    initialized = true;
                    aar = AbstractFloatStream.this.toArray();

                    if (isParallel()) {
                        N.parallelSort(aar);
                    } else {
                        N.sort(aar);
                    }

                    cursor = aar.length;
                }
            }
        }, false);
    }

    private FloatStream lazyLoad(final Function<float[], float[]> op, final boolean sorted) {
        return newStream(new FloatIteratorEx() {
            private boolean initialized = false;
            private float[] aar;
            private int cursor = 0;
            private int len;

            @Override
            public boolean hasNext() {
                if (initialized == false) {
                    init();
                }

                return cursor < len;
            }

            @Override
            public float nextFloat() {
                if (initialized == false) {
                    init();
                }

                if (cursor >= len) {
                    throw new NoSuchElementException();
                }

                return aar[cursor++];
            }

            @Override
            public long count() {
                if (initialized == false) {
                    init();
                }

                return len - cursor;
            }

            @Override
            public void skip(long n) {
                if (initialized == false) {
                    init();
                }

                cursor = n > len - cursor ? len : cursor + (int) n;
            }

            @Override
            public float[] toArray() {
                if (initialized == false) {
                    init();
                }

                final float[] a = new float[len - cursor];

                for (int i = cursor; i < len; i++) {
                    a[i - cursor] = aar[i];
                }

                return a;
            }

            private void init() {
                if (initialized == false) {
                    initialized = true;
                    aar = op.apply(AbstractFloatStream.this.toArray());
                    len = aar.length;
                }
            }
        }, sorted);
    }

    @Override
    public Optional<Map<Percentage, Float>> percentiles() {
        final float[] a = sorted().toArray();

        if (a.length == 0) {
            return Optional.empty();
        }

        return Optional.of(N.percentiles(a));
    }

    @Override
    public Pair<FloatSummaryStatistics, Optional<Map<Percentage, Float>>> summarizze() {
        final float[] a = sorted().toArray();

        if (N.isNullOrEmpty(a)) {
            return Pair.of(new FloatSummaryStatistics(), Optional.<Map<Percentage, Float>> empty());
        } else {
            return Pair.of(new FloatSummaryStatistics(a.length, sum(a), a[0], a[a.length - 1]), Optional.of(N.percentiles(a)));
        }
    }

    @Override
    public String join(final CharSequence delimiter) {
        return join(delimiter, "", "");
    }

    @Override
    public String join(final CharSequence delimiter, final CharSequence prefix, final CharSequence suffix) {
        final Joiner joiner = Joiner.with(delimiter, prefix, suffix).reuseStringBuilder(true);
        final FloatIteratorEx iter = this.iteratorEx();

        while (iter.hasNext()) {
            joiner.append(iter.nextFloat());
        }

        return joiner.toString();
    }

    @Override
    public <R> R collect(Supplier<R> supplier, ObjFloatConsumer<R> accumulator) {
        final BiConsumer<R, R> combiner = collectingCombiner;

        return collect(supplier, accumulator, combiner);
    }

    @Override
    public Pair<OptionalFloat, FloatStream> headAndTail() {
        return Pair.of(head(), tail());
    }

    @Override
    public Pair<FloatStream, OptionalFloat> headAndTaill() {
        return Pair.of(headd(), taill());
    }

    @Override
    public Stream<IndexedFloat> indexed() {
        final MutableLong idx = MutableLong.of(0);

        return newStream(this.sequential().mapToObj(new FloatFunction<IndexedFloat>() {
            @Override
            public IndexedFloat apply(float t) {
                return IndexedFloat.of(t, idx.getAndIncrement());
            }
        }).iterator(), true, INDEXED_FLOAT_COMPARATOR);
    }

    @Override
    public FloatStream append(FloatStream stream) {
        return FloatStream.concat(this, stream);
    }

    @Override
    public FloatStream prepend(FloatStream stream) {
        return FloatStream.concat(stream, this);
    }

    @Override
    public FloatStream merge(FloatStream b, FloatBiFunction<Nth> nextSelector) {
        return FloatStream.merge(this, b, nextSelector);
    }

    @Override
    public FloatStream zipWith(FloatStream b, FloatBiFunction<Float> zipFunction) {
        return FloatStream.zip(this, b, zipFunction);
    }

    @Override
    public FloatStream zipWith(FloatStream b, FloatStream c, FloatTriFunction<Float> zipFunction) {
        return FloatStream.zip(this, b, c, zipFunction);
    }

    @Override
    public FloatStream zipWith(FloatStream b, float valueForNoneA, float valueForNoneB, FloatBiFunction<Float> zipFunction) {
        return FloatStream.zip(this, b, valueForNoneA, valueForNoneB, zipFunction);
    }

    @Override
    public FloatStream zipWith(FloatStream b, FloatStream c, float valueForNoneA, float valueForNoneB, float valueForNoneC,
            FloatTriFunction<Float> zipFunction) {
        return FloatStream.zip(this, b, c, valueForNoneA, valueForNoneB, valueForNoneC, zipFunction);
    }

    @Override
    public FloatStream cached() {
        return newStream(toArray(), sorted);
    }
}
