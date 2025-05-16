package com.app.camerademo

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.app.camerademo.databinding.ItemTilesViewBinding
import com.bumptech.glide.Glide
import java.io.File

class TilesImageAdapter(
    private val files: List<File>,
    private val onClick: (File) -> Unit
) : RecyclerView.Adapter<TilesImageAdapter.ImageViewHolder>() {

    inner class ImageViewHolder(val binding: ItemTilesViewBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        return ImageViewHolder(
            ItemTilesViewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val file = files[position]
        Log.d("TilesAdapter", "Binding item at position $position: ${file.name}, exists: ${file.exists()}")

        val glideRequest = Glide.with(holder.itemView.context)

        if (file.extension.lowercase() == "mp4") {
            glideRequest
                .load(file)
                .frame(1000000) // get frame at 1 second
                .centerCrop()
                .into(holder.binding.folderImage)
            holder.binding.playIcon.visibility = View.VISIBLE
        } else {
            glideRequest
                .load(file)
                .centerCrop()
                .into(holder.binding.folderImage)

            holder.binding.playIcon.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            onClick(file)
        }
    }

    override fun getItemCount(): Int {
        Log.d("TilesAdapter", "Item count: ${files.size}")
        return files.size
    }
}