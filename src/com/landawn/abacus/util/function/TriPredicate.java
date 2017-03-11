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

package com.landawn.abacus.util.function;

import com.landawn.abacus.util.N;

/**
 * @since 0.8
 * 
 * @author Haiyang Li
 */
public interface TriPredicate<A, B, C> {

    @SuppressWarnings("rawtypes")
   static final TriPredicate ALWAYS_TRUE = new TriPredicate() {
        @Override
        public boolean test(Object a, Object b, Object c) {
            return true;
        }
    };

    @SuppressWarnings("rawtypes")
   static final TriPredicate ALWAYS_FALSE = new TriPredicate() {
        @Override
        public boolean test(Object a, Object b, Object c) {
            return false;
        }
    };

    boolean test(A a, B b, C c);

    default TriPredicate<A, B, C> and(TriPredicate<A, B, C> other) {
        N.requireNonNull(other);

        return (a, b, c) -> test(a, b, c) && other.test(a, b, c);
    }

    default TriPredicate<A, B, C> negate() {
        return (a, b, c) -> !test(a, b, c);
    }

    default TriPredicate<A, B, C> or(TriPredicate<A, B, C> other) {
        N.requireNonNull(other);

        return (a, b, c) -> test(a, b, c) || other.test(a, b, c);
    }
}
