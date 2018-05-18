package org.aion.avm.core.stacktracking;

import org.aion.avm.core.TestClassLoader;
import org.aion.avm.internal.OutOfStackError;
import org.aion.avm.internal.StackWatcher;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.objectweb.asm.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Field;

public class StackWatcherTest {
    private Class<?> clazz;

    private static void writeBytesToFile(byte[] bytes, String file) {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Before
    // We only need to load the instrumented class once.
    public void getInstructmentedClass()throws IOException, ClassNotFoundException{
        String name = "org.aion.avm.core.stacktracking.TestResource";
        TestClassLoader loader = new TestClassLoader(TestResource.class.getClassLoader(), name, (inputBytes) -> {
            ClassReader in = new ClassReader(inputBytes);
            ClassWriter out = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

            StackWatcherClassAdapter swc = new StackWatcherClassAdapter(out);
            in.accept(swc, ClassReader.EXPAND_FRAMES);

            byte[] transformed = out.toByteArray();
            writeBytesToFile(transformed, "/tmp/output.class");
            return transformed;
        });

        clazz = loader.loadClass(name);
    }

    @Test
    public void testDepthOverflow() throws IOException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        StackWatcher.reset();
        StackWatcher.setPolicy(StackWatcher.POLICY_DEPTH);
        StackWatcher.setMaxStackDepth(500);
        StackWatcher.setMaxStackSize(20000);

        Object obj = clazz.getConstructor().newInstance();
        Method method = clazz.getMethod("testStackOverflow");

        try{
            Object ret = method.invoke(obj);
        }catch(InvocationTargetException e){
            Assert.assertTrue(e.getCause() instanceof OutOfStackError);
        }
    }

    @Test
    public void testSizeOverflow() throws IOException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        StackWatcher.reset();
        StackWatcher.setPolicy(StackWatcher.POLICY_SIZE);
        StackWatcher.setMaxStackDepth(500);
        StackWatcher.setMaxStackSize(20000);

        Object obj = clazz.getConstructor().newInstance();
        Method method = clazz.getMethod("testStackOverflow");

        try{
            Object ret = method.invoke(obj);
        }catch(InvocationTargetException e){
            Assert.assertTrue(e.getCause() instanceof OutOfStackError);
        }
    }

    @Test
    public void testStackOverflowConsistency() throws IOException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException, NoSuchFieldException {
        StackWatcher.reset();
        StackWatcher.setPolicy(StackWatcher.POLICY_SIZE);
        StackWatcher.setMaxStackDepth(600);
        StackWatcher.setMaxStackSize(20000);

        Object obj;
        Method method;
        Field counter;
        Object ret;
        Boolean expectedError;
        Object prev = -1;
        Object cur = -1;

        for (int i = 0; i < 50; i++){
            StackWatcher.reset();
            obj = clazz.getConstructor().newInstance();
            method = clazz.getMethod("testStackOverflowConsistency");
            counter = clazz.getDeclaredField("upCounter");
            try{
                ret = method.invoke(obj);
            }catch(InvocationTargetException e){
                Assert.assertTrue(e.getCause() instanceof OutOfStackError);
                cur = counter.get(obj);
                if ((int)prev != -1){
                    Assert.assertEquals(cur, prev);
                }else{
                    prev = cur;
                }
            }
        }

        prev = -1;
        cur = -1;
        StackWatcher.setPolicy(StackWatcher.POLICY_DEPTH);
        for (int i = 0; i < 50; i++){
            StackWatcher.reset();
            obj = clazz.getConstructor().newInstance();
            method = clazz.getMethod("testStackOverflowConsistency");
            counter = clazz.getDeclaredField("upCounter");
            try{
                ret = method.invoke(obj);
            }catch(InvocationTargetException e){
                Assert.assertTrue(e.getCause() instanceof OutOfStackError);
                cur = counter.get(obj);
                if ((int)prev != -1){
                    Assert.assertEquals(cur, prev);
                }else{
                    prev = cur;
                }
            }
        }
    }

    @Test
    public void testStackTrackingConsistency() throws IOException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        StackWatcher.reset();
        StackWatcher.setPolicy(StackWatcher.POLICY_SIZE | StackWatcher.POLICY_DEPTH);
        StackWatcher.setMaxStackDepth(200);
        StackWatcher.setMaxStackSize(20000);

        Object obj = clazz.getConstructor().newInstance();
        Method method = clazz.getMethod("testStackTrackingConsistency");

        Object ret = method.invoke(obj);
        Assert.assertEquals(ret, true);
    }

    @Test
    public void testLocalTryCatch() throws IOException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        StackWatcher.reset();
        StackWatcher.setPolicy(StackWatcher.POLICY_SIZE | StackWatcher.POLICY_DEPTH);
        StackWatcher.setMaxStackDepth(200);
        StackWatcher.setMaxStackSize(20000);

        Object obj = clazz.getConstructor().newInstance();
        Method method = clazz.getMethod("testLocalTryCatch");

        Object ret = method.invoke(obj);
        //Assert.assertEquals(ret, true);
    }

    @Test
    public void testRemoteTryCatch() throws IOException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        StackWatcher.reset();
        StackWatcher.setPolicy(StackWatcher.POLICY_SIZE | StackWatcher.POLICY_DEPTH);
        StackWatcher.setMaxStackDepth(200);
        StackWatcher.setMaxStackSize(20000);

        Object obj = clazz.getConstructor().newInstance();
        Method method = clazz.getMethod("testRemoteTryCatch");

        Object ret = method.invoke(obj);
        Assert.assertEquals(ret, true);
    }
}
