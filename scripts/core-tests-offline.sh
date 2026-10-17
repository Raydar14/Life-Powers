#!/usr/bin/env bash
# Compiles and runs the :core module tests with a standalone Kotlin compiler, for machines that
# cannot reach Maven Central (the normal path is ./gradlew :core:test).
# Needs: KOTLINC=/path/to/kotlinc (from https://github.com/JetBrains/kotlin/releases) and a JDK 17+.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KOTLINC="${KOTLINC:?set KOTLINC to the unpacked kotlinc directory}"
OUT="${OUT:-$ROOT/build/offline-core}"
mkdir -p "$OUT/stub/org/junit"
cat > "$OUT/stub/org/junit/Test.java" <<'J'
package org.junit;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
public @interface Test { Class<? extends Throwable> expected() default None.class; long timeout() default 0L; public static class None extends Throwable { private None() {} } }
J
cat > "$OUT/stub/org/junit/ComparisonFailure.java" <<'J'
package org.junit;
public class ComparisonFailure extends AssertionError { public ComparisonFailure(String m, String e, String a) { super(m + " expected:<" + e + "> but was:<" + a + ">"); } }
J
cat > "$OUT/stub/org/junit/Assert.java" <<'J'
package org.junit;
public class Assert {
    public static void fail(String m) { throw new AssertionError(m); }
    public static void fail() { throw new AssertionError(); }
    public static void assertTrue(String m, boolean c) { if (!c) fail(m); }
    public static void assertTrue(boolean c) { if (!c) fail(); }
    public static void assertFalse(String m, boolean c) { if (c) fail(m); }
    public static void assertFalse(boolean c) { if (c) fail(); }
    public static void assertNull(String m, Object o) { if (o != null) fail(m + " expected null but was " + o); }
    public static void assertNull(Object o) { assertNull("", o); }
    public static void assertNotNull(String m, Object o) { if (o == null) fail(m); }
    public static void assertNotNull(Object o) { assertNotNull("", o); }
    public static void assertEquals(String m, Object e, Object a) { if (e == null ? a != null : !e.equals(a)) throw new ComparisonFailure(m == null ? "" : m, String.valueOf(e), String.valueOf(a)); }
    public static void assertEquals(Object e, Object a) { assertEquals("", e, a); }
    public static void assertEquals(String m, long e, long a) { if (e != a) throw new ComparisonFailure(m == null ? "" : m, String.valueOf(e), String.valueOf(a)); }
    public static void assertEquals(long e, long a) { assertEquals("", e, a); }
    public static void assertEquals(String m, double e, double a, double d) { if (Double.compare(e, a) != 0 && Math.abs(e - a) > d) throw new ComparisonFailure(m == null ? "" : m, String.valueOf(e), String.valueOf(a)); }
    public static void assertEquals(double e, double a, double d) { assertEquals("", e, a, d); }
    public static void assertNotEquals(String m, Object e, Object a) { if (e == null ? a == null : e.equals(a)) fail(m); }
    public static void assertSame(String m, Object e, Object a) { if (e != a) fail(m); }
    public static void assertNotSame(String m, Object e, Object a) { if (e == a) fail(m); }
}
J
cat > "$OUT/Runner.kt" <<'K'
import java.lang.reflect.InvocationTargetException
fun main(args: Array<String>) {
    var passed = 0; var failed = 0
    for (name in args) {
        val cls = Class.forName(name); val inst = cls.getDeclaredConstructor().newInstance()
        for (m in cls.declaredMethods.sortedBy { it.name }) {
            if (m.getAnnotation(org.junit.Test::class.java) == null) continue
            try { m.invoke(inst); passed++; println("PASS  ${cls.simpleName}.${m.name}") }
            catch (e: InvocationTargetException) { failed++; val c = e.cause ?: e; println("FAIL  ${cls.simpleName}.${m.name}: ${c.javaClass.simpleName}: ${c.message}") }
        }
    }
    println("\n$passed passed, $failed failed"); if (failed > 0) System.exit(1)
}
K
(cd "$OUT/stub" && javac -d "$OUT/stubclasses" org/junit/*.java && cd "$OUT/stubclasses" && jar cf "$OUT/junit-stub.jar" org)
KL="$KOTLINC/lib"
CP="$KL/kotlin-test.jar:$KL/kotlin-test-junit.jar:$KL/kotlinx-coroutines-core-jvm.jar:$OUT/junit-stub.jar"
"$KOTLINC/bin/kotlinc" "$ROOT/core/src/main/kotlin" -cp "$CP" -d "$OUT/core-main.jar"
"$KOTLINC/bin/kotlinc" "$ROOT/core/src/test/kotlin" "$OUT/Runner.kt" -cp "$CP:$OUT/core-main.jar" -d "$OUT/core-test.jar"
CLASSES=$(cd "$ROOT/core/src/test/kotlin" && find . -name "*Test.kt" | sed 's#^\./##; s#/#.#g; s#\.kt$##')
java -cp "$CP:$OUT/core-main.jar:$OUT/core-test.jar:$KL/kotlin-stdlib.jar" RunnerKt $CLASSES
