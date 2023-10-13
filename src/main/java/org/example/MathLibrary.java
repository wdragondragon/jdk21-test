package org.example;

import org.example.jna.Library;
import org.example.jna.Native;

import java.util.List;

/**
 * @author JDragon
 * @date 2023/10/13 15:39
 * @description
 */
public interface MathLibrary extends Library {

    MathLibrary DLL = Native.load("src\\main\\resources\\MathLibrary.dll", MathLibrary.class);

    Long fibonacci_init(Long a, Long b);

    Boolean fibonacci_next();

    Long fibonacci_current();

    Long fibonacci_index();

    Point test_point(Point[] point, long count);

    Points[] test_point_two(List<Points> points, long count);

}
