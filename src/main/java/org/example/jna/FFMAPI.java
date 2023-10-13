package org.example.jna;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.*;

import static java.lang.foreign.ValueLayout.*;

/**
 * @author JDragon
 * @date 2023/10/13 14:20
 * @description
 */
public class FFMAPI {

    static Linker linker = Linker.nativeLinker();

    public static Object buildObjectFromMemorySegment(Class<?> aClass, MemorySegment memorySegment, MemoryLayout originMemoryLayout, MemoryLayout memoryLayout, List<MemoryLayout.PathElement> pathElements, List<Object> indexParams) throws Throwable {
        if (aClass.isArray()) {
            aClass = aClass.getComponentType();
        }
        if (memoryLayout instanceof SequenceLayout sequenceLayout) {
            pathElements.add(MemoryLayout.PathElement.sequenceElement());
            Object[] array = (Object[]) Array.newInstance(aClass, (int) sequenceLayout.elementCount());
            for (int i = 0; i < sequenceLayout.elementCount(); i++) {
                indexParams.add((long) i);
                array[i] = buildObjectFromMemorySegment(aClass, memorySegment, originMemoryLayout, sequenceLayout.elementLayout(), pathElements, indexParams);
                indexParams.removeLast();
            }
            pathElements.removeLast();
            return array;
        } else if (memoryLayout instanceof StructLayout structLayout) {
            List<MemoryLayout> memoryLayouts = structLayout.memberLayouts();
            List<Object> list = new LinkedList<>();
            for (MemoryLayout layout : memoryLayouts) {
                String layoutName = layout.name().orElseThrow();
                pathElements.add(MemoryLayout.PathElement.groupElement(layoutName));
                Field declaredField = aClass.getDeclaredField(layoutName);
                Object o = buildObjectFromMemorySegment(declaredField.getType(), memorySegment, originMemoryLayout, layout, pathElements, indexParams);
                list.add(o);
                pathElements.removeLast();
            }
            return aClass.getDeclaredConstructors()[0].newInstance(list.toArray());
        } else if (memoryLayout instanceof ValueLayout) {
            Object[] varParams = new Object[indexParams.size() + 1];
            varParams[0] = memorySegment;
            for (int i = 0; i < indexParams.size(); i++) {
                varParams[i + 1] = indexParams.get(i);
            }
            VarHandle varHandle = originMemoryLayout.varHandle(pathElements.toArray(new MemoryLayout.PathElement[0]));
            MethodHandle getter = varHandle.toMethodHandle(VarHandle.AccessMode.GET);
            return getter.invokeWithArguments(varParams);
        }
        return null;
    }

    public static MemorySegment buildMemorySegment(Object object, MemorySegment memorySegment, MemoryLayout originMemoryLayout, MemoryLayout memoryLayout, List<MemoryLayout.PathElement> pathElements, List<Object> indexParams) throws Throwable {
        Class<?> aClass = object.getClass();
        if (memoryLayout instanceof SequenceLayout sequenceLayout) {
            pathElements.add(MemoryLayout.PathElement.sequenceElement());
            for (int i = 0; i < sequenceLayout.elementCount(); i++) {
                Object[] array;
                if (Collection.class.isAssignableFrom(aClass)) {
                    assert object instanceof Collection<?>;
                    Collection<?> collection = (Collection<?>) object;
                    array = collection.toArray();
                } else {
                    assert object instanceof Object[];
                    array = ((Object[]) object);
                }
                Object o = array[i];
                indexParams.add((long) i);
                buildMemorySegment(o, memorySegment, originMemoryLayout, sequenceLayout.elementLayout(), pathElements, indexParams);
                indexParams.removeLast();
            }
            pathElements.removeLast();
        } else if (memoryLayout instanceof StructLayout structLayout) {
            List<MemoryLayout> memoryLayouts = structLayout.memberLayouts();
            for (MemoryLayout layout : memoryLayouts) {
                String layoutName = layout.name().orElseThrow();
                pathElements.add(MemoryLayout.PathElement.groupElement(layoutName));
                Field declaredField = aClass.getDeclaredField(layoutName);
                Object fieldValue = getFieldValue(object, declaredField);
                buildMemorySegment(fieldValue, memorySegment, originMemoryLayout, layout, pathElements, indexParams);
                pathElements.removeLast();
            }
        } else if (memoryLayout instanceof ValueLayout) {
            Object[] varParams = new Object[indexParams.size() + 2];
            varParams[0] = memorySegment;
            for (int i = 0; i < indexParams.size(); i++) {
                varParams[i + 1] = indexParams.get(i);
            }
            varParams[varParams.length - 1] = object;
            VarHandle varHandle = originMemoryLayout.varHandle(pathElements.toArray(new MemoryLayout.PathElement[0]));
            MethodHandle setter = varHandle.toMethodHandle(VarHandle.AccessMode.SET);
            setter.invokeWithArguments(varParams);
        }
        return memorySegment;
    }

