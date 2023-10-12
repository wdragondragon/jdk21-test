package org.example;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.*;

import static java.lang.foreign.ValueLayout.*;


/**
 * @author JDragon
 * @date ${DATE} ${TIME}
 * @description
 */
public class TestNative {

    static Linker linker = Linker.nativeLinker();

    static SymbolLookup defaultLookup = linker.defaultLookup();

    static SymbolLookup symbolLookup = SymbolLookup.libraryLookup("src\\main\\resources\\MathLibrary.dll", Arena.global());

    public static void main(String[] args) throws Throwable {
        dereferenceSegmentsStructs();
//        System.out.println(9L << 32 | 9L);
    }

    public static void fb() throws Throwable {
        MethodHandle fibonacci_init = linker.downcallHandle(symbolLookup.find("fibonacci_init").orElseThrow(), FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_LONG));
        MethodHandle fibonacci_next = linker.downcallHandle(symbolLookup.find("fibonacci_next").orElseThrow(), FunctionDescriptor.of(JAVA_BOOLEAN));
        MethodHandle fibonacci_current = linker.downcallHandle(symbolLookup.find("fibonacci_current").orElseThrow(), FunctionDescriptor.of(JAVA_LONG));
        MethodHandle fibonacci_index = linker.downcallHandle(symbolLookup.find("fibonacci_index").orElseThrow(), FunctionDescriptor.of(JAVA_LONG));
        fibonacci_init.invoke(1, 1);
        do {
            System.out.println(fibonacci_index.invoke() + ": " + fibonacci_current.invoke());
        } while ((boolean) fibonacci_next.invoke());
    }

    public static void strlen() throws Throwable {
        MethodHandle strlenHandle = linker.downcallHandle(
                defaultLookup.find("strlen").orElseThrow(),
                FunctionDescriptor.of(JAVA_LONG, ADDRESS)
        );
        try (Arena offHeap = Arena.ofConfined()) {
            MemorySegment pointers = offHeap.allocateUtf8String("Hello world!");
            System.out.println(strlenHandle.invoke(pointers));  //11
        }
    }


    public static void dereferenceSegments() throws Throwable {
        long byteAlignment = JAVA_INT.byteAlignment();
        int arraySize = 25;
        MemorySegment segment
                = Arena.ofAuto().allocate(byteAlignment * arraySize, byteAlignment); // alignment
        for (int i = 0; i < arraySize; i++) {
            segment.setAtIndex(ValueLayout.JAVA_INT,
                    /* index */ i,
                    /* value to write */ i);
        }
        for (int i = 0; i < arraySize; i++) {
            int i1 = segment.get(JAVA_INT, i * byteAlignment);
            System.out.println(i1);
            int i2 = segment.getAtIndex(JAVA_INT, i);
            System.out.println(i2);
        }
    }

    public static void segmentAllocator() {
        MemorySegment segment = Arena.ofAuto().allocate(100);
        SegmentAllocator allocator = SegmentAllocator.slicingAllocator(segment);
        for (int i = 0; i < 10; i++) {
            MemorySegment s = allocator.allocateArray(JAVA_INT,
                    1, 2, 3, 4, 5);
        }
    }


    public static void dereferenceSegmentsStruct() throws Throwable {
        StructLayout structLayout = MemoryLayout.structLayout(
                JAVA_INT.withName("x"),
                JAVA_INT.withName("y"));

        SequenceLayout ptsLayout = MemoryLayout.sequenceLayout(10, structLayout);

        MethodHandle test_point = linker.downcallHandle(
                symbolLookup.find("test_point").orElseThrow(),
                FunctionDescriptor.of(structLayout, ADDRESS, JAVA_LONG)
        );

        VarHandle xHandle
                = ptsLayout.varHandle(PathElement.sequenceElement(),
                PathElement.groupElement("x"));
        VarHandle yHandle
                = ptsLayout.varHandle(PathElement.sequenceElement(),
                PathElement.groupElement("y"));

        MemorySegment segment = Arena.ofAuto().allocate(ptsLayout);
        for (int i = 0; i < ptsLayout.elementCount(); i++) {
            xHandle.set(segment, (long) i, i);
            yHandle.set(segment, (long) i, i);
        }
        SegmentAllocator allocator = SegmentAllocator.slicingAllocator(Arena.ofAuto().allocate(structLayout.byteSize()));
        MemorySegment result = (MemorySegment) test_point.invoke(allocator, segment, ptsLayout.elementCount());
        result = result.reinterpret(structLayout.byteSize());
        VarHandle resultX
                = structLayout.varHandle(PathElement.groupElement("x"));
        VarHandle resultY
                = structLayout.varHandle(PathElement.groupElement("y"));
        System.out.println(StringTemplate.STR. "\{ resultX.get(result) }:\{ resultY.get(result) }" );
    }


    public static void lookingUpForeignFunctions() throws Throwable {
        MethodHandle qsort = linker.downcallHandle(
                defaultLookup.find("qsort").orElseThrow(),
                FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, JAVA_LONG, ADDRESS)
        );

        MethodHandle comparHandle
                = MethodHandles.lookup()
                .findStatic(Qsort.class, "qsortCompare",
                        MethodType.methodType(int.class,
                                MemorySegment.class,
                                MemorySegment.class));

        MemorySegment comparFunc
                = linker.upcallStub(comparHandle,
                    /* A Java description of a C function
                       implemented by a Java method! */
                FunctionDescriptor.of(JAVA_INT,
                        ADDRESS.withTargetLayout(JAVA_INT),
                        ADDRESS.withTargetLayout(JAVA_INT)),
                Arena.ofAuto());
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment array
                    = arena.allocateArray(ValueLayout.JAVA_INT,
                    0, 9, 3, 4, 6, 5, 1, 8, 2, 7);
            qsort.invoke(array, 10L, ValueLayout.JAVA_INT.byteSize(), comparFunc);
            int[] sorted = array.toArray(JAVA_INT); // [ 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 ]
            System.out.println(Arrays.toString(sorted));
        }
    }

    public static void dereferenceSegmentsStructs() throws Throwable {
        Points[] pointsArray = new Points[10];
        for (int i = 0; i < pointsArray.length; i++) {
            Point point = new Point(i, i);
            Point[] pointArray = new Point[10];
            for (int j = 0; j < pointArray.length; j++) {
                pointArray[i] = new Point(i, j);
            }
            pointsArray[i] = new Points(i, i, point, pointArray);
        }


        StructLayout pointMemoryLayout = MemoryLayout.structLayout(
                JAVA_INT.withName("x"),
                JAVA_INT.withName("y"));

        SequenceLayout pointArrayMemoryLayout = MemoryLayout.sequenceLayout(10, pointMemoryLayout);

        StructLayout pointsMemoryLayout = MemoryLayout.structLayout(
                JAVA_INT.withName("x"),
                JAVA_INT.withName("y"),
                pointMemoryLayout.withName("point"),
                pointArrayMemoryLayout.withName("points")
        );

        SequenceLayout pointsArrayMemoryLayout = MemoryLayout.sequenceLayout(10, pointsMemoryLayout);


        List<VarHandle> varHandleList = buildVarHandle(pointsArray, pointsArrayMemoryLayout);


        MethodHandle test_point = linker.downcallHandle(
                symbolLookup.find("test_point_two").orElseThrow(),
                FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_LONG)
        );

        VarHandle xHandle
                = pointsArrayMemoryLayout.varHandle(PathElement.sequenceElement(),
                PathElement.groupElement("x"));
        VarHandle yHandle
                = pointsArrayMemoryLayout.varHandle(PathElement.sequenceElement(),
                PathElement.groupElement("y"));

        VarHandle pointXHandler = pointsArrayMemoryLayout.varHandle(PathElement.sequenceElement(),
                PathElement.groupElement("point"), PathElement.groupElement("x"));

        VarHandle pointYHandler = pointsArrayMemoryLayout.varHandle(PathElement.sequenceElement(),
                PathElement.groupElement("point"), PathElement.groupElement("y"));


        VarHandle pointArrayXVarHandler = pointsArrayMemoryLayout.varHandle(PathElement.sequenceElement(),
                PathElement.groupElement("points"), PathElement.sequenceElement(), PathElement.groupElement("x"));

        VarHandle pointArrayYVarHandler = pointsArrayMemoryLayout.varHandle(PathElement.sequenceElement(),
                PathElement.groupElement("points"), PathElement.sequenceElement(), PathElement.groupElement("y"));


        MemorySegment segment = Arena.ofAuto().allocate(pointsArrayMemoryLayout);
        for (int i = 0; i < pointsArrayMemoryLayout.elementCount(); i++) {
            xHandle.set(segment, i, i);
            yHandle.set(segment, i, i);
            pointXHandler.set(segment, i, i);
            pointYHandler.set(segment, i, i);
            for (int l = 0; l < pointArrayMemoryLayout.elementCount(); l++) {
                pointArrayXVarHandler.set(segment, (long) i, (long) l, i);
                pointArrayYVarHandler.set(segment, (long) i, (long) l, l);
            }
        }
        MemorySegment result = (MemorySegment) test_point.invoke(segment, pointsArrayMemoryLayout.elementCount());
        result = result.reinterpret(pointsArrayMemoryLayout.byteSize());

        for (int i = 0; i < pointsArrayMemoryLayout.elementCount(); i++) {
            int x = (int) xHandle.get(result, i);
            int y = (int) xHandle.get(result, i);
            int pointX = (int) pointXHandler.get(result, i);
            int pointY = (int) pointYHandler.get(result, i);

            System.out.println(StringTemplate.STR. "J \{ i }: x = \{ x }, y = \{ y }" );
            System.out.println(StringTemplate.STR. "J Points \{ i }: x = \{ pointX }, y = \{ pointY }" );
            for (int l = 0; l < pointArrayMemoryLayout.elementCount(); l++) {
                int pointsX = (int) pointArrayXVarHandler.get(result, (long) i, (long) l);
                int pointsY = (int) pointArrayYVarHandler.get(result, (long) i, (long) l);
                System.out.println(StringTemplate.STR. "J     Point \{ i }: x = \{ pointsX }, y = \{ pointsY }" );
            }
        }
    }

    public static void invoke(Object object) {
        if (object instanceof Object[]) {
            Object[] objects = (Object[]) object;
            if (objects.length == 0) {
                return;
            }
            object = objects[0];
            Class<?> aClass = object.getClass();
            Field[] declaredFields = aClass.getDeclaredFields();
            for (Field declaredField : declaredFields) {

            }

        }
        System.out.println(object);
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


    public static List<VarHandle> buildVarHandle(Object object, MemoryLayout memoryLayout) {
        List<VarHandle> varHandleList = new LinkedList<>();
        if (object instanceof Object[] objects) {
            if (objects.length == 0) {
                return new ArrayList<>();
            }
            object = objects[0];
            Class<?> aClass = object.getClass();
            Field[] declaredFields = aClass.getDeclaredFields();
            for (Field declaredField : declaredFields) {
                List<PathElement> pathElements = new LinkedList<>();
                pathElements.add(PathElement.sequenceElement());
                addVarHandle(declaredField, memoryLayout, pathElements, varHandleList);
            }
        } else {
            Class<?> aClass = object.getClass();
            Field[] declaredFields = aClass.getDeclaredFields();
            for (Field declaredField : declaredFields) {
                addVarHandle(declaredField, memoryLayout, new LinkedList<>(), varHandleList);
            }
        }
        return varHandleList;
    }

    public static List<PathElement> addVarHandle(Field field, MemoryLayout memoryLayout, List<PathElement> pathElements, List<VarHandle> varHandleList) {
        Class<?> fieldType = field.getType();
        if (fieldType.isArray()) {
            String name = field.getName();
            pathElements.add(PathElement.groupElement(name));
            pathElements.add(PathElement.sequenceElement());
            Class<?> componentType = fieldType.getComponentType();
            Field[] declaredFields = componentType.getDeclaredFields();
            for (Field declaredField : declaredFields) {
                addVarHandle(declaredField, memoryLayout, pathElements, varHandleList);
            }
            pathElements.removeLast();
        } else {
            String name = field.getName();
            pathElements.add(PathElement.groupElement(name));
            if (!isBaseType(fieldType)) {
                for (Field declaredField : fieldType.getDeclaredFields()) {
                    addVarHandle(declaredField, memoryLayout, pathElements, varHandleList);
                }
                pathElements.removeLast();
            } else {
                varHandleList.add(memoryLayout.varHandle(pathElements.toArray(new PathElement[0])));
                pathElements.removeLast();
            }

        }
        return pathElements;
    }
}