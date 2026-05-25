/*
 * 此类的包名 + 类名是 Shadow 框架固定查找的入口：
 * com.tencent.shadow.dynamic.loader.impl.CoreLoaderFactoryImpl
 * Shadow 加载 loader.apk 后通过反射 newInstance 取得这个类的实例。
 * 见 com.tencent.shadow.dynamic.loader.impl.DynamicPluginLoader#CORE_LOADER_FACTORY_IMPL_NAME
 */
package com.tencent.shadow.dynamic.loader.impl;

import android.content.Context;

import com.iwatchme.plugin.loader.IwatchmePluginLoader;
import com.tencent.shadow.core.loader.ShadowPluginLoader;

public class CoreLoaderFactoryImpl implements CoreLoaderFactory {
    @Override
    public ShadowPluginLoader build(Context hostAppContext) {
        return new IwatchmePluginLoader(hostAppContext);
    }
}
