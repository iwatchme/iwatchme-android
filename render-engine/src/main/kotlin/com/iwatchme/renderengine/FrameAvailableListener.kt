package com.iwatchme.renderengine

import android.graphics.SurfaceTexture

/**
 * JNI bridge for SurfaceTexture.OnFrameAvailableListener.
 * Created from C++ (SurfaceTextureHelper) and calls back into native code
 * when a new decoded frame is available on the SurfaceTexture.
 */
class FrameAvailableListener(private val nativePtr: Long) : SurfaceTexture.OnFrameAvailableListener {
    override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
        nativeOnFrameAvailable(nativePtr)
    }

    private external fun nativeOnFrameAvailable(nativePtr: Long)
}
