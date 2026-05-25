/*
 * 此接口的包名 + 名称是 Shadow 框架固定查找的入口。
 * 列出宿主中允许插件 ClassLoader 反向访问的类前缀。
 * 我们只暴露 host-shadow 的 container 包（壳子 Activity 基类等）。
 */
package com.tencent.shadow.dynamic.impl;

public interface WhiteList {
    String[] sWhiteList = new String[]{
            "com.iwatchme.host.shadow",
            "com.iwatchme.android",
    };
}
