package com.iwatchme.player.core.ui

import android.util.Log
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class UIComponentAdapterImpl(
    private val bindingScope: CoroutineScope,
) : RecyclerView.Adapter<UIComponentAdapterImpl.ViewHolder>() {

    private var components: List<RunningUIComponent> = emptyList()

    fun submitList(newComponents: List<RunningUIComponent>) {
        components = newComponents
        Log.d("Player", "[UIComponentAdapter] submitList: ${newComponents.size} components")
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = components.size

    override fun getItemViewType(position: Int): Int {
        return components[position].uiComponent.viewReusingKey().hashCode()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val component = components.first { it.uiComponent.viewReusingKey().hashCode() == viewType }
        val viewEntry = component.uiComponent.createViewEntry(parent.context, parent)
        return ViewHolder(viewEntry)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(components[position].uiComponent, bindingScope)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.unbind()
    }

    class ViewHolder(
        private val viewEntry: UIComponent.ViewEntry,
    ) : RecyclerView.ViewHolder(viewEntry.root) {

        private var bindJob: Job? = null

        @Suppress("UNCHECKED_CAST")
        fun bind(component: UIComponent<*>, scope: CoroutineScope) {
            bindJob?.cancel()
            bindJob = scope.launch {
                (component as UIComponent<UIComponent.ViewEntry>).bindToView(viewEntry)
            }
        }

        fun unbind() {
            bindJob?.cancel()
            bindJob = null
        }
    }
}
