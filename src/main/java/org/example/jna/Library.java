package org.example.jna;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.*;

import static java.lang.foreign.ValueLayout.ADDRESS;

/**
 * @author JDragon
 * @date 2023/10/13 15:45
 * @description
 */
public interface Library {

    class Handler implements InvocationHandler {

        private final SymbolLookup symbolLookup;

        public Handler(String libPath) {
            symbolLookup = SymbolLookup.libraryLookup(libPath, Arena.ofConfined());
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            try (Arena arena = Arena.ofConfined()) {
                List<Object> objectList = new LinkedList<>();

                Class<?> returnType = method.getReturnType();
                Class<?> returnRealType;
                MemoryLayout retMemoryLayout;
                MemoryLayout retMemoryRealLayout;
                boolean needSegmentAllocator = false;
                boolean baseType = false;
                if (returnType.isArray() || Collection.class.isAssignableFrom(returnType)) {
                    retMemoryLayout = ADDRESS;
                    retMemoryRealLayout = MemoryLayout.sequenceLayout(Integer.MAX_VALUE, FFMAPI.buildMemoryLayout(returnType.getComponentType()));
                    returnRealType = returnType.getComponentType();
                } else if (FFMAPI.isBaseType(returnType)) {
                    retMemoryLayout = retMemoryRealLayout = FFMAPI.toValueLayout(returnType);
                    returnRealType = returnType;
                    baseType = true;
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
                    objectList.add(SegmentAllocator.slicingAllocator(arena.allocate(retMemoryRealLayout.byteSize())));
                }
                if (args != null) {
                    for (Object arg : args) {
                        if (FFMAPI.isBaseType(arg)) {
                            objectList.add(arg);
                        } else {
                            SequenceLayout pointsArrayMemoryLayout = (SequenceLayout) FFMAPI.buildMemoryLayout(arg);
                            MemorySegment segment = arena.allocate(pointsArrayMemoryLayout);
                            FFMAPI.buildMemorySegment(arg, segment, pointsArrayMemoryLayout, pointsArrayMemoryLayout, new LinkedList<>(), new LinkedList<>());
                            objectList.add(segment);
                        }
                    }
                }

                MethodHandle methodHandle = FFMAPI.linker.downcallHandle(
                        symbolLookup.find(method.getName()).orElseThrow(),
                        FunctionDescriptor.of(retMemoryLayout, argsMemoryLayout)
                );

                if (baseType) {
                    return methodHandle.invokeWithArguments(objectList.toArray());
                }
                MemorySegment result = (MemorySegment) methodHandle.invokeWithArguments(objectList.toArray());
                result = result.reinterpret(retMemoryRealLayout.byteSize());
                return FFMAPI.buildObjectFromMemorySegment(returnRealType, result, retMemoryRealLayout, retMemoryRealLayout, new LinkedList<>(), new LinkedList<>());
            }
        }
    }
}
