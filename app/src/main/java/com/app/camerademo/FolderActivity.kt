package com.app.camerademo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app.camerademo.databinding.ActivityFolderBinding
import com.app.camerademo.databinding.ItemFolderBinding
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
        val layoutManager = GridLayoutManager(this, 1)
        binding.list.layoutManager = layoutManager
        binding.list.adapter = adapter
    }

    private fun lastFile(folder: String) {
        val file = File("${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)}/$folder/")
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
            holder.binding.text.text = item


            holder.itemView.setOnClickListener {
                val intent = Intent(this@FolderActivity, PreviewActivity::class.java)
                intent.putExtra("path", parents[item])
                intent.putExtra("hide", true)
                startActivity(intent)
            }

        }

    }

}