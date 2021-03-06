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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.StreamSupport;

import com.landawn.abacus.util.ByteIterator;
import com.landawn.abacus.util.CharIterator;
import com.landawn.abacus.util.DoubleIterator;
import com.landawn.abacus.util.FloatIterator;
import com.landawn.abacus.util.IntIterator;
import com.landawn.abacus.util.LongIterator;
import com.landawn.abacus.util.LongMultiset;
import com.landawn.abacus.util.Multimap;
import com.landawn.abacus.util.Multiset;
import com.landawn.abacus.util.N;
import com.landawn.abacus.util.Optional;
import com.landawn.abacus.util.ShortIterator;
import com.landawn.abacus.util.Try;
import com.landawn.abacus.util.function.BiConsumer;
import com.landawn.abacus.util.function.BiFunction;
import com.landawn.abacus.util.function.BiPredicate;
import com.landawn.abacus.util.function.BinaryOperator;
import com.landawn.abacus.util.function.Consumer;
import com.landawn.abacus.util.function.Function;
import com.landawn.abacus.util.function.Predicate;
import com.landawn.abacus.util.function.Supplier;
import com.landawn.abacus.util.function.ToByteFunction;
import com.landawn.abacus.util.function.ToCharFunction;
import com.landawn.abacus.util.function.ToDoubleFunction;
import com.landawn.abacus.util.function.ToFloatFunction;
import com.landawn.abacus.util.function.ToIntFunction;
import com.landawn.abacus.util.function.ToLongFunction;
import com.landawn.abacus.util.function.ToShortFunction;
import com.landawn.abacus.util.function.TriFunction;
import com.landawn.abacus.util.stream.ObjIteratorEx.QueuedIterator;

/**
 * This class is a sequential, stateful and immutable stream implementation.
 *
 * @param <T>
 * @since 0.8
 * 
 * @author Haiyang Li
 */
class IteratorStream<T> extends AbstractStream<T> {
    final ObjIteratorEx<T> elements;

    Optional<T> head;
    Stream<T> tail;

    Stream<T> head2;
    Optional<T> tail2;

    IteratorStream(final Iterator<? extends T> values) {
        this(values, null);
    }

    IteratorStream(final Iterator<? extends T> values, final Collection<Runnable> closeHandlers) {
        this(values, false, null, closeHandlers);
    }

    IteratorStream(final Iterator<? extends T> values, final boolean sorted, final Comparator<? super T> comparator, final Collection<Runnable> closeHandlers) {
        super(sorted, comparator, closeHandlers);

        N.checkArgNotNull(values);

        ObjIteratorEx<T> tmp = null;

        if (values instanceof ObjIteratorEx) {
            tmp = (ObjIteratorEx<T>) values;
        } else {
            tmp = new ObjIteratorEx<T>() {
                @Override
                public boolean hasNext() {
                    return values.hasNext();
                }

                @Override
                public T next() {
                    return values.next();
                }
            };
        }

        this.elements = tmp;
    }

    IteratorStream(final Stream<T> stream, final boolean sorted, final Comparator<? super T> comparator, final Set<Runnable> closeHandlers) {
        this(stream.iterator(), sorted, comparator, mergeCloseHandlers(stream, closeHandlers));
    }

