package com.iwatchme.player.feature.playerpage.uicomponent

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.iwatchme.player.core.ui.UIComponent
import kotlinx.coroutines.awaitCancellation

class PlayerGestureSurfaceUIComponent(
    private val touchHandler: (event: MotionEvent, surfaceWidth: Int, surfaceHeight: Int) -> Boolean,
) : UIComponent<UIComponent.ViewViewEntry<View>> {

    override fun createViewEntry(
        context: Context,
        parent: ViewGroup?,
    ): UIComponent.ViewViewEntry<View> {
        return UIComponent.ViewViewEntry(View(context))
    }

    fun wrapExistingView(view: View): UIComponent.ViewViewEntry<View> {
        return UIComponent.ViewViewEntry(view)
    }

    @SuppressLint("ClickableViewAccessibility")
    override suspend fun bindToView(viewEntry: UIComponent.ViewViewEntry<View>) {
        val view = viewEntry.value
        view.setOnTouchListener { v, event -> touchHandler(event, v.width, v.height) }
        try {
            awaitCancellation()
        } finally {
            view.setOnTouchListener(null)
        }
    }
}
