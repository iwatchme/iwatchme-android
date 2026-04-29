package com.iwatchme.renderengine

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView

class RenderEngineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    val engine = RenderEngine()

    init {
        holder.addCallback(this)
        engine.create()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        engine.setSurface(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Surface dimensions changed — the native side handles viewport via ANativeWindow
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        engine.setSurface(null)
    }

    fun release() {
        engine.destroy()
    }
}
