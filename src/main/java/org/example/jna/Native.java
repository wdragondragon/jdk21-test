package org.example.jna;

import java.lang.reflect.Proxy;

/**
 * @author JDragon
 * @date 2023/10/13 15:42
 * @description
 */
public class Native {

    public static <T extends Library> T load(String libPath, Class<T> interfaceClass) {

        Library.Handler handler = new Library.Handler(libPath);
        Object proxy = Proxy.newProxyInstance(interfaceClass.getClassLoader(), new Class[]{interfaceClass}, handler);

        return interfaceClass.cast(proxy);
    }
}
