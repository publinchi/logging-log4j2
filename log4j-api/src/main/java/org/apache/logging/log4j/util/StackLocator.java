/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package org.apache.logging.log4j.util;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * <em>Consider this class private.</em> Determines the caller's class.
 */
public final class StackLocator {

    private final static StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    private final static StackWalker STACK_WALKER = StackWalker.getInstance();

    private final static StackLocator INSTANCE = new StackLocator();

    /**
     * Gets the singleton instance.
     *
     * @return the singleton instance.
     */
    public static StackLocator getInstance() {
        return INSTANCE;
    }

    private StackLocator() {
    }

    @PerformanceSensitive
    public Class<?> getCallerClass(final Class<?> sentinelClass, final Predicate<Class<?>> callerPredicate) {
        if (sentinelClass == null) {
            throw new IllegalArgumentException("sentinelClass cannot be null");
        }
        if (callerPredicate == null) {
            throw new IllegalArgumentException("callerPredicate cannot be null");
        }
        return WALKER.walk(s -> s
                .map(StackWalker.StackFrame::getDeclaringClass)
                // Skip until the sentinel class is found
                .dropWhile(clazz -> !sentinelClass.equals(clazz))
                // Skip until the predicate evaluates to true, also ignoring recurrences of the sentinel
                .dropWhile(clazz -> sentinelClass.equals(clazz) || !callerPredicate.test(clazz))
                .findFirst().orElse(null));
    }

    @PerformanceSensitive
    public Class<?> getCallerClass(final String fqcn) {
        return getCallerClass(fqcn, "");
    }

    @PerformanceSensitive
    public Class<?> getCallerClass(final String fqcn, final String pkg) {
        return WALKER.walk(s -> s
                .dropWhile(f -> !f.getClassName().equals(fqcn))
                .dropWhile(f -> f.getClassName().equals(fqcn))
                .dropWhile(f -> !f.getClassName().startsWith(pkg))
                .findFirst())
                .map(StackWalker.StackFrame::getDeclaringClass)
                .orElse(null);
    }

    @PerformanceSensitive
    public Class<?> getCallerClass(final Class<?> anchor) {
        return WALKER.walk(s -> s.dropWhile(f -> !f.getDeclaringClass().equals(anchor)).
                dropWhile(f -> f.getDeclaringClass().equals(anchor)).findFirst()).
                map(StackWalker.StackFrame::getDeclaringClass).orElse(null);
    }

    /**
     * Gets the Class of the method that called <em>this</em> method at the location up the call stack by the given stack
     * frame depth.
     *
     * @param depth The stack frame count to walk.
     * @return A class or null.
     * @throws IndexOutOfBoundsException if depth is negative.
     */
    @PerformanceSensitive
    public Class<?> getCallerClass(final int depth) {
        return WALKER.walk(s -> s.skip(depth).findFirst()).map(StackWalker.StackFrame::getDeclaringClass).orElse(null);
    }

    @PerformanceSensitive
    public Deque<Class<?>> getCurrentStackTrace() {
        // benchmarks show that using the SecurityManager is much faster than looping through getCallerClass(int)
        if (PrivateSecurityManagerStackTraceUtil.isEnabled()) {
            return PrivateSecurityManagerStackTraceUtil.getCurrentStackTrace();
        }
        final Deque<Class<?>> stack = new ArrayDeque<>();
        return WALKER.walk(s -> {
            s.forEach(f -> stack.add(f.getDeclaringClass()));
            return stack;
        });

    }

    public StackTraceElement calcLocation(final String fqcnOfLogger) {
        return STACK_WALKER.walk(
                s -> s.dropWhile(f -> !f.getClassName().equals(fqcnOfLogger)) // drop the top frames until we reach the logger
                        .dropWhile(f -> f.getClassName().equals(fqcnOfLogger)) // drop the logger frames
                        .findFirst()).map(StackWalker.StackFrame::toStackTraceElement).orElse(null);
    }

    public StackTraceElement getStackTraceElement(final int depth) {
        return STACK_WALKER.walk(s -> s.skip(depth).findFirst())
                .map(StackWalker.StackFrame::toStackTraceElement).orElse(null);
    }
}
