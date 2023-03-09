package com.iwatchme.jetpackstarterplayer;

import android.view.Surface;

public class Decoder {
    static {
        System.loadLibrary("native-lib");
    }
    public native void decodeToSurface(String path, Surface surface);
}
