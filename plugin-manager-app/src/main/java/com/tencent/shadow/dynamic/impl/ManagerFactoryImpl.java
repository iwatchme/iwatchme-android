/*
 * 此类的包名 + 类名是 Shadow 框架固定查找的入口：
 * com.tencent.shadow.dynamic.impl.ManagerFactoryImpl
 * Shadow 加载 manager.apk 后通过反射 newInstance 取得这个类的实例。
 * 见 com.tencent.shadow.dynamic.host.DynamicPluginManager 内部对该名称的引用。
 */
package com.tencent.shadow.dynamic.impl;

import android.content.Context;

import com.iwatchme.plugin.manager.IwatchmePluginManager;
import com.tencent.shadow.dynamic.host.ManagerFactory;
import com.tencent.shadow.dynamic.host.PluginManagerImpl;

public final class ManagerFactoryImpl implements ManagerFactory {
    @Override
    public PluginManagerImpl buildManager(Context context) {
        return new IwatchmePluginManager(context);
    }
}
