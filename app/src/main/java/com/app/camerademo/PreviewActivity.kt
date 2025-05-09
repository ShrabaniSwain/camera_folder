package com.app.camerademo

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.DialogInterface.OnClickListener
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
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
    private val adapter: MyAdapter by lazy { MyAdapter() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        load()
    }

    private fun load() {
        val path = intent.getStringExtra("path") ?: ""
        Log.i("423u5", "Path $path")
        Thread {
            allFiles.clear()
            val folder = File(path)
            val kFiles = folder.listFiles() ?: arrayOf()
            kFiles.sortByDescending { x -> x.lastModified() }
            allFiles.addAll(
                kFiles.filter { file ->
                    file.extension.lowercase() in listOf("jpg", "jpeg", "mp4")
                }
            )
            for (x in allFiles) {
                files.add(x.absolutePath)
            }
            Handler(Looper.getMainLooper()).post { setup() }
            loadExtra()
        }.start()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadExtra() {
        Thread {
            Thread.sleep(1000)
            val start = System.currentTimeMillis()
            if (allFiles.size > 5)
                allFiles.subList(5, allFiles.size).forEach { x ->
                    files.add(x.absolutePath)
                    Handler(Looper.getMainLooper()).post { adapter.notifyDataSetChanged() }
                }
        }.start()
    }

    fun removeItem(path: String) {

        val alertDialog = AlertDialog.Builder(this)
            .setTitle("Delete")
            .setMessage("Are you sure you want to delete this file?")
            .setPositiveButton("Yes", object: OnClickListener {
                override fun onClick(dialog: DialogInterface?, which: Int) {
                    dialog?.dismiss()
                    val index =  files.indexOf(path)
                    Log.i("423u5", "index $index")
                    if (index == -1) return
                    files.removeAt(index)
                    File(path).delete()
                    binding.previews.adapter = MyAdapter()
                    binding.previews.setCurrentItem(index, false)
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
            val intent = Intent(this, FolderActivity::class.java)
            startActivity(intent)
        }

        binding.previews.offscreenPageLimit = 20
        binding.previews.adapter = adapter
        binding.previews.post {
            binding.progress.visibility = View.GONE
        }

        binding.msg.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        binding.folders.visibility = if (intent.getBooleanExtra("hide", false)) View.GONE else View.VISIBLE

    }

    inner class MyAdapter: FragmentStateAdapter(this) {

        override fun getItemCount() = files.size

        override fun createFragment(position: Int): Fragment {
            return generateFragment(position)
        }

        private fun generateFragment(position: Int): Fragment {
            val frag = GalleryFragment()
            frag.arguments = Bundle().apply {
                putString("path", files[position])
                putBoolean("isFrontCamera", intent.getBooleanExtra("isFrontCamera", false)) // 👈 Pass here
            }
            return frag
        }

    }

}