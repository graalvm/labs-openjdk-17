/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * @test
 * @requires vm.jvmci
 * @library ../../../../../
 * @ignore 8249621
 * @modules jdk.internal.vm.ci/jdk.vm.ci.meta
 *          jdk.internal.vm.ci/jdk.vm.ci.runtime
 *          java.base/jdk.internal.misc
 * @run junit/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI -XX:-UseJVMCICompiler jdk.vm.ci.runtime.test.TestResolvedJavaMethod
 */

package jdk.vm.ci.runtime.test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod.Parameter;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Tests for {@link ResolvedJavaMethod}.
 */
public class TestResolvedJavaMethod extends MethodUniverse {

    public TestResolvedJavaMethod() {
    }

    /**
     * @see ResolvedJavaMethod#getCode()
     */
    @Test
    public void getCodeTest() {
        for (ResolvedJavaMethod m : joinValues(methods, constructors)) {
            String ms = m.toString();
            byte[] code = m.getCode();
            if (code == null) {
                assertEquals(ms, m.getCodeSize(), 0);
            } else {
                assertTrue(ms, code.length > 0);
            }
        }
    }

    /**
     * @see ResolvedJavaMethod#getCodeSize()
     */
    @Test
    public void getCodeSizeTest() {
        ResolvedJavaType unlinkedType = metaAccess.lookupJavaType(UnlinkedType.class);
        assertTrue(!unlinkedType.isLinked());
        for (ResolvedJavaMethod m : addExecutables(joinValues(methods, constructors), unlinkedType, false)) {
            int codeSize = m.getCodeSize();
            String ms = m.toString();
            if (m.isAbstract() || m.isNative()) {
                assertEquals(ms, codeSize, 0);
            } else if (!m.getDeclaringClass().isLinked()) {
                assertEquals(ms, -1, codeSize);
            } else {
                assertTrue(ms, codeSize > 0);
            }
        }
        assertTrue(!unlinkedType.isLinked());
    }

    @Test
    public void equalsTest() {
        List<ResolvedJavaMethod> executables = joinValues(methods, constructors);
        for (ResolvedJavaMethod m : executables) {
            for (ResolvedJavaMethod that : executables) {
                boolean expect = m == that;
                boolean actual = m.equals(that);
                assertEquals(expect, actual);
            }
        }
        assertTrue(!unlinkedType.isLinked());
    }

    @Test
    public void getModifiersTest() {
        for (Map.Entry<Executable, ResolvedJavaMethod> e : join(methods, constructors).entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            int expected = e.getKey().getModifiers();
            int actual = m.getModifiers();
            assertEquals(String.format("%s: 0x%x != 0x%x", m, expected, actual), expected, actual);
        }
    }

    /**
     * @see ResolvedJavaMethod#isClassInitializer()
     */
    @Test
    public void isClassInitializerTest() {
        for (Map.Entry<Executable, ResolvedJavaMethod> e : join(methods, constructors).entrySet()) {
            // Class initializers are hidden from reflection
            ResolvedJavaMethod m = e.getValue();
            assertFalse(m.isClassInitializer());
        }
    }

    @Test
    public void isConstructorTest() {
        for (Map.Entry<Method, ResolvedJavaMethod> e : methods.entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            assertFalse(m.isConstructor());
        }
        for (Map.Entry<Constructor<?>, ResolvedJavaMethod> e : constructors.entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            assertTrue(m.isConstructor());
        }
    }

    @Test
    public void isSyntheticTest() {
        for (Map.Entry<Executable, ResolvedJavaMethod> e : join(methods, constructors).entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            assertEquals(e.getKey().isSynthetic(), m.isSynthetic());
        }
    }

    @Test
    public void isBridgeTest() {
        for (Map.Entry<Method, ResolvedJavaMethod> e : methods.entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            assertEquals(e.getKey().isBridge(), m.isBridge());
        }
        for (Map.Entry<Constructor<?>, ResolvedJavaMethod> e : constructors.entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            assertEquals(false, m.isBridge());
        }
    }

    @Test
    public void isVarArgsTest() {
        for (Map.Entry<Executable, ResolvedJavaMethod> e : join(methods, constructors).entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            assertEquals(e.getKey().isVarArgs(), m.isVarArgs());
        }
    }

    @Test
    public void isSynchronizedTest() {
        for (Map.Entry<Executable, ResolvedJavaMethod> e : join(methods, constructors).entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            assertEquals(Modifier.isSynchronized(e.getKey().getModifiers()), m.isSynchronized());
        }
    }

