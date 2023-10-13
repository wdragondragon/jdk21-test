package org.example;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.*;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * @author JDragon
 * @date 2023/10/13 15:45
 * @description
 */
public interface Library {

    class Handler implements InvocationHandler {

        private Arena arena = Arena.ofConfined();

        private SymbolLookup symbolLookup;

        public Handler(String libPath, Class<?> interfaceClass, Map<String, ?> options) {
            symbolLookup = SymbolLookup.libraryLookup(libPath, arena);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            List<Object> objectList = new LinkedList<>();

            Class<?> returnType = method.getReturnType();
            Class<?> returnRealType;
            MemoryLayout retMemoryLayout;
            MemoryLayout retMemoryRealLayout;
            boolean needSegmentAllocator = false;
            if (returnType.isArray() || Collection.class.isAssignableFrom(returnType)) {
                retMemoryLayout = ADDRESS;
                retMemoryRealLayout = MemoryLayout.sequenceLayout(Integer.MAX_VALUE, FFMAPI.buildMemoryLayout(returnType.getComponentType()));
                returnRealType = returnType.getComponentType();
            } else if (FFMAPI.isBaseType(returnType)) {
                retMemoryLayout = retMemoryRealLayout = FFMAPI.toValueLayout(returnType);
                returnRealType = returnType;
            } else {
                retMemoryLayout = retMemoryRealLayout = FFMAPI.buildMemoryLayout(returnType);
                returnRealType = returnType;
                needSegmentAllocator = true;
            }


            Class<?>[] parameterTypes = method.getParameterTypes();
            MemoryLayout[] argsMemoryLayout = new MemoryLayout[parameterTypes.length];
            for (int i = 0; i < parameterTypes.length; i++) {
                Class<?> parameterType = parameterTypes[i];
                if (parameterType.isArray() || Collection.class.isAssignableFrom(parameterType)) {
                    argsMemoryLayout[i] = ADDRESS;
                } else if (FFMAPI.isBaseType(parameterType)) {
                    argsMemoryLayout[i] = FFMAPI.toValueLayout(parameterType);
                } else {
                    argsMemoryLayout[i] = FFMAPI.buildMemoryLayout(parameterType);
                }
            }

            if (needSegmentAllocator) {
                objectList.add(SegmentAllocator.slicingAllocator(Arena.ofAuto().allocate(retMemoryRealLayout.byteSize())));
            }

            for (Object arg : args) {
                if (FFMAPI.isBaseType(arg)) {
                    objectList.add(arg);
                } else {
                    SequenceLayout pointsArrayMemoryLayout = (SequenceLayout) FFMAPI.buildMemoryLayout(arg);
                    MemorySegment segment = Arena.ofAuto().allocate(pointsArrayMemoryLayout);
                    FFMAPI.buildMemorySegment(arg, segment, pointsArrayMemoryLayout, pointsArrayMemoryLayout, new LinkedList<>(), new LinkedList<>());
                    objectList.add(segment);
                }
            }

            MethodHandle test_point = FFMAPI.linker.downcallHandle(
                    symbolLookup.find(method.getName()).orElseThrow(),
                    FunctionDescriptor.of(retMemoryLayout, argsMemoryLayout)
            );

            MemorySegment result = (MemorySegment) test_point.invokeWithArguments(objectList.toArray());
            result = result.reinterpret(retMemoryRealLayout.byteSize());
            return FFMAPI.buildObjectFromMemorySegment(returnRealType, result, retMemoryRealLayout, retMemoryRealLayout, new LinkedList<>(), new LinkedList<>());
        }
    }
}