    @Override
    public Stream<T> filter(final Predicate<? super T> predicate) {
        return newStream(new ObjIteratorEx<T>() {
            private boolean hasNext = false;
            private T next = null;

            @Override
            public boolean hasNext() {
                if (hasNext == false) {
                    while (elements.hasNext()) {
                        next = elements.next();

                        if (predicate.test(next)) {
                            hasNext = true;
                            break;
                        }
                    }
                }

                return hasNext;
            }

            @Override
            public T next() {
                if (hasNext == false && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                hasNext = false;

                return next;
            }
        }, sorted, cmp);
    }

    @Override
    public Stream<T> takeWhile(final Predicate<? super T> predicate) {
        return newStream(new ObjIteratorEx<T>() {
            private boolean hasMore = true;
            private boolean hasNext = false;
            private T next = null;

            @Override
            public boolean hasNext() {
                if (hasNext == false && hasMore && elements.hasNext()) {
                    next = elements.next();

                    if (predicate.test(next)) {
                        hasNext = true;
                    } else {
                        hasMore = false;
                    }
                }

                return hasNext;
            }

            @Override
            public T next() {
                if (hasNext == false && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                hasNext = false;

                return next;
            }

        }, sorted, cmp);
    }

    @Override
    public Stream<T> dropWhile(final Predicate<? super T> predicate) {
        return newStream(new ObjIteratorEx<T>() {
            private boolean hasNext = false;
            private T next = null;
            private boolean dropped = false;

            @Override
            public boolean hasNext() {
                if (hasNext == false) {
                    if (dropped == false) {
                        while (elements.hasNext()) {
                            next = elements.next();

                            if (predicate.test(next) == false) {
                                hasNext = true;
                                break;
                            }
                        }

                        dropped = true;
                    } else if (elements.hasNext()) {
                        next = elements.next();
                        hasNext = true;
                    }
                }

                return hasNext;
            }

            @Override
            public T next() {
                if (hasNext == false && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                hasNext = false;

                return next;
            }

        }, sorted, cmp);
    }

    @Override
    public <R> Stream<R> map(final Function<? super T, ? extends R> mapper) {
        return newStream(new ObjIteratorEx<R>() {
            @Override
            public boolean hasNext() {
                return elements.hasNext();
            }

            @Override
            public R next() {
                return mapper.apply(elements.next());
            }

            //            @Override
            //            public long count() {
            //                return elements.count();
            //            }
            //
            //            @Override
            //            public void skip(long n) {
            //                elements.skip(n);
            //            }
        }, false, null);
    }

    //    @Override
    //    public <R> Stream<R> biMap(final BiFunction<? super T, ? super T, ? extends R> mapper, final boolean ignoreNotPaired) {
    //        return newStream(new ObjIteratorEx<R>() {
    //            private T pre = (T) NONE;
    //
    //            @Override
    //            public boolean hasNext() {
    //                if (ignoreNotPaired && pre == NONE) {
    //                    if (elements.hasNext()) {
    //                        pre = elements.next();
    //                    } else {
    //                        return false;
    //                    }
    //                }
    //
    //                return elements.hasNext();
    //            }
    //
    //            @Override
    //            public R next() {
    //                if (!hasNext()) {
    //                    throw new NoSuchElementException();
    //                }
    //
    //                if (ignoreNotPaired) {
    //                    final R res = mapper.apply(pre, elements.next());
    //                    pre = (T) NONE;
    //                    return res;
    //                } else {
    //                    return mapper.apply(elements.next(), elements.hasNext() ? elements.next() : null);
    //                }
    //            }
    //
    //            //            @Override
    //            //            public void skip(long n) {
    //            //                elements.skip(n >= Long.MAX_VALUE / 2 ? Long.MAX_VALUE : n * 2);
    //            //            }
    //            //
    //            //            @Override
    //            //            public long count() {
    //            //                final long count = elements.count();
    //            //                return count % 2 == 0 || ignoreNotPaired ? count / 2 : count / 2 + 1;
    //            //            }
    //        }, closeHandlers);
    //    }
    //
    //    @Override
    //    public <R> Stream<R> triMap(final TriFunction<? super T, ? super T, ? super T, ? extends R> mapper, final boolean ignoreNotPaired) {
    //        return newStream(new ObjIteratorEx<R>() {
    //            private T prepre = (T) NONE;
    //            private T pre = (T) NONE;
    //
    //            @Override
    //            public boolean hasNext() {
    //                if (ignoreNotPaired && pre == NONE) {
    //                    if (elements.hasNext()) {
    //                        prepre = elements.next();
    //
    //                        if (elements.hasNext()) {
    //                            pre = elements.next();
    //                        } else {
    //                            return false;
    //                        }
    //                    } else {
    //                        return false;
    //                    }
    //                }
    //
    //                return elements.hasNext();
    //            }
    //
    //            @Override
    //            public R next() {
    //                if (!hasNext()) {
    //                    throw new NoSuchElementException();
    //                }
    //
    //                if (ignoreNotPaired) {
    //                    final R res = mapper.apply(prepre, pre, elements.next());
    //                    prepre = (T) NONE;
    //                    pre = (T) NONE;
    //                    return res;
    //                } else {
    //                    return mapper.apply(elements.next(), elements.hasNext() ? elements.next() : null, elements.hasNext() ? elements.next() : null);
    //                }
    //            }
    //
    //            //            @Override
    //            //            public void skip(long n) {
    //            //                elements.skip(n >= Long.MAX_VALUE / 3 ? Long.MAX_VALUE : n * 3);
    //            //            }
    //            //
    //            //            @Override
    //            //            public long count() {
    //            //                final long count = elements.count();
    //            //                return count % 3 == 0 || ignoreNotPaired ? count / 3 : count / 3 + 1;
    //            //            }
    //        }, closeHandlers);
    //    }

    @Override
    public <R> Stream<R> slidingMap(final BiFunction<? super T, ? super T, R> mapper, final int increment, final boolean ignoreNotPaired) {
        final int windowSize = 2;

        N.checkArgument(windowSize > 0 && increment > 0, "'windowSize'=%s and 'increment'=%s must not be less than 1", windowSize, increment);

        return newStream(new ObjIteratorEx<R>() {
            @SuppressWarnings("unchecked")
            private final T NONE = (T) Stream.NONE;
            private T prev = NONE;
            private T _1 = NONE;

            @Override
            public boolean hasNext() {
                if (increment > windowSize && prev != NONE) {
                    int skipNum = increment - windowSize;

                    while (skipNum-- > 0 && elements.hasNext()) {
                        elements.next();
                    }

                    prev = NONE;
                }

                if (ignoreNotPaired && _1 == NONE && elements.hasNext()) {
                    _1 = elements.next();
                }

                return elements.hasNext();
            }

            @Override
            public R next() {
                if (hasNext() == false) {
                    throw new NoSuchElementException();
                }

                if (ignoreNotPaired) {
                    final R res = mapper.apply(_1, (prev = elements.next()));
                    _1 = increment == 1 ? prev : NONE;
                    return res;
                } else {
                    if (increment == 1) {
                        return mapper.apply(prev == NONE ? elements.next() : prev, (prev = (elements.hasNext() ? elements.next() : null)));
                    } else {
                        return mapper.apply(elements.next(), (prev = (elements.hasNext() ? elements.next() : null)));
                    }
                }
            }
        }, false, null);
    }

    @Override
    public <R> Stream<R> slidingMap(final TriFunction<? super T, ? super T, ? super T, R> mapper, final int increment, final boolean ignoreNotPaired) {
        final int windowSize = 3;

        N.checkArgument(windowSize > 0 && increment > 0, "'windowSize'=%s and 'increment'=%s must not be less than 1", windowSize, increment);

        return newStream(new ObjIteratorEx<R>() {
            @SuppressWarnings("unchecked")
            private final T NONE = (T) Stream.NONE;
            private T prev = NONE;
            private T prev2 = NONE;
            private T _1 = NONE;
            private T _2 = NONE;

            @Override
            public boolean hasNext() {
                if (increment > windowSize && prev != NONE) {
                    int skipNum = increment - windowSize;

                    while (skipNum-- > 0 && elements.hasNext()) {
                        elements.next();
                    }

                    prev = NONE;
                }

                if (ignoreNotPaired) {
                    if (_1 == NONE && elements.hasNext()) {
                        _1 = elements.next();
                    }

                    if (_2 == NONE && elements.hasNext()) {
                        _2 = elements.next();
                    }
                }

                return elements.hasNext();
            }

            @Override
            public R next() {
                if (hasNext() == false) {
                    throw new NoSuchElementException();
                }

                if (ignoreNotPaired) {
                    final R res = mapper.apply(_1, _2, (prev = elements.next()));
                    _1 = increment == 1 ? _2 : (increment == 2 ? prev : NONE);
                    _2 = increment == 1 ? prev : NONE;
                    return res;
                } else {
                    if (increment == 1) {
                        return mapper.apply(prev2 == NONE ? elements.next() : prev2,
                                (prev2 = (prev == NONE ? (elements.hasNext() ? elements.next() : null) : prev)),
                                (prev = (elements.hasNext() ? elements.next() : null)));

                    } else if (increment == 2) {
                        return mapper.apply(prev == NONE ? elements.next() : prev, (prev2 = (elements.hasNext() ? elements.next() : null)),
                                (prev = (elements.hasNext() ? elements.next() : null)));
                    } else {
                        return mapper.apply(elements.next(), (prev2 = (elements.hasNext() ? elements.next() : null)),
                                (prev = (elements.hasNext() ? elements.next() : null)));
                    }
                }
            }
        }, false, null);
    }

    @Override
    public Stream<T> mapFirst(final Function<? super T, ? extends T> mapperForFirst) {
        N.checkArgNotNull(mapperForFirst);

        return newStream(new ObjIteratorEx<T>() {
            private boolean isFirst = true;

            @Override
            public boolean hasNext() {
                return elements.hasNext();
            }

            @Override
            public T next() {
                if (isFirst) {
                    isFirst = false;
                    return mapperForFirst.apply(elements.next());
                } else {
                    return elements.next();
                }
            }

            //            @Override
            //            public void skip(long n) {
            //                if (n > 0) {
            //                    isFirst = false;
            //                }
            //
            //                elements.skip(n);
            //            }
            //
            //            @Override
            //            public long count() {
            //                isFirst = false;
            //
            //                return elements.count();
            //            }

            @Override
            public void skip(long n) {
                if (n > 0) {
                    if (hasNext()) {
                        next();
                    }

                    elements.skip(n - 1);
                }
            }

            @Override
            public long count() {
                if (hasNext()) {
                    next();
                    return elements.count() + 1;
                }

                return 0;
            }
        }, false, null);
    }

    @Override
    public <R> Stream<R> mapFirstOrElse(final Function<? super T, ? extends R> mapperForFirst, final Function<? super T, ? extends R> mapperForElse) {
        N.checkArgNotNull(mapperForFirst);
        N.checkArgNotNull(mapperForElse);

        return newStream(new ObjIteratorEx<R>() {
            private boolean isFirst = true;

            @Override
            public boolean hasNext() {
                return elements.hasNext();
            }

            @Override
            public R next() {
                if (isFirst) {
                    isFirst = false;
                    return mapperForFirst.apply(elements.next());
                } else {
                    return mapperForElse.apply(elements.next());
                }
            }

            //            @Override
            //            public long count() {
            //                isFirst = false;
            //
            //                return elements.count();
            //            }
            //
            //            @Override
            //            public void skip(long n) {
            //                if (n > 0) {
            //                    isFirst = false;
            //                }
            //
            //                elements.skip(n);
            //            }
        }, false, null);
    }

    @Override
    public Stream<T> mapLast(final Function<? super T, ? extends T> mapperForLast) {
        N.checkArgNotNull(mapperForLast);

        return newStream(new ObjIteratorEx<T>() {
            @Override
            public boolean hasNext() {
                return elements.hasNext();
            }

            @Override
            public T next() {
                final T next = elements.next();

                if (elements.hasNext()) {
                    return next;
                } else {
                    return mapperForLast.apply(next);
                }
            }

            //            @Override
            //            public long count() {
            //                return elements.count();
            //            }
            //
            //            @Override
            //            public void skip(long n) {
            //                elements.skip(n);
            //            }
        }, false, null);
    }

    @Override
    public <R> Stream<R> mapLastOrElse(final Function<? super T, ? extends R> mapperForLast, final Function<? super T, ? extends R> mapperForElse) {
        N.checkArgNotNull(mapperForLast);
        N.checkArgNotNull(mapperForElse);

        return newStream(new ObjIteratorEx<R>() {
            @Override
            public boolean hasNext() {
                return elements.hasNext();
            }

            @Override
            public R next() {
                final T next = elements.next();

                if (elements.hasNext()) {
                    return mapperForElse.apply(next);
                } else {
                    return mapperForLast.apply(next);
                }
            }

            //            @Override
            //            public long count() {
            //                return elements.count();
            //            }
            //
            //            @Override
            //            public void skip(long n) {
            //                elements.skip(n);
            //            }
        }, false, null);
    }

    @Override
    public CharStream mapToChar(final ToCharFunction<? super T> mapper) {
        return newStream(new CharIteratorEx() {
            @Override
            public boolean hasNext() {
                return elements.hasNext();
            }

            @Override
            public char nextChar() {
                return mapper.applyAsChar(elements.next());
            }

            //            @Override
            //            public long count() {
            //                return elements.count();
            //            }
            //
            //            @Override
            //            public void skip(long n) {
            //                elements.skip(n);
            //            }
        }, false);
    }

    @Override
    public ByteStream mapToByte(final ToByteFunction<? super T> mapper) {
        return newStream(new ByteIteratorEx() {
            @Override
            public boolean hasNext() {
                return elements.hasNext();
            }

            @Override
            public byte nextByte() {
                return mapper.applyAsByte(elements.next());
            }

            //            @Override
            //            public long count() {
            //                return elements.count();
            //            }
            //
            //            @Override
            //            public void skip(long n) {
            //                elements.skip(n);
            //            }
        }, false);
    }

    @Override
    public ShortStream mapToShort(final ToShortFunction<? super T> mapper) {
        return newStream(new ShortIteratorEx() {
            @Override
            public boolean hasNext() {
                return elements.hasNext();
            }

            @Override
            public short nextShort() {
                return mapper.applyAsShort(elements.next());
            }

            //            @Override
            //            public long count() {
            //                return elements.count();
            //            }
            //
            //            @Override
            //            public void skip(long n) {
            //                elements.skip(n);
            //            }
        }, false);
    }

    @Override
    public IntStream mapToInt(final ToIntFunction<? super T> mapper) {
        return newStream(new IntIteratorEx() {
            @Override
            public boolean hasNext() {
                return elements.hasNext();
            }

            @Override
            public int nextInt() {
                return mapper.applyAsInt(elements.next());
            }

            //            @Override
            //            public long count() {
            //                return elements.count();
            //            }
            //
            //            @Override
            //            public void skip(long n) {
            //                elements.skip(n);
            //            }
        }, false);
    }

    @Override
    public LongStream mapToLong(final ToLongFunction<? super T> mapper) {
        return newStream(new LongIteratorEx() {
            @Override
            public boolean hasNext() {
                return elements.hasNext();
            }

            @Override
            public long nextLong() {
                return mapper.applyAsLong(elements.next());
            }

            //            @Override
            //            public long count() {
            //                return elements.count();
            //            }
            //
            //            @Override
            //            public void skip(long n) {
            //                elements.skip(n);
            //            }
        }, false);
    }

    @Override
    public FloatStream mapToFloat(final ToFloatFunction<? super T> mapper) {
        return newStream(new FloatIteratorEx() {
            @Override
            public boolean hasNext() {
                return elements.hasNext();
            }

            @Override
            public float nextFloat() {
                return mapper.applyAsFloat(elements.next());
            }

            //            @Override
            //            public long count() {
            //                return elements.count();
            //            }
            //
            //            @Override
            //            public void skip(long n) {
            //                elements.skip(n);
            //            }
        }, false);
    }

    @Override
    public DoubleStream mapToDouble(final ToDoubleFunction<? super T> mapper) {
        return newStream(new DoubleIteratorEx() {
            @Override
            public boolean hasNext() {
                return elements.hasNext();
            }

            @Override
            public double nextDouble() {
                return mapper.applyAsDouble(elements.next());
            }

            //            @Override
            //            public long count() {
            //                return elements.count();
            //            }
            //
            //            @Override
            //            public void skip(long n) {
            //                elements.skip(n);
            //            }
        }, false);
    }

    @Override
    public <R> Stream<R> flatMap(final Function<? super T, ? extends Stream<? extends R>> mapper) {
        final ObjIteratorEx<R> iter = new ObjIteratorEx<R>() {
            private Iterator<? extends R> cur = null;
            private Stream<? extends R> s = null;
            private Runnable closeHandle = null;

            @Override
            public boolean hasNext() {
                while ((cur == null || cur.hasNext() == false) && elements.hasNext()) {
                    if (closeHandle != null) {
                        final Runnable tmp = closeHandle;
                        closeHandle = null;
                        tmp.run();
                    }

                    s = mapper.apply(elements.next());

                    if (N.notNullOrEmpty(s.closeHandlers)) {
                        final Set<Runnable> tmp = s.closeHandlers;

                        closeHandle = new Runnable() {
                            @Override
                            public void run() {
                                Stream.close(tmp);
                            }
                        };
                    }

                    cur = s.iterator();
                }

                return cur != null && cur.hasNext();
            }

            @Override
            public R next() {
                if ((cur == null || cur.hasNext() == false) && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                return cur.next();
            }

            @Override
            public void close() {
                if (closeHandle != null) {
                    final Runnable tmp = closeHandle;
                    closeHandle = null;
                    tmp.run();
                }
            }
        };

        final Set<Runnable> newCloseHandlers = N.isNullOrEmpty(closeHandlers) ? new LocalLinkedHashSet<Runnable>(1)
                : new LocalLinkedHashSet<Runnable>(closeHandlers);

        newCloseHandlers.add(new Runnable() {
            @Override
            public void run() {
                iter.close();
            }
        });

        return new IteratorStream<>(iter, newCloseHandlers);
    }

    @Override
    public CharStream flatMapToChar(final Function<? super T, ? extends CharStream> mapper) {
        final CharIteratorEx iter = new CharIteratorEx() {
            private CharIterator cur = null;
            private CharStream s = null;
            private Runnable closeHandle = null;

            @Override
            public boolean hasNext() {
                while ((cur == null || cur.hasNext() == false) && elements.hasNext()) {
                    if (closeHandle != null) {
                        final Runnable tmp = closeHandle;
                        closeHandle = null;
                        tmp.run();
                    }

                    s = mapper.apply(elements.next());

                    if (N.notNullOrEmpty(s.closeHandlers)) {
                        final Set<Runnable> tmp = s.closeHandlers;

                        closeHandle = new Runnable() {
                            @Override
                            public void run() {
                                Stream.close(tmp);
                            }
                        };
                    }

                    cur = s.iterator();
                }

                return cur != null && cur.hasNext();
            }

            @Override
            public char nextChar() {
                if ((cur == null || cur.hasNext() == false) && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                return cur.nextChar();
            }

            @Override
            public void close() {
                if (closeHandle != null) {
                    final Runnable tmp = closeHandle;
                    closeHandle = null;
                    tmp.run();
                }
            }
        };

        final Set<Runnable> newCloseHandlers = N.isNullOrEmpty(closeHandlers) ? new LocalLinkedHashSet<Runnable>(1)
                : new LocalLinkedHashSet<Runnable>(closeHandlers);

        newCloseHandlers.add(new Runnable() {
            @Override
            public void run() {
                iter.close();
            }
        });

        return new IteratorCharStream(iter, newCloseHandlers);
    }

    @Override
    public ByteStream flatMapToByte(final Function<? super T, ? extends ByteStream> mapper) {
        final ByteIteratorEx iter = new ByteIteratorEx() {
            private ByteIterator cur = null;
            private ByteStream s = null;
            private Runnable closeHandle = null;

            @Override
            public boolean hasNext() {
                while ((cur == null || cur.hasNext() == false) && elements.hasNext()) {
                    if (closeHandle != null) {
                        final Runnable tmp = closeHandle;
                        closeHandle = null;
                        tmp.run();
                    }

                    s = mapper.apply(elements.next());

                    if (N.notNullOrEmpty(s.closeHandlers)) {
                        final Set<Runnable> tmp = s.closeHandlers;

                        closeHandle = new Runnable() {
                            @Override
                            public void run() {
                                Stream.close(tmp);
                            }
                        };
                    }

                    cur = s.iterator();
                }

                return cur != null && cur.hasNext();
            }

            @Override
            public byte nextByte() {
                if ((cur == null || cur.hasNext() == false) && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                return cur.nextByte();
            }

            @Override
            public void close() {
                if (closeHandle != null) {
                    final Runnable tmp = closeHandle;
                    closeHandle = null;
                    tmp.run();
                }
            }
        };

        final Set<Runnable> newCloseHandlers = N.isNullOrEmpty(closeHandlers) ? new LocalLinkedHashSet<Runnable>(1)
                : new LocalLinkedHashSet<Runnable>(closeHandlers);

        newCloseHandlers.add(new Runnable() {
            @Override
            public void run() {
                iter.close();
            }
        });

        return new IteratorByteStream(iter, newCloseHandlers);
    }

    @Override
    public ShortStream flatMapToShort(final Function<? super T, ? extends ShortStream> mapper) {
        final ShortIteratorEx iter = new ShortIteratorEx() {
            private ShortIterator cur = null;
            private ShortStream s = null;
            private Runnable closeHandle = null;

            @Override
            public boolean hasNext() {
                while ((cur == null || cur.hasNext() == false) && elements.hasNext()) {
                    if (closeHandle != null) {
                        final Runnable tmp = closeHandle;
                        closeHandle = null;
                        tmp.run();
                    }

                    s = mapper.apply(elements.next());

                    if (N.notNullOrEmpty(s.closeHandlers)) {
                        final Set<Runnable> tmp = s.closeHandlers;

                        closeHandle = new Runnable() {
                            @Override
                            public void run() {
                                Stream.close(tmp);
                            }
                        };
                    }

                    cur = s.iterator();
                }

                return cur != null && cur.hasNext();
            }

            @Override
            public short nextShort() {
                if ((cur == null || cur.hasNext() == false) && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                return cur.nextShort();
            }

            @Override
            public void close() {
                if (closeHandle != null) {
                    final Runnable tmp = closeHandle;
                    closeHandle = null;
                    tmp.run();
                }
            }
        };

        final Set<Runnable> newCloseHandlers = N.isNullOrEmpty(closeHandlers) ? new LocalLinkedHashSet<Runnable>(1)
                : new LocalLinkedHashSet<Runnable>(closeHandlers);

        newCloseHandlers.add(new Runnable() {
            @Override
            public void run() {
                iter.close();
            }
        });

        return new IteratorShortStream(iter, newCloseHandlers);
    }

    @Override
    public IntStream flatMapToInt(final Function<? super T, ? extends IntStream> mapper) {
        final IntIteratorEx iter = new IntIteratorEx() {
            private IntIterator cur = null;
            private IntStream s = null;
            private Runnable closeHandle = null;

            @Override
            public boolean hasNext() {
                while ((cur == null || cur.hasNext() == false) && elements.hasNext()) {
                    if (closeHandle != null) {
                        final Runnable tmp = closeHandle;
                        closeHandle = null;
                        tmp.run();
                    }

                    s = mapper.apply(elements.next());

                    if (N.notNullOrEmpty(s.closeHandlers)) {
                        final Set<Runnable> tmp = s.closeHandlers;

                        closeHandle = new Runnable() {
                            @Override
                            public void run() {
                                Stream.close(tmp);
                            }
                        };
                    }

                    cur = s.iterator();
                }

                return cur != null && cur.hasNext();
            }

            @Override
            public int nextInt() {
                if ((cur == null || cur.hasNext() == false) && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                return cur.nextInt();
            }

            @Override
            public void close() {
                if (closeHandle != null) {
                    final Runnable tmp = closeHandle;
                    closeHandle = null;
                    tmp.run();
                }
            }
        };

        final Set<Runnable> newCloseHandlers = N.isNullOrEmpty(closeHandlers) ? new LocalLinkedHashSet<Runnable>(1)
                : new LocalLinkedHashSet<Runnable>(closeHandlers);

        newCloseHandlers.add(new Runnable() {
            @Override
            public void run() {
                iter.close();
            }
        });

        return new IteratorIntStream(iter, newCloseHandlers);
    }

    @Override
    public LongStream flatMapToLong(final Function<? super T, ? extends LongStream> mapper) {
        final LongIteratorEx iter = new LongIteratorEx() {
            private LongIterator cur = null;
            private LongStream s = null;
            private Runnable closeHandle = null;

            @Override
            public boolean hasNext() {
                while ((cur == null || cur.hasNext() == false) && elements.hasNext()) {
                    if (closeHandle != null) {
                        final Runnable tmp = closeHandle;
                        closeHandle = null;
                        tmp.run();
                    }

                    s = mapper.apply(elements.next());

                    if (N.notNullOrEmpty(s.closeHandlers)) {
                        final Set<Runnable> tmp = s.closeHandlers;

                        closeHandle = new Runnable() {
                            @Override
                            public void run() {
                                Stream.close(tmp);
                            }
                        };
                    }

                    cur = s.iterator();
                }

                return cur != null && cur.hasNext();
            }

            @Override
            public long nextLong() {
                if ((cur == null || cur.hasNext() == false) && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                return cur.nextLong();
            }

            @Override
            public void close() {
                if (closeHandle != null) {
                    final Runnable tmp = closeHandle;
                    closeHandle = null;
                    tmp.run();
                }
            }
        };

        final Set<Runnable> newCloseHandlers = N.isNullOrEmpty(closeHandlers) ? new LocalLinkedHashSet<Runnable>(1)
                : new LocalLinkedHashSet<Runnable>(closeHandlers);

        newCloseHandlers.add(new Runnable() {
            @Override
            public void run() {
                iter.close();
            }
        });

        return new IteratorLongStream(iter, newCloseHandlers);
    }

    @Override
    public FloatStream flatMapToFloat(final Function<? super T, ? extends FloatStream> mapper) {
        final FloatIteratorEx iter = new FloatIteratorEx() {
            private FloatIterator cur = null;
            private FloatStream s = null;
            private Runnable closeHandle = null;

            @Override
            public boolean hasNext() {
                while ((cur == null || cur.hasNext() == false) && elements.hasNext()) {
                    if (closeHandle != null) {
                        final Runnable tmp = closeHandle;
                        closeHandle = null;
                        tmp.run();
                    }

                    s = mapper.apply(elements.next());

                    if (N.notNullOrEmpty(s.closeHandlers)) {
                        final Set<Runnable> tmp = s.closeHandlers;

                        closeHandle = new Runnable() {
                            @Override
                            public void run() {
                                Stream.close(tmp);
                            }
                        };
                    }

                    cur = s.iterator();
                }

                return cur != null && cur.hasNext();
            }

            @Override
            public float nextFloat() {
                if ((cur == null || cur.hasNext() == false) && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                return cur.nextFloat();
            }

            @Override
            public void close() {
                if (closeHandle != null) {
                    final Runnable tmp = closeHandle;
                    closeHandle = null;
                    tmp.run();
                }
            }
        };

        final Set<Runnable> newCloseHandlers = N.isNullOrEmpty(closeHandlers) ? new LocalLinkedHashSet<Runnable>(1)
                : new LocalLinkedHashSet<Runnable>(closeHandlers);

        newCloseHandlers.add(new Runnable() {
            @Override
            public void run() {
                iter.close();
            }
        });

        return new IteratorFloatStream(iter, newCloseHandlers);
    }

    @Override
    public DoubleStream flatMapToDouble(final Function<? super T, ? extends DoubleStream> mapper) {
        final DoubleIteratorEx iter = new DoubleIteratorEx() {
            private DoubleIterator cur = null;
            private DoubleStream s = null;
            private Runnable closeHandle = null;

            @Override
            public boolean hasNext() {
                while ((cur == null || cur.hasNext() == false) && elements.hasNext()) {
                    if (closeHandle != null) {
                        final Runnable tmp = closeHandle;
                        closeHandle = null;
                        tmp.run();
                    }

                    s = mapper.apply(elements.next());

                    if (N.notNullOrEmpty(s.closeHandlers)) {
                        final Set<Runnable> tmp = s.closeHandlers;

                        closeHandle = new Runnable() {
                            @Override
                            public void run() {
                                Stream.close(tmp);
                            }
                        };
                    }

                    cur = s.iterator();
                }

                return cur != null && cur.hasNext();
            }

            @Override
            public double nextDouble() {
                if ((cur == null || cur.hasNext() == false) && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                return cur.nextDouble();
            }

            @Override
            public void close() {
                if (closeHandle != null) {
                    final Runnable tmp = closeHandle;
                    closeHandle = null;
                    tmp.run();
                }
            }
        };

        final Set<Runnable> newCloseHandlers = N.isNullOrEmpty(closeHandlers) ? new LocalLinkedHashSet<Runnable>(1)
                : new LocalLinkedHashSet<Runnable>(closeHandlers);

        newCloseHandlers.add(new Runnable() {
            @Override
            public void run() {
                iter.close();
            }
        });

        return new IteratorDoubleStream(iter, newCloseHandlers);
    }

    @Override
    public Stream<List<T>> splitToList(final int size) {
        N.checkArgPositive(size, "size");

        return newStream(new ObjIteratorEx<List<T>>() {
            @Override
            public boolean hasNext() {
                return elements.hasNext();
            }

            @Override
            public List<T> next() {
                if (hasNext() == false) {
                    throw new NoSuchElementException();
                }

                final List<T> result = new ArrayList<>(size);
                int cnt = 0;

                while (cnt < size && elements.hasNext()) {
                    result.add(elements.next());
                    cnt++;
                }

                return result;
            }

            @Override
            public long count() {
                final long len = elements.count();
                return len % size == 0 ? len / size : len / size + 1;
            }

            @Override
            public void skip(long n) {
                elements.skip(n >= Long.MAX_VALUE / size ? Long.MAX_VALUE : n * size);
            }
        }, false, null);
    }

    @Override
    public Stream<Set<T>> splitToSet(final int size) {
        N.checkArgPositive(size, "size");

        return newStream(new ObjIteratorEx<Set<T>>() {
            @Override
            public boolean hasNext() {
                return elements.hasNext();
            }

            @Override
            public Set<T> next() {
                if (hasNext() == false) {
                    throw new NoSuchElementException();
                }

                final Set<T> result = new HashSet<>(N.min(9, size));
                int cnt = 0;

                while (cnt < size && elements.hasNext()) {
                    result.add(elements.next());
                    cnt++;
                }

                return result;
            }

            @Override
            public long count() {
                final long len = elements.count();
                return len % size == 0 ? len / size : len / size + 1;
            }

            @Override
            public void skip(long n) {
                elements.skip(n >= Long.MAX_VALUE / size ? Long.MAX_VALUE : n * size);
            }
        }, false, null);
    }

    @Override
    public Stream<List<T>> splitToList(final Predicate<? super T> predicate) {
        return newStream(new ObjIteratorEx<List<T>>() {
            private T next = (T) NONE;
            private boolean preCondition = false;

            @Override
            public boolean hasNext() {
                return next != NONE || elements.hasNext();
            }

            @Override
            public List<T> next() {
                if (hasNext() == false) {
                    throw new NoSuchElementException();
                }

                final List<T> result = new ArrayList<>();

                if (next == NONE) {
                    next = elements.next();
                }

                while (next != NONE) {
                    if (result.size() == 0) {
                        result.add(next);
                        preCondition = predicate.test(next);
                        next = elements.hasNext() ? elements.next() : (T) NONE;
                    } else if (predicate.test(next) == preCondition) {
                        result.add(next);
                        next = elements.hasNext() ? elements.next() : (T) NONE;
                    } else {

                        break;
                    }
                }

                return result;
            }

        }, false, null);
    }

    @Override
    public <U> Stream<List<T>> splitToList(final U seed, final BiPredicate<? super T, ? super U> predicate, final Consumer<? super U> seedUpdate) {
        return newStream(new ObjIteratorEx<List<T>>() {
            private T next = (T) NONE;
            private boolean preCondition = false;

            @Override
            public boolean hasNext() {
                return next != NONE || elements.hasNext();
            }

            @Override
            public List<T> next() {
                if (hasNext() == false) {
                    throw new NoSuchElementException();
                }

                final List<T> result = new ArrayList<>();

                if (next == NONE) {
                    next = elements.next();
                }

                while (next != NONE) {
                    if (result.size() == 0) {
                        result.add(next);
                        preCondition = predicate.test(next, seed);
                        next = elements.hasNext() ? elements.next() : (T) NONE;
                    } else if (predicate.test(next, seed) == preCondition) {
                        result.add(next);
                        next = elements.hasNext() ? elements.next() : (T) NONE;
                    } else {
                        if (seedUpdate != null) {
                            seedUpdate.accept(seed);
                        }

                        break;
                    }
                }

                return result;
            }

        }, false, null);
    }

    @Override
    public <U> Stream<Set<T>> splitToSet(final U seed, final BiPredicate<? super T, ? super U> predicate, final Consumer<? super U> seedUpdate) {
        return newStream(new ObjIteratorEx<Set<T>>() {
            private T next = (T) NONE;
            private boolean preCondition = false;

            @Override
            public boolean hasNext() {
                return next != NONE || elements.hasNext();
            }

            @Override
            public Set<T> next() {
                if (hasNext() == false) {
                    throw new NoSuchElementException();
                }

                final Set<T> result = new HashSet<>();

                if (next == NONE) {
                    next = elements.next();
                }

                while (next != NONE) {
                    if (result.size() == 0) {
                        result.add(next);
                        preCondition = predicate.test(next, seed);
                        next = elements.hasNext() ? elements.next() : (T) NONE;
                    } else if (predicate.test(next, seed) == preCondition) {
                        result.add(next);
                        next = elements.hasNext() ? elements.next() : (T) NONE;
                    } else {
                        if (seedUpdate != null) {
                            seedUpdate.accept(seed);
                        }

                        break;
                    }
                }

                return result;
            }

        }, false, null);
    }

    @Override
    public Stream<List<T>> slidingToList(final int windowSize, final int increment) {
        N.checkArgument(windowSize > 0 && increment > 0, "'windowSize'=%s and 'increment'=%s must not be less than 1", windowSize, increment);

        return newStream(new ObjIteratorEx<List<T>>() {
            private List<T> prev = null;

            @Override
            public boolean hasNext() {
                if (prev != null && increment > windowSize) {
                    int skipNum = increment - windowSize;

                    while (skipNum-- > 0 && elements.hasNext()) {
                        elements.next();
                    }

                    prev = null;
                }

                return elements.hasNext();
            }

            @Override
            public List<T> next() {
                if (hasNext() == false) {
                    throw new NoSuchElementException();
                }

                final List<T> result = new ArrayList<>(windowSize);
                int cnt = 0;

                if (prev != null && increment < windowSize) {
                    cnt = windowSize - increment;

                    if (cnt <= 8) {
                        for (int i = windowSize - cnt; i < windowSize; i++) {
                            result.add(prev.get(i));
                        }
                    } else {
                        result.addAll(prev.subList(windowSize - cnt, windowSize));
                    }
                }

                while (cnt++ < windowSize && elements.hasNext()) {
                    result.add(elements.next());
                }

                return prev = result;
            }
        }, false, null);
    }

    @Override
    public Stream<T> top(final int n, final Comparator<? super T> comparator) {
        N.checkArgument(n > 0, "'n' must be bigger than 0");

        return newStream(new ObjIteratorEx<T>() {
            private boolean initialized = false;
            private T[] aar = null;
            private int cursor = 0;
            private int to;

            @Override
            public boolean hasNext() {
                if (initialized == false) {
                    init();
                }

                return cursor < to;
            }

            @Override
            public T next() {
                if (initialized == false) {
                    init();
                }

                if (cursor >= to) {
                    throw new NoSuchElementException();
                }

                return aar[cursor++];
            }

            @Override
            public long count() {
                if (initialized == false) {
                    init();
                }

                return to - cursor;
            }

            @Override
            public void skip(long n) {
                if (initialized == false) {
                    init();
                }

                cursor = n < to - cursor ? cursor + (int) n : to;
            }

            @Override
            public <A> A[] toArray(A[] b) {
                if (initialized == false) {
                    init();
                }

                b = b.length >= to - cursor ? b : (A[]) N.newArray(b.getClass().getComponentType(), to - cursor);

                N.copy(aar, cursor, b, 0, to - cursor);

                return b;
            }

            private void init() {
                if (initialized == false) {
                    initialized = true;
                    if (sorted && isSameComparator(comparator, cmp)) {
                        final LinkedList<T> queue = new LinkedList<>();

                        while (elements.hasNext()) {
                            if (queue.size() >= n) {
                                queue.poll();
                            }

                            queue.offer(elements.next());
                        }

                        aar = (T[]) queue.toArray();
                    } else {
                        final Queue<T> heap = new PriorityQueue<>(n, comparator);

                        T next = null;
                        while (elements.hasNext()) {
                            next = elements.next();

                            if (heap.size() >= n) {
                                if (comparator.compare(next, heap.peek()) > 0) {
                                    heap.poll();
                                    heap.offer(next);
                                }
                            } else {
                                heap.offer(next);
                            }
                        }

                        aar = (T[]) heap.toArray();
                    }

                    to = aar.length;
                }
            }
        }, false, null);
    }

    @Override
    public Stream<T> peek(final Consumer<? super T> action) {
        return newStream(new ObjIteratorEx<T>() {
            @Override
            public boolean hasNext() {
                return elements.hasNext();
            }

            @Override
            public T next() {
                final T next = elements.next();
                action.accept(next);
                return next;
            }
        }, sorted, cmp);
    }

    @Override
    public Stream<T> limit(final long maxSize) {
        N.checkArgNotNegative(maxSize, "maxSize");

        return newStream(new ObjIteratorEx<T>() {
            private long cnt = 0;

            @Override
            public boolean hasNext() {
                return cnt < maxSize && elements.hasNext();
            }

            @Override
            public T next() {
                if (cnt >= maxSize) {
                    throw new NoSuchElementException();
                }

                cnt++;
                return elements.next();
            }

            @Override
            public void skip(long n) {
                elements.skip(n);
            }
        }, sorted, cmp);
    }

    @Override
    public Stream<T> skip(final long n) {
        N.checkArgNotNegative(n, "n");

        if (n == 0) {
            return this;
        }

        return newStream(new ObjIteratorEx<T>() {
            private boolean skipped = false;

            @Override
            public boolean hasNext() {
                if (skipped == false) {
                    elements.skip(n);
                    skipped = true;
                }

                return elements.hasNext();
            }

            @Override
            public T next() {
                if (skipped == false) {
                    elements.skip(n);
                    skipped = true;
                }

                return elements.next();
            }

            @Override
            public long count() {
                if (skipped == false) {
                    elements.skip(n);
                    skipped = true;
                }

                return elements.count();
            }

            @Override
            public void skip(long n2) {
                if (skipped == false) {
                    elements.skip(n);
                    skipped = true;
                }

                elements.skip(n2);
            }

            @Override
            public <A> A[] toArray(A[] a) {
                if (skipped == false) {
                    elements.skip(n);
                    skipped = true;
                }

                return elements.toArray(a);
            }
        }, sorted, cmp);
    }

    @Override
    public <E extends Exception> void forEach(Try.Consumer<? super T, E> action) throws E {
        while (elements.hasNext()) {
            action.accept(elements.next());
        }
    }

    @Override
    public <E extends Exception> void forEachPair(final Try.BiConsumer<? super T, ? super T, E> action, final int increment) throws E {
        final int windowSize = 2;

        N.checkArgument(windowSize > 0 && increment > 0, "'windowSize'=%s and 'increment'=%s must not be less than 1", windowSize, increment);

        T prev = (T) NONE;

        while (elements.hasNext()) {
            if (increment > windowSize && prev != NONE) {
                int skipNum = increment - windowSize;

                while (skipNum-- > 0 && elements.hasNext()) {
                    elements.next();
                }

                if (elements.hasNext() == false) {
                    break;
                }

                prev = (T) NONE;
            }

            if (increment == 1) {
                action.accept(prev == NONE ? elements.next() : prev, (prev = (elements.hasNext() ? elements.next() : null)));
            } else {
                action.accept(elements.next(), (prev = (elements.hasNext() ? elements.next() : null)));
            }
        }
    }

    @Override
    public <E extends Exception> void forEachTriple(final Try.TriConsumer<? super T, ? super T, ? super T, E> action, final int increment) throws E {
        final int windowSize = 3;

        N.checkArgument(windowSize > 0 && increment > 0, "'windowSize'=%s and 'increment'=%s must not be less than 1", windowSize, increment);

        T prev = (T) NONE;
        T prev2 = (T) NONE;

        while (elements.hasNext()) {
            if (increment > windowSize && prev != NONE) {
                int skipNum = increment - windowSize;

                while (skipNum-- > 0 && elements.hasNext()) {
                    elements.next();
                }

                if (elements.hasNext() == false) {
                    break;
                }

                prev = (T) NONE;
            }

            if (increment == 1) {
                action.accept(prev2 == NONE ? elements.next() : prev2, (prev2 = (prev == NONE ? (elements.hasNext() ? elements.next() : null) : prev)),
                        (prev = (elements.hasNext() ? elements.next() : null)));

            } else if (increment == 2) {
                action.accept(prev == NONE ? elements.next() : prev, (prev2 = (elements.hasNext() ? elements.next() : null)),
                        (prev = (elements.hasNext() ? elements.next() : null)));
            } else {
                action.accept(elements.next(), (prev2 = (elements.hasNext() ? elements.next() : null)), (prev = (elements.hasNext() ? elements.next() : null)));
            }
        }
    }

    @Override
    public Object[] toArray() {
        return toArray(N.EMPTY_OBJECT_ARRAY);
    }

    <A> A[] toArray(A[] a) {
        return elements.toArray(a);
    }

    @Override
    public List<T> toList() {
        final List<T> result = new ArrayList<>();

        while (elements.hasNext()) {
            result.add(elements.next());
        }

        return result;
    }

    @Override
    public Set<T> toSet() {
        final Set<T> result = new HashSet<>();

        while (elements.hasNext()) {
            result.add(elements.next());
        }

        return result;
    }

    @Override
    public <C extends Collection<T>> C toCollection(Supplier<? extends C> supplier) {
        final C result = supplier.get();

        while (elements.hasNext()) {
            result.add(elements.next());
        }

        return result;
    }

    @Override
    public Multiset<T> toMultiset() {
        final Multiset<T> result = new Multiset<>();

        while (elements.hasNext()) {
            result.add(elements.next());
        }

        return result;
    }

    @Override
    public Multiset<T> toMultiset(Supplier<? extends Multiset<T>> supplier) {
        final Multiset<T> result = supplier.get();

        while (elements.hasNext()) {
            result.add(elements.next());
        }

        return result;
    }

    @Override
    public LongMultiset<T> toLongMultiset() {
        final LongMultiset<T> result = new LongMultiset<>();

        while (elements.hasNext()) {
            result.add(elements.next());
        }

        return result;
    }

    @Override
    public LongMultiset<T> toLongMultiset(Supplier<? extends LongMultiset<T>> supplier) {
        final LongMultiset<T> result = supplier.get();

        while (elements.hasNext()) {
            result.add(elements.next());
        }

        return result;
    }

    @Override
    public <K, U, M extends Map<K, U>> M toMap(Function<? super T, ? extends K> keyExtractor, Function<? super T, ? extends U> valueMapper,
            BinaryOperator<U> mergeFunction, Supplier<M> mapFactory) {
        final M result = mapFactory.get();
        T element = null;

        while (elements.hasNext()) {
            element = elements.next();
            Collectors.merge(result, keyExtractor.apply(element), valueMapper.apply(element), mergeFunction);
        }

        return result;
    }

    @Override
    public <K, A, D, M extends Map<K, D>> M toMap(final Function<? super T, ? extends K> classifier, final Collector<? super T, A, D> downstream,
            final Supplier<M> mapFactory) {
        final M result = mapFactory.get();
        final Supplier<A> downstreamSupplier = downstream.supplier();
        final BiConsumer<A, ? super T> downstreamAccumulator = downstream.accumulator();
        final Map<K, A> intermediate = (Map<K, A>) result;
        K key = null;
        A v = null;
        T element = null;

        while (elements.hasNext()) {
            element = elements.next();
            key = N.checkArgNotNull(classifier.apply(element), "element cannot be mapped to a null key");

            if ((v = intermediate.get(key)) == null) {
                if ((v = downstreamSupplier.get()) != null) {
                    intermediate.put(key, v);
                }
            }

            downstreamAccumulator.accept(v, element);
        }

        final BiFunction<? super K, ? super A, ? extends A> function = new BiFunction<K, A, A>() {
            @Override
            public A apply(K k, A v) {
                return (A) downstream.finisher().apply(v);
            }
        };

        Collectors.replaceAll(intermediate, function);

        return result;
    }

    @Override
    public <K, U, V extends Collection<U>, M extends Multimap<K, U, V>> M toMultimap(Function<? super T, ? extends K> keyExtractor,
            Function<? super T, ? extends U> valueMapper, Supplier<M> mapFactory) {
        final M result = mapFactory.get();
        T element = null;

        while (elements.hasNext()) {
            element = elements.next();
            result.put(keyExtractor.apply(element), valueMapper.apply(element));
        }

        return result;
    }

    @Override
    public T reduce(T identity, BinaryOperator<T> accumulator) {
        T result = identity;

        while (elements.hasNext()) {
            result = accumulator.apply(result, elements.next());
        }

        return result;
    }

    @Override
    public Optional<T> reduce(BinaryOperator<T> accumulator) {
        if (elements.hasNext() == false) {
            return Optional.empty();
        }

        T result = elements.next();

        while (elements.hasNext()) {
            result = accumulator.apply(result, elements.next());
        }

        return Optional.of(result);
    }

    @Override
    public <U> U reduce(U identity, BiFunction<U, ? super T, U> accumulator, BinaryOperator<U> combiner) {
        U result = identity;

        while (elements.hasNext()) {
            result = accumulator.apply(result, elements.next());
        }

        return result;
    }

    @Override
    public <R> R collect(Supplier<R> supplier, BiConsumer<R, ? super T> accumulator, BiConsumer<R, R> combiner) {
        final R result = supplier.get();

        while (elements.hasNext()) {
            accumulator.accept(result, elements.next());
        }

        return result;
    }

    @Override
    public <R, A> R collect(Collector<? super T, A, R> collector) {
        final A container = collector.supplier().get();
        final BiConsumer<A, ? super T> accumulator = collector.accumulator();

        while (elements.hasNext()) {
            accumulator.accept(container, elements.next());
        }

        return collector.finisher().apply(container);
    }

    @Override
    public Optional<T> head() {
        if (head == null) {
            head = elements.hasNext() ? Optional.of(elements.next()) : Optional.<T> empty();
            tail = newStream(elements, sorted, cmp);
        }

        return head;
    }

    @Override
    public Stream<T> tail() {
        if (tail == null) {
            head = elements.hasNext() ? Optional.of(elements.next()) : Optional.<T> empty();
            tail = newStream(elements, sorted, cmp);
        }

        return tail;
    }

    @Override
    public Stream<T> headd() {
        if (head2 == null) {
            final Object[] a = this.toArray();
            head2 = newStream((T[]) a, 0, a.length == 0 ? 0 : a.length - 1, sorted, cmp);
            tail2 = a.length == 0 ? Optional.<T> empty() : Optional.of((T) a[a.length - 1]);
        }

        return head2;
    }

    @Override
    public Optional<T> taill() {
        if (tail2 == null) {
            final Object[] a = this.toArray();
            head2 = newStream((T[]) a, 0, a.length == 0 ? 0 : a.length - 1, sorted, cmp);
            tail2 = a.length == 0 ? Optional.<T> empty() : Optional.of((T) a[a.length - 1]);
        }

        return tail2;
    }

    @Override
    public Stream<T> last(final int n) {
        N.checkArgNotNegative(n, "n");

        if (n == 0) {
            return new IteratorStream<>(ObjIteratorEx.EMPTY, sorted, cmp, closeHandlers);
        }

        final Deque<T> dqueue = n <= 1024 ? new ArrayDeque<T>(n) : new LinkedList<T>();

        while (elements.hasNext()) {
            if (dqueue.size() >= n) {
                dqueue.pollFirst();
            }

            dqueue.offerLast(elements.next());
        }

        return newStream(dqueue.iterator(), sorted, cmp);
    }

    @Override
    public Stream<T> skipLast(final int n) {
        N.checkArgNotNegative(n, "n");

        if (n == 0) {
            return this;
        }

        return newStream(new ObjIteratorEx<T>() {
            private Deque<T> dqueue = null;

            @Override
            public boolean hasNext() {
                if (dqueue == null) {
                    dqueue = n <= 1024 ? new ArrayDeque<T>(n) : new LinkedList<T>();

                    while (dqueue.size() < n && elements.hasNext()) {
                        dqueue.offerLast(elements.next());
                    }
                }

                return elements.hasNext();
            }

            @Override
            public T next() {
                if (hasNext() == false) {
                    throw new NoSuchElementException();
                }

                dqueue.offerLast(elements.next());

                return dqueue.pollFirst();
            }

        }, sorted, cmp);
    }

    @Override
    public Optional<T> min(Comparator<? super T> comparator) {
        if (elements.hasNext() == false) {
            return Optional.empty();
        } else if (sorted && isSameComparator(comparator, cmp)) {
            return Optional.of(elements.next());
        }

        comparator = comparator == null ? NATURAL_COMPARATOR : comparator;
        T candidate = elements.next();
        T next = null;

        while (elements.hasNext()) {
            next = elements.next();
            if (comparator.compare(next, candidate) < 0) {
                candidate = next;
            }
        }

        return Optional.of(candidate);
    }

    @Override
    public Optional<T> max(Comparator<? super T> comparator) {
        if (elements.hasNext() == false) {
            return Optional.empty();
        } else if (sorted && isSameComparator(comparator, cmp)) {
            T next = null;

            while (elements.hasNext()) {
                next = elements.next();
            }

            return Optional.of(next);
        }

        comparator = comparator == null ? NATURAL_COMPARATOR : comparator;
        T candidate = elements.next();
        T next = null;

        while (elements.hasNext()) {
            next = elements.next();
            if (comparator.compare(next, candidate) > 0) {
                candidate = next;
            }
        }

        return Optional.of(candidate);
    }

    @Override
    public Optional<T> kthLargest(int k, Comparator<? super T> comparator) {
        N.checkArgPositive(k, "k");

        if (elements.hasNext() == false) {
            return Optional.empty();
        } else if (sorted && isSameComparator(comparator, cmp)) {
            final LinkedList<T> queue = new LinkedList<>();

            while (elements.hasNext()) {
                if (queue.size() >= k) {
                    queue.poll();
                }

                queue.offer(elements.next());
            }

            return queue.size() < k ? (Optional<T>) Optional.empty() : Optional.of(queue.peek());
        }

        comparator = comparator == null ? NATURAL_COMPARATOR : comparator;
        final Queue<T> queue = new PriorityQueue<>(k, comparator);
        T e = null;

        while (elements.hasNext()) {
            e = elements.next();

            if (queue.size() < k) {
                queue.offer(e);
            } else {
                if (comparator.compare(e, queue.peek()) > 0) {
                    queue.poll();
                    queue.offer(e);
                }
            }
        }

        return queue.size() < k ? (Optional<T>) Optional.empty() : Optional.of(queue.peek());
    }

    @Override
    public long count() {
        return elements.count();
    }

    @Override
    public <E extends Exception> boolean anyMatch(final Try.Predicate<? super T, E> predicate) throws E {
        while (elements.hasNext()) {
            if (predicate.test(elements.next())) {
                return true;
            }
        }

        return false;
    }

    @Override
    public <E extends Exception> boolean allMatch(final Try.Predicate<? super T, E> predicate) throws E {
        while (elements.hasNext()) {
            if (predicate.test(elements.next()) == false) {
                return false;
            }
        }

        return true;
    }

    @Override
    public <E extends Exception> boolean noneMatch(final Try.Predicate<? super T, E> predicate) throws E {
        while (elements.hasNext()) {
            if (predicate.test(elements.next())) {
                return false;
            }
        }

        return true;
    }

    @Override
    public <E extends Exception> Optional<T> findFirst(final Try.Predicate<? super T, E> predicate) throws E {
        while (elements.hasNext()) {
            T e = elements.next();

            if (predicate.test(e)) {
                return Optional.of(e);
            }
        }

        return (Optional<T>) Optional.empty();
    }

    @Override
    public <E extends Exception> Optional<T> findLast(final Try.Predicate<? super T, E> predicate) throws E {
        if (elements.hasNext() == false) {
            return (Optional<T>) Optional.empty();
        }

        boolean hasResult = false;
        T e = null;
        T result = null;

        while (elements.hasNext()) {
            e = elements.next();

            if (predicate.test(e)) {
                result = e;
                hasResult = true;
            }
        }

        return hasResult ? Optional.of(result) : (Optional<T>) Optional.empty();
    }

    /**
     * Returns a Stream with elements from a temporary queue which is filled by reading the elements from the specified iterator asynchronously.
     * 
     * @param stream
     * @param queueSize Default value is 8
     * @return
     */
    @Override
    public Stream<T> queued(int queueSize) {
        final Iterator<T> iter = iterator();

        if (iter instanceof QueuedIterator && ((QueuedIterator<? extends T>) iter).max() >= queueSize) {
            return this;
        } else {
            return new IteratorStream<>(Stream.parallelConcatt(Arrays.asList(iter), 1, queueSize), sorted, cmp, closeHandlers);
        }
    }

    @Override
    ObjIteratorEx<T> iteratorEx() {
        return elements;
    }

    @Override
    public Stream<T> parallel(int maxThreadNum, Splitor splitor) {
        return new ParallelIteratorStream<>(elements, sorted, cmp, maxThreadNum, splitor, closeHandlers);
    }

    @Override
    public java.util.stream.Stream<T> toJdkStream() {
        final Spliterator<T> spliterator = Spliterators.spliteratorUnknownSize(elements, Spliterator.ORDERED);

        if (N.isNullOrEmpty(closeHandlers)) {
            return StreamSupport.stream(() -> spliterator, Spliterator.ORDERED | Spliterator.IMMUTABLE | Spliterator.NONNULL, isParallel());
        } else {
            return StreamSupport.stream(() -> spliterator, Spliterator.ORDERED | Spliterator.IMMUTABLE | Spliterator.NONNULL, isParallel())
                    .onClose(() -> close(closeHandlers));
        }
    }

    @Override
    public Stream<T> onClose(Runnable closeHandler) {
        final Set<Runnable> newCloseHandlers = new LocalLinkedHashSet<>(N.isNullOrEmpty(this.closeHandlers) ? 1 : this.closeHandlers.size() + 1);

        if (N.notNullOrEmpty(this.closeHandlers)) {
            newCloseHandlers.addAll(this.closeHandlers);
        }

        newCloseHandlers.add(closeHandler);

        return new IteratorStream<>(elements, sorted, cmp, newCloseHandlers);
    }
}