    @Test
    public void canBeStaticallyBoundTest() {
        for (Map.Entry<Method, ResolvedJavaMethod> e : methods.entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            assertEquals(m.canBeStaticallyBound(), canBeStaticallyBound(e.getKey()));
        }
        for (Map.Entry<Constructor<?>, ResolvedJavaMethod> e : constructors.entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            boolean expect = m.canBeStaticallyBound();
            boolean actual = canBeStaticallyBound(e.getKey());
            assertEquals(m.toString(), expect, actual);
        }
    }
    private static boolean canBeStaticallyBound(Member method) {
        int modifiers = method.getModifiers();
        return (Modifier.isFinal(modifiers) || Modifier.isPrivate(modifiers) || Modifier.isStatic(modifiers) || Modifier.isFinal(method.getDeclaringClass().getModifiers())) &&
                        !Modifier.isAbstract(modifiers);
    }

    private static String methodWithExceptionHandlers(String p1, Object o2) {
        try {
            return p1.substring(100) + o2.toString();
        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Test
    public void getExceptionHandlersTest() throws NoSuchMethodException {
        ResolvedJavaMethod method = metaAccess.lookupJavaMethod(getClass().getDeclaredMethod("methodWithExceptionHandlers", String.class, Object.class));
        ExceptionHandler[] handlers = method.getExceptionHandlers();
        assertNotNull(handlers);
        assertEquals(handlers.length, 3);
        handlers[0].getCatchType().equals(metaAccess.lookupJavaType(IndexOutOfBoundsException.class));
        handlers[1].getCatchType().equals(metaAccess.lookupJavaType(NullPointerException.class));
        handlers[2].getCatchType().equals(metaAccess.lookupJavaType(RuntimeException.class));
    }

    private static String nullPointerExceptionOnFirstLine(Object o, String ignored) {
        return o.toString() + ignored;
    }

    @Test
    public void asStackTraceElementTest() throws NoSuchMethodException {
        try {
            nullPointerExceptionOnFirstLine(null, "ignored");
            Assert.fail("should not reach here");
        } catch (NullPointerException e) {
            StackTraceElement expected = e.getStackTrace()[0];
            ResolvedJavaMethod method = metaAccess.lookupJavaMethod(getClass().getDeclaredMethod("nullPointerExceptionOnFirstLine", Object.class, String.class));
            StackTraceElement actual = method.asStackTraceElement(0);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void getConstantPoolTest() {
        for (Map.Entry<Executable, ResolvedJavaMethod> e : join(methods, constructors).entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            ConstantPool cp = m.getConstantPool();
            assertTrue(cp.length() > 0);
        }
    }

    @Test
    public void getParametersTest() {
        for (Map.Entry<Method, ResolvedJavaMethod> e : methods.entrySet()) {
            java.lang.reflect.Parameter[] expected = e.getKey().getParameters();
            Parameter[] actual = e.getValue().getParameters();
            assertEquals(actual.length, expected.length);
            for (int i = 0; i < actual.length; i++) {
                java.lang.reflect.Parameter exp = expected[i];
                Parameter act = actual[i];
                assertEquals(exp.getName(), act.getName());
                assertEquals(exp.isNamePresent(), act.isNamePresent());
                assertEquals(exp.getModifiers(), act.getModifiers());
                assertArrayEquals(exp.getAnnotations(), act.getAnnotations());
                assertEquals(exp.getType().getName(), act.getType().toClassName());
                assertEquals(exp.getParameterizedType(), act.getParameterizedType());
                assertEquals(metaAccess.lookupJavaMethod(exp.getDeclaringExecutable()), act.getDeclaringMethod());
            }
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface TestAnnotation {
        long value();
    }

    @Test
    @TestAnnotation(value = 1000L)
    public void getAnnotationTest() throws NoSuchMethodException {
        ResolvedJavaMethod method = metaAccess.lookupJavaMethod(getClass().getDeclaredMethod("getAnnotationTest"));
        TestAnnotation annotation = method.getAnnotation(TestAnnotation.class);
        assertNotNull(annotation);
        assertEquals(1000L, annotation.value());
    }

    @Test
    @TestAnnotation(value = 1000L)
    public void getAnnotationsTest() throws NoSuchMethodException {
        ResolvedJavaMethod method = metaAccess.lookupJavaMethod(getClass().getDeclaredMethod("getAnnotationsTest"));
        Annotation[] annotations = method.getAnnotations();
        assertNotNull(annotations);
        assertEquals(2, annotations.length);
        TestAnnotation annotation = null;
        for (Annotation a : annotations) {
            if (a instanceof TestAnnotation) {
                annotation = (TestAnnotation) a;
                break;
            }
        }
        assertNotNull(annotation);
        assertEquals(1000L, annotation.value());
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface NonNull {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface Special {
    }

    private static native void methodWithAnnotatedParameters(@NonNull HashMap<String, String> p1, @Special @NonNull Class<? extends Annotation> p2);

    @Test
    public void getParameterAnnotationsTest() throws NoSuchMethodException {
        ResolvedJavaMethod method = metaAccess.lookupJavaMethod(getClass().getDeclaredMethod("methodWithAnnotatedParameters", HashMap.class, Class.class));
        Annotation[][] annotations = method.getParameterAnnotations();
        assertEquals(2, annotations.length);
        assertEquals(1, annotations[0].length);
        assertEquals(NonNull.class, annotations[0][0].annotationType());
        assertEquals(2, annotations[1].length);
        assertEquals(Special.class, annotations[1][0].annotationType());
        assertEquals(NonNull.class, annotations[1][1].annotationType());
    }

    @Test
    public void getGenericParameterTypesTest() throws NoSuchMethodException {
        ResolvedJavaMethod method = metaAccess.lookupJavaMethod(getClass().getDeclaredMethod("methodWithAnnotatedParameters", HashMap.class, Class.class));
        Type[] genericParameterTypes = method.getGenericParameterTypes();
        assertEquals(2, genericParameterTypes.length);
        assertEquals("java.util.HashMap<java.lang.String, java.lang.String>", genericParameterTypes[0].toString());
        assertEquals("java.lang.Class<? extends java.lang.annotation.Annotation>", genericParameterTypes[1].toString());
    }

    @Test
    public void getMaxLocalsTest() throws NoSuchMethodException {
        ResolvedJavaMethod method1 = metaAccess.lookupJavaMethod(getClass().getDeclaredMethod("methodWithAnnotatedParameters", HashMap.class, Class.class));
        ResolvedJavaMethod method2 = metaAccess.lookupJavaMethod(getClass().getDeclaredMethod("nullPointerExceptionOnFirstLine", Object.class, String.class));
        assertEquals(0, method1.getMaxLocals());
        assertEquals(2, method2.getMaxLocals());

    }

    @Test
    public void getMaxStackSizeTest() throws NoSuchMethodException {
        ResolvedJavaMethod method1 = metaAccess.lookupJavaMethod(getClass().getDeclaredMethod("methodWithAnnotatedParameters", HashMap.class, Class.class));
        ResolvedJavaMethod method2 = metaAccess.lookupJavaMethod(getClass().getDeclaredMethod("nullPointerExceptionOnFirstLine", Object.class, String.class));
        assertEquals(0, method1.getMaxStackSize());
        // some versions of javac produce bytecode with a stacksize of 2 for this method
        // JSR 292 also sometimes need one more stack slot
        int method2StackSize = method2.getMaxStackSize();
        assertTrue(2 <= method2StackSize && method2StackSize <= 4);
    }

    @Test
    public void isDefaultTest() {
        for (Map.Entry<Method, ResolvedJavaMethod> e : methods.entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            assertEquals(e.getKey().isDefault(), m.isDefault());
        }
        for (Map.Entry<Constructor<?>, ResolvedJavaMethod> e : constructors.entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            assertFalse(m.isDefault());
        }
    }

    @Test
    public void hasReceiverTest() {
        for (Map.Entry<Method, ResolvedJavaMethod> e : methods.entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            assertTrue(m.hasReceiver() != Modifier.isStatic(e.getKey().getModifiers()));
        }
        for (Map.Entry<Constructor<?>, ResolvedJavaMethod> e : constructors.entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            assertTrue(m.hasReceiver());
        }
    }

    public static List<ResolvedJavaMethod> addExecutables(List<ResolvedJavaMethod> to, ResolvedJavaType declaringType, boolean forceLink) {
        to.addAll(List.of(declaringType.getDeclaredMethods(forceLink)));
        to.addAll(List.of(declaringType.getDeclaredConstructors(forceLink)));
        return to;
    }

    @Test
    public void hasBytecodesTest() {
        ResolvedJavaType unlinkedType = metaAccess.lookupJavaType(UnlinkedType.class);
        assertTrue(!unlinkedType.isLinked());
        for (ResolvedJavaMethod m : addExecutables(joinValues(methods, constructors), unlinkedType, false)) {
            boolean expect = m.getDeclaringClass().isLinked() && m.isConcrete() && !m.isNative();
            boolean actual = m.hasBytecodes();
            assertEquals(m.toString(), expect, actual);
        }
        assertTrue(!unlinkedType.isLinked());
    }

    @Test
    public void isJavaLangObjectInitTest() throws NoSuchMethodException {
        ResolvedJavaMethod method = metaAccess.lookupJavaMethod(Object.class.getConstructor());
        assertTrue(method.isJavaLangObjectInit());
        for (Map.Entry<Method, ResolvedJavaMethod> e : methods.entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            assertFalse(m.isJavaLangObjectInit());
        }
        for (Map.Entry<Constructor<?>, ResolvedJavaMethod> e : constructors.entrySet()) {
            ResolvedJavaMethod m = e.getValue();
            Constructor<?> key = e.getKey();
            if (key.getDeclaringClass() == Object.class && key.getParameters().length == 0) {
                assertTrue(m.isJavaLangObjectInit());
            } else {
                assertFalse(m.isJavaLangObjectInit());
            }
        }
    }

    abstract static class UnlinkedType {
        abstract void abstractMethod();

        void concreteMethod() {
        }

        native void nativeMethod();
    }

    /**
     * All public non-final methods should be available in the vtable.
     */
    @Test
    public void testVirtualMethodTableAccess() {
        ResolvedJavaType unlinkedType = metaAccess.lookupJavaType(UnlinkedType.class);
        assertTrue(!unlinkedType.isLinked());
        for (Class<?> c : classes) {
            if (c.isInterface()) {
                for (Method m : c.getDeclaredMethods()) {
                    ResolvedJavaMethod method = metaAccess.lookupJavaMethod(m);
                    method.isInVirtualMethodTable(unlinkedType);
                }
            }
        }
        for (Class<?> c : classes) {
            if (c.isPrimitive() || c.isInterface()) {
                continue;
            }
            ResolvedJavaType receiverType = metaAccess.lookupJavaType(c);
            for (Method m : c.getMethods()) {
                ResolvedJavaMethod method = metaAccess.lookupJavaMethod(m);
                if (!method.isStatic() && !method.isFinal() && !method.getDeclaringClass().isLeaf() && !method.getDeclaringClass().isInterface()) {
                    assertTrue(method + " not available in " + receiverType, method.isInVirtualMethodTable(receiverType));
                }
            }
        }
    }

    private Method findTestMethod(Method apiMethod) {
        String testName = apiMethod.getName() + "Test";
        for (Method m : getClass().getDeclaredMethods()) {
            if (m.getName().equals(testName) && m.getAnnotation(Test.class) != null) {
                return m;
            }
        }
        return null;
    }

    // @formatter:off
    private static final String[] untestedApiMethods = {
        "newInstance",
        "getDeclaringClass",
        "getEncoding",
        "getProfilingInfo",
        "reprofile",
        "getCompilerStorage",
        "hasNeverInlineDirective",
        "canBeInlined",
        "shouldBeInlined",
        "getLineNumberTable",
        "getLocalVariableTable",
        "isInVirtualMethodTable",
        "toParameterTypes",
        "getParameterAnnotation",
        "getSpeculationLog",
        "isFinal",
        "invoke",
        "$jacocoInit"
    };
    // @formatter:on

    /**
     * Ensures that any new methods added to {@link ResolvedJavaMethod} either have a test written
     * for them or are added to {@link #untestedApiMethods}.
     */
    @Test
    public void testCoverage() {
        Set<String> known = new HashSet<>(Arrays.asList(untestedApiMethods));
        for (Method m : ResolvedJavaMethod.class.getDeclaredMethods()) {
            if (Modifier.isStatic(m.getModifiers())) {
                continue;
            }
            if (findTestMethod(m) == null) {
                assertTrue("test missing for " + m, known.contains(m.getName()));
            } else {
                assertFalse("test should be removed from untestedApiMethods" + m, known.contains(m.getName()));
            }
        }
    }

    @SafeVarargs
    public static <K, V> Map<K, V> join(Map<? extends K, V>... maps) {
        Map<K, V> res = new HashMap<>();
        for (Map<? extends K, V> e : maps) {
            res.putAll(e);
        }
        return res;
    }

    @SafeVarargs
    public static <V> List<V> joinValues(Map<?, V>... maps) {
        List<V> res = new ArrayList<>();
        for (Map<?, V> e : maps) {
            res.addAll(e.values());
        }
        return res;
    }
}
