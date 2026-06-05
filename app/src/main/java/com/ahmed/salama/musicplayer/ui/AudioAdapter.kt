package com.ahmed.salama.musicplayer.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.ahmed.salama.musicplayer.R
import com.ahmed.salama.musicplayer.model.AudioItem
import java.util.Locale

class AudioAdapter(context: Context) : BaseAdapter() {
    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private val items = mutableListOf<AudioItem>()
    // Keep track of the currently selected item index. The default is -1 meaning nothing is selected.
    private var selectedPosition: Int = -1

    fun submitList(newItems: List<AudioItem>?) {
        items.clear()
        if (newItems != null) {
            items.addAll(newItems)
        }
        notifyDataSetChanged()
    }

    fun getItemsCopy(): ArrayList<AudioItem> = ArrayList(items)

    /**
     * Update the adapter to mark a given position as selected. This will trigger
     * a view refresh causing the selected row to use a different background. Passing
     * -1 will clear the selection.
     */
    fun setSelectedPosition(position: Int) {
        selectedPosition = position
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): AudioItem = items[position]

    override fun getItemId(position: Int): Long = items[position].id

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val holder: ViewHolder
        val view: View

        if (convertView == null) {
            view = inflater.inflate(R.layout.item_audio, parent, false)
            holder = ViewHolder(view)
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as ViewHolder
        }

        val item = getItem(position)
        holder.title.text = item.title
        holder.subtitle.text = "${item.artist} • ${item.album}"
        holder.duration.text = formatDuration(item.durationMs)

        // Highlight the selected item using a distinct background color; otherwise use the default background.
        val ctx = inflater.context
        val selectedColor = ctx.getColor(R.color.selectedItemBackground)
        val defaultColor = ctx.getColor(R.color.white)
        if (position == selectedPosition) {
            view.setBackgroundColor(selectedColor)
        } else {
            view.setBackgroundColor(defaultColor)
        }

        return view
    }

    private class ViewHolder(view: View) {
        val title: TextView = view.findViewById(R.id.textTitle)
        val subtitle: TextView = view.findViewById(R.id.textSubtitle)
        val duration: TextView = view.findViewById(R.id.textDuration)
    }

    companion object {
        fun formatDuration(durationMs: Long): String {
            val totalSeconds = maxOf(0, durationMs / 1000)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }
}

