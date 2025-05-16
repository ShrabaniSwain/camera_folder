package com.app.camerademo

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.app.camerademo.databinding.ActivityTilesFolderViewBinding
import java.io.File

class TilesFolderViewActivity : AppCompatActivity() {
    private val binding by lazy {
        ActivityTilesFolderViewBinding.inflate(layoutInflater)
    }

    private val imageFiles = mutableListOf<File>()
    private lateinit var adapter: TilesImageAdapter
    private lateinit var folderPath: String
    private var isFirstLoad = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.back.setOnClickListener { finish() }
        load()

        adapter = TilesImageAdapter(imageFiles) { file ->
            val intent = Intent(this, PreviewActivity::class.java)
            Log.d("TilesActivity", "Opening preview for: ${file.absolutePath}")
            intent.putExtra("path", folderPath)
            intent.putExtra("hide", true)
            intent.putExtra("selectedFile", file.absolutePath)
            startActivity(intent)
        }

        binding.list.layoutManager = GridLayoutManager(this, 3)
        binding.list.adapter = adapter

    }

    @SuppressLint("NotifyDataSetChanged")
    private fun load() {
        folderPath = intent.getStringExtra("path") ?: ""
        Log.i("TilesActivity", "Loading folder path: $folderPath")
        val label = intent.getStringExtra("folderName") ?: File(folderPath).name
        binding.folderName.text = label
        Thread {
            val folder = File(folderPath)
            val files = folder.listFiles()?.filter { file ->
                file.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp", "mp4")
            }?.sortedByDescending { it.lastModified() } ?: listOf()
            Log.d("TilesActivity", "Found ${files.size} media files")

            imageFiles.clear()

            val firstFew = files.take(5)
            val remaining = files.drop(5)
            imageFiles.addAll(firstFew)

            // Notify adapter on main thread
            runOnUiThread {
                adapter.notifyDataSetChanged()
                binding.emptyText.visibility = if (imageFiles.isEmpty()) View.VISIBLE else View.GONE
            }
            loadExtra(remaining)
        }.start()
    }

    override fun onResume() {
        super.onResume()
        if (!isFirstLoad) {
            load()
        }
        isFirstLoad = false
    }


    @SuppressLint("NotifyDataSetChanged")
    private fun loadExtra(remainingFiles: List<File>) {
        Thread {
            Thread.sleep(500)  // Smooth delay
            for (file in remainingFiles) {
                imageFiles.add(file)
                Handler(Looper.getMainLooper()).post {
                    adapter.notifyDataSetChanged()
                }
            }
        }.start()
    }

}