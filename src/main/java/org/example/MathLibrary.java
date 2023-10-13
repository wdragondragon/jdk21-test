package org.example;

import java.util.List;

/**
 * @author JDragon
 * @date 2023/10/13 15:39
 * @description
 */
public interface MathLibrary extends Library {

    MathLibrary mathLibrary = Native.load("src\\main\\resources\\MathLibrary.dll", MathLibrary.class);

    Point test_point(Point[] point, long count);

    Points[] test_point_two(List<Points> points, long count);

}
