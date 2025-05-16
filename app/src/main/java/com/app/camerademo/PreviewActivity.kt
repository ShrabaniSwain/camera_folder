package com.app.camerademo

import android.content.DialogInterface
import android.content.DialogInterface.OnClickListener
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.app.camerademo.databinding.ActivityPreviewBinding
import java.io.File

class PreviewActivity : AppCompatActivity() {

    private val allFiles = mutableListOf<File>()
    private val files = mutableListOf<String>()

    private val binding: ActivityPreviewBinding by lazy { ActivityPreviewBinding.inflate(layoutInflater) }

    private lateinit var adapter: MyAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        load()
    }

    private fun load() {
        val path = intent.getStringExtra("path") ?: ""
        val selectedFile = intent.getStringExtra("selectedFile")
        Log.i("PreviewActivity", "Path $path")

        Thread {
            allFiles.clear()
            files.clear()

            // ✅ Detect if we're previewing Drive media
            val isDriveMode = path.contains("Drive")

            // ✅ Choose the correct folder
            val folder = if (isDriveMode) {
                File(getExternalFilesDir("videos") ?: filesDir, "")
            } else {
                File(path)
            }

            // ✅ Load and filter media files
            val kFiles = folder.listFiles()?.filter {
                it.extension.lowercase() in listOf("jpg", "jpeg", "mp4") &&
                        (it.length() > 100 * 1024 || !it.name.endsWith(".mp4")) // Skip small/broken videos
            }?.sortedByDescending { it.lastModified() } ?: listOf()

            allFiles.addAll(kFiles)
            files.addAll(kFiles.map { it.absolutePath })

            val index = files.indexOf(selectedFile)

            Handler(Looper.getMainLooper()).post {
                adapter = MyAdapter()
                binding.previews.adapter = adapter
                setup()

                if (index != -1) {
                    binding.previews.setCurrentItem(index, false)
                }
            }
        }.start()
    }

    fun removeItem(path: String) {
        val alertDialog = AlertDialog.Builder(this)
            .setTitle("Delete")
            .setMessage("Are you sure you want to delete this file?")
            .setPositiveButton("Yes", object : OnClickListener {
                override fun onClick(dialog: DialogInterface?, which: Int) {
                    dialog?.dismiss()
                    val index = files.indexOf(path)
                    Log.i("PreviewActivity", "index $index")
                    if (index == -1) return
                    files.removeAt(index)
                    File(path).delete()
                    adapter = MyAdapter()
                    binding.previews.adapter = adapter
                    binding.previews.setCurrentItem(index.coerceAtMost(files.size - 1), false)
                    binding.msg.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
                }
            })
            .setNegativeButton("No") { dialog, _ -> dialog?.dismiss() }
            .create()

        alertDialog.show()
    }

    private fun setup() {
        binding.back.setOnClickListener { finish() }
        binding.folders.setOnClickListener {
            startActivity(Intent(this, FolderActivity::class.java))
        }

        binding.previews.offscreenPageLimit = 20
        binding.progress.visibility = View.GONE
        binding.msg.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        binding.folders.visibility = if (intent.getBooleanExtra("hide", false)) View.GONE else View.VISIBLE
    }

    inner class MyAdapter : FragmentStateAdapter(this) {

        override fun getItemCount() = files.size

        override fun createFragment(position: Int): Fragment {
            return GalleryFragment().apply {
                arguments = Bundle().apply {
                    putString("path", files[position])
                    putBoolean("isFrontCamera", intent.getBooleanExtra("isFrontCamera", false))
                }
            }
        }
    }
}
