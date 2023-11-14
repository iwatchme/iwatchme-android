package com.iwatchme.jetpackstarter;

import java.util.concurrent.ThreadPoolExecutor;

public class Test {

    void test() {
        ThreadPoolExecutor executor = new ProxyThreadExecutor2(1, 1, 1, null, null, "");
    }
}
