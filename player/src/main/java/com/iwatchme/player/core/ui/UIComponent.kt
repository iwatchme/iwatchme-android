package com.iwatchme.player.core.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding

interface UIComponent<E : UIComponent.ViewEntry> {

    fun createViewEntry(
        context: Context,
        parent: ViewGroup? = null,
    ): E

    suspend fun bindToView(viewEntry: E)

    fun onViewRecycled(viewEntry: E) = Unit

    fun viewReusingKey(): Any = this::class.java

    fun contentEqualityKey(): Any = this

    fun identityEqualityKey(): Any = contentEqualityKey()

    interface ViewEntry {
        val root: View
    }

    class ViewViewEntry<V : View>(val value: V) : ViewEntry {
        override val root: View get() = value
    }

    class ViewBindingViewEntry<VB : ViewBinding>(val value: VB) : ViewEntry {
        override val root: View get() = value.root
    }
}
