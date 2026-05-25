/*
 * Loader 端的 WhiteList —— 与 plugin-manager-app 中的对齐。
 */
package com.tencent.shadow.dynamic.impl;

public interface WhiteList {
    String[] sWhiteList = new String[]{
            "com.iwatchme.host.shadow",
            "com.iwatchme.android",
    };
}