    public static Object getFieldValue(Object object, Field field) throws IllegalAccessException {
        boolean b = field.canAccess(object);
        if (!b) {
            field.setAccessible(true);
        }
        Object o = field.get(object);
        if (!b) {
            field.setAccessible(false);
        }
        return o;
    }

    public static void setFieldValue(Object object, Field field, Object o) throws IllegalAccessException {
        boolean b = field.canAccess(object);
        if (!b) {
            field.setAccessible(true);
            field.set(object, o);
            field.setAccessible(false);
        } else {
            field.set(object, o);
        }
    }

    public static MemoryLayout toValueLayout(Field field) {
        Class<?> type = field.getType();
        if (type == int.class || type == Integer.class) {
            return JAVA_INT;
        } else if (type == long.class || type == Long.class) {
            return JAVA_LONG;
        } else if (type == short.class || type == Short.class) {
            return JAVA_SHORT;
        } else if (type == byte.class || type == Byte.class) {
            return JAVA_BYTE;
        } else if (type == boolean.class || type == Boolean.class) {
            return JAVA_BOOLEAN;
        } else if (type == char.class || type == Character.class) {
            return JAVA_CHAR;
        } else if (type == float.class || type == Float.class) {
            return JAVA_FLOAT;
        } else if (type == double.class || type == Double.class) {
            return JAVA_DOUBLE;
        } else {
            return ADDRESS;
        }
    }

    public static MemoryLayout toValueLayout(Class<?> type) {
        if (type == int.class || type == Integer.class) {
            return JAVA_INT;
        } else if (type == long.class || type == Long.class) {
            return JAVA_LONG;
        } else if (type == short.class || type == Short.class) {
            return JAVA_SHORT;
        } else if (type == byte.class || type == Byte.class) {
            return JAVA_BYTE;
        } else if (type == boolean.class || type == Boolean.class) {
            return JAVA_BOOLEAN;
        } else if (type == char.class || type == Character.class) {
            return JAVA_CHAR;
        } else if (type == float.class || type == Float.class) {
            return JAVA_FLOAT;
        } else if (type == double.class || type == Double.class) {
            return JAVA_DOUBLE;
        } else {
            return ADDRESS;
        }
    }

    public static boolean isBaseType(Class<?> type) {
        return type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == short.class || type == Short.class
                || type == byte.class || type == Byte.class
                || (type == boolean.class || type == Boolean.class
                || type == char.class || type == Character.class
                || type == float.class || type == Float.class
                || type == double.class || type == Double.class);
    }

    public static boolean isBaseType(Object object) {
        return isBaseType(object.getClass());
    }

    public static MemoryLayout buildMemoryLayout(Object object) throws IllegalAccessException {
        if (isBaseType(object)) {
            return toValueLayout(object.getClass());
        }
        Class<?> aClass = object.getClass();
        if (object instanceof Class<?> objectC) {
            aClass = objectC;
        }
        if (aClass.isArray() || Collection.class.isAssignableFrom(aClass)) {
            Object[] array;
            if (Collection.class.isAssignableFrom(aClass)) {
                Collection<?> collection = (Collection<?>) object;
                array = collection.toArray();
            } else {
                array = ((Object[]) object);
            }
            int length = array.length;
            if (length > 0) {
                return MemoryLayout.sequenceLayout(length, buildMemoryLayout(array[0]));
            } else {
                throw new RuntimeException("传入数组长度要大于0");
            }
        }

        List<MemoryLayout> memoryLayouts = new LinkedList<>();

        for (Field declaredField : aClass.getDeclaredFields()) {
            String name = declaredField.getName();
            Class<?> type = declaredField.getType();

            if (isBaseType(type)) {
                memoryLayouts.add(toValueLayout(declaredField).withName(name));
                continue;
            }
            Object o = getFieldValue(object, declaredField);
            memoryLayouts.add(buildMemoryLayout(o).withName(name));
        }
        return MemoryLayout.structLayout(memoryLayouts.toArray(new MemoryLayout[0]));
    }
}
