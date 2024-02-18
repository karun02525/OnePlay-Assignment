package com.oneplay.ui.adapter

import android.net.Uri
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.oneplay.data.Recording
import com.oneplay.databinding.ItemRecordingBinding
import com.oneplay.utils.showShareRecordingDialog
import java.text.DecimalFormat
import kotlin.math.log10
import kotlin.math.pow


class RecordingAdapter(private val items: List<Recording> = emptyList()) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding =
            ItemRecordingBinding.inflate(LayoutInflater.from(viewGroup.context), viewGroup, false)
        return RecordingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val recording = items[position]
        val recordingViewHolder = holder as RecordingViewHolder
        recordingViewHolder.bind(items[position])
    }

    override fun getItemCount() = items.size
}

class RecordingViewHolder(private val binding: ItemRecordingBinding) :
    RecyclerView.ViewHolder(binding.root) {

    var recording: Recording? = null
    var pos: Int = -1

    fun bind(recording: Recording) {
        this.recording = recording
        this.pos = position
        binding.apply {
            thumbnail.load(recording.uri)
            title.text = recording.title
            duration.text = toTime(recording.duration.toLong())
            modified.text = DateUtils.getRelativeTimeSpanString(recording.modified)
            size.text = getFileSize(recording.size)
            container.setOnClickListener {
                it.context.showShareRecordingDialog(recording)
            }

        }
    }


    private fun ImageView.load(uri: Uri) {
        Glide.with(this)
            .asBitmap()
            .centerCrop()
            .load(uri)
            .into(this)
    }

    private fun getFileSize(size: Long): String {
        if (size <= 0)
            return "0"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
        return DecimalFormat("#,##0.#").format(size / 1024.0.pow(digitGroups.toDouble())) + " " + units[digitGroups]
    }

    private fun toTime(millis: Long): String {
        val hours: Long = (millis / (1000 * 60 * 60))
        val minutes = (millis % (1000 * 60 * 60) / (1000 * 60))
        val seconds = (millis % (1000 * 60 * 60) % (1000 * 60) / 1000)
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

}
