package org.example;

import java.lang.reflect.Proxy;
import java.util.Collections;

/**
 * @author JDragon
 * @date 2023/10/13 15:42
 * @description
 */
public class Native {

    public static <T extends Library> T load(String libPath, Class<T> interfaceClass) {

        Library.Handler handler = new Library.Handler(libPath, interfaceClass, Collections.emptyMap());
        Object proxy = Proxy.newProxyInstance(interfaceClass.getClassLoader(), new Class[]{interfaceClass}, handler);

        return interfaceClass.cast(proxy);
    }
}
