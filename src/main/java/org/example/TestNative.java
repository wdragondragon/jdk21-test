package org.example;

import jdk.internal.foreign.layout.ValueLayouts;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.Arrays;

import static java.lang.foreign.ValueLayout.*;


/**
 * @author JDragon
 * @date ${DATE} ${TIME}
 * @description
 */
public class TestNative {

    static {
        System.load("D:\\dev\\IdeaProjects\\jdk21-test\\src\\main\\resources\\MathLibrary.dll");
    }

    static Linker linker = Linker.nativeLinker();

    static SymbolLookup defaultLookup = linker.defaultLookup();

    static SymbolLookup symbolLookup = SymbolLookup.loaderLookup();

    public static void main(String[] args) throws Throwable {
        lookingUpForeignFunctions();
    }

    public static void fb() throws Throwable {
        MethodHandle fibonacci_init = linker.downcallHandle(symbolLookup.find("fibonacci_init").orElseThrow(), FunctionDescriptor.ofVoid(ValueLayouts.OfLongImpl.JAVA_LONG, ValueLayouts.OfIntImpl.JAVA_LONG));
        MethodHandle fibonacci_next = linker.downcallHandle(symbolLookup.find("fibonacci_next").orElseThrow(), FunctionDescriptor.of(ValueLayouts.OfBooleanImpl.JAVA_BOOLEAN));
        MethodHandle fibonacci_current = linker.downcallHandle(symbolLookup.find("fibonacci_current").orElseThrow(), FunctionDescriptor.of(ValueLayouts.OfByteImpl.JAVA_LONG));
        MethodHandle fibonacci_index = linker.downcallHandle(symbolLookup.find("fibonacci_index").orElseThrow(), FunctionDescriptor.of(ValueLayouts.OfLongImpl.JAVA_LONG));
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
            System.out.println(strlenHandle.invoke(pointers));
        }
    }


    public static void dereferenceSegments() throws Throwable {
        MemorySegment segment
                = Arena.ofAuto().allocate(100,                                 // size
                ValueLayout.JAVA_INT.byteAlignment()); // alignment
        for (int i = 0; i < 25; i++) {
            segment.setAtIndex(ValueLayout.JAVA_INT,
                    /* index */ i,
                    /* value to write */ i);
        }
        for (int i = 0; i < 25; i++) {
            int i1 = segment.get(JAVA_INT, i * 4);
            System.out.println(i1);
            int i2 = segment.getAtIndex(JAVA_INT, i);
            System.out.println(i2);
        }
    }

    public static void dereferenceSegmentsStruct() throws Throwable {
        MethodHandle test_point = linker.downcallHandle(
                symbolLookup.find("test_point").orElseThrow(),
                FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_LONG)
        );

        StructLayout structLayout = MemoryLayout.structLayout(
                JAVA_INT.withName("x"),
                JAVA_INT.withName("y"));

        SequenceLayout ptsLayout = MemoryLayout.sequenceLayout(10, structLayout);

        VarHandle xHandle    // (MemorySegment, long) -> int
                = ptsLayout.varHandle(PathElement.sequenceElement(),
                PathElement.groupElement("x"));
        VarHandle yHandle    // (MemorySegment, long) -> int
                = ptsLayout.varHandle(PathElement.sequenceElement(),
                PathElement.groupElement("y"));

        MemorySegment segment = Arena.ofAuto().allocate(ptsLayout);
        for (int i = 0; i < ptsLayout.elementCount(); i++) {
            xHandle.set(segment,
                    /* index */ (long) i,
                    /* value to write */ i); // x
            yHandle.set(segment,
                    /* index */ (long) i,
                    /* value to write */ i); // y
        }

        MemorySegment result = (MemorySegment) test_point.invoke(segment, ptsLayout.elementCount());
        result = result.reinterpret(structLayout.byteSize());
        System.out.println(result.getAtIndex(JAVA_INT, 0));
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
}