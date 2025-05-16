package com.app.camerademo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app.camerademo.databinding.ActivityFolderBinding
import com.app.camerademo.databinding.ItemFolderBinding
import com.bumptech.glide.Glide
import java.io.File

class FolderActivity : AppCompatActivity() {

    val folders = mutableListOf<String>()
    val parents = mutableMapOf<String, String?>()

    private val binding by lazy {
        ActivityFolderBinding.inflate(layoutInflater)
    }
    private val adapter by lazy {
        MyAdapter()
    }
    private var isFirstLoad = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.back.setOnClickListener { finish() }
        loadFolder()
    }

    private fun loadFolder() {
        val pref = getSharedPreferences(packageName, Context.MODE_PRIVATE)
        folders.clear()
        folders.addAll(pref.getStringSet("folders", setOf()) ?: setOf())
        folders.addAll(pref.getStringSet("date_folders", setOf()) ?: setOf())
        folders.sort()
        folders.forEach { lastFile(it) }
        binding.list.layoutManager = GridLayoutManager(this, 3)
        binding.list.adapter = adapter
    }

    private fun lastFile(folder: String) {
        val file = if (folder.startsWith("Drive:")) {
            // 🔄 Point to internal app folder where media is saved before Drive upload
            File(getExternalFilesDir("videos") ?: filesDir, "")
        } else {
            File("${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)}/$folder/")
        }
        parents[folder] = file.absolutePath
    }


    inner class MyAdapter: RecyclerView.Adapter<MyAdapter.MyViewHolder>() {

        inner class MyViewHolder(val binding: ItemFolderBinding): RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
            return MyViewHolder(
                ItemFolderBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )
        }

        override fun getItemCount(): Int {
            return folders.size
        }

        override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
            val item = folders[holder.absoluteAdapterPosition]
            holder.binding.folderName.text = item

            val folderPath = parents[item]
            val folderFile = folderPath?.let { File(it) }
            val imageFile = folderFile?.listFiles()?.filter { it.isFile }?.maxByOrNull { it.lastModified() }
            if (imageFile != null) {
                Glide.with(this@FolderActivity)
                    .load(imageFile)
                    .centerCrop()
                    .into(holder.binding.folderImage)
            }

            holder.itemView.setOnClickListener {
                val intent = Intent(this@FolderActivity, TilesFolderViewActivity::class.java)
                intent.putExtra("path", parents[item])
                intent.putExtra("folderName", item)
                intent.putExtra("hide", true)// Pass folder path
                startActivity(intent)
                Log.d("FolderActivity", "Opening folder path: ${parents[item]}")
            }

        }

    }

    override fun onResume() {
        super.onResume()
        if (!isFirstLoad) {
            loadFolder()
        }
        isFirstLoad = false
    }



}