package com.ahmed.salama.musicplayer.ui

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.ahmed.salama.musicplayer.R
import com.ahmed.salama.musicplayer.model.AudioItem
import com.bumptech.glide.Glide
import java.util.Locale

class AudioAdapter(
    private val onItemClick: (item: AudioItem, position: Int) -> Unit
) : RecyclerView.Adapter<AudioAdapter.AudioViewHolder>() {

    private val items = mutableListOf<AudioItem>()
    private var selectedPosition: Int = RecyclerView.NO_POSITION

    fun submitList(newItems: List<AudioItem>?) {
        items.clear()
        if (newItems != null) {
            items.addAll(newItems)
        }
        selectedPosition = RecyclerView.NO_POSITION
        notifyDataSetChanged()
    }

    fun getItemsCopy(): ArrayList<AudioItem> {
        return ArrayList(items)
    }

    fun getItem(position: Int): AudioItem {
        return items[position]
    }

    fun setSelectedPosition(position: Int) {
        val oldPosition = selectedPosition
        selectedPosition = position

        if (oldPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(oldPosition)
        }

        if (selectedPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(selectedPosition)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AudioViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_audio, parent, false)

        return AudioViewHolder(view)
    }

    override fun onBindViewHolder(holder: AudioViewHolder, position: Int) {
        val item = items[position]
        val isSelected = position == selectedPosition
        holder.bind(item, isSelected)

        holder.itemView.setOnClickListener {
            val currentPosition = holder.bindingAdapterPosition
            if (currentPosition == RecyclerView.NO_POSITION) return@setOnClickListener

            setSelectedPosition(currentPosition)
            onItemClick(items[currentPosition], currentPosition)
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    class AudioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageArtwork: ImageView = itemView.findViewById(R.id.imageArtwork)
        private val title: TextView = itemView.findViewById(R.id.textTitle)
        private val subtitle: TextView = itemView.findViewById(R.id.textSubtitle)
        private val duration: TextView = itemView.findViewById(R.id.textDuration)

        fun bind(item: AudioItem, isSelected: Boolean) {
            title.text = item.displayTitle
            subtitle.text = "${item.displayArtist} • ${item.displayAlbum}"
            duration.text = formatDuration(item.durationMs)

            val background = if (isSelected) {
                R.drawable.audio_item_blue_bg
            } else {
                R.drawable.audio_item_bg
            }
            itemView.setBackgroundResource(background)

            Glide.with(itemView.context.applicationContext)
                .load(item.artworkUriString?.let { Uri.parse(it) })
                .placeholder(R.drawable.ic_music)
                .error(R.drawable.ic_music)
                .centerCrop()
                .into(imageArtwork)

        }
    }

    fun indexOfAudioId(audioId: Long): Int {
        return items.indexOfFirst { it.id == audioId }.takeIf { it >= 0 }
            ?: RecyclerView.NO_POSITION
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