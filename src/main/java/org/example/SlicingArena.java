package org.example;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;

/**
 * @author JDragon
 * @date 2023/10/12 16:47
 * @description
 */
public class SlicingArena implements Arena {
    final Arena arena = Arena.ofConfined();
    final SegmentAllocator slicingAllocator;

    SlicingArena(long size) {
        slicingAllocator = SegmentAllocator.slicingAllocator(arena.allocate(size));
    }

    public MemorySegment allocate(long byteSize, long byteAlignment) {
        return slicingAllocator.allocate(byteSize, byteAlignment);
    }

    @Override
    public MemorySegment.Scope scope() {
        return null;
    }

    public void close() {
        arena.close();
    }
}