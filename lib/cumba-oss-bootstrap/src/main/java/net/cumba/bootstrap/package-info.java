/**
 * Bootstrap launcher: locates configuration, builds the child-first classpath, and launches the
 * real application in an isolated classloader.
 *
 * <p>
 * This package is {@code @NullMarked} (JSpecify): every type is non-null by default, and anything
 * that may be {@code null} is annotated {@code @Nullable} explicitly. NullAway enforces this at
 * compile time.
 */
@NullMarked
package net.cumba.bootstrap;

import org.jspecify.annotations.NullMarked;
