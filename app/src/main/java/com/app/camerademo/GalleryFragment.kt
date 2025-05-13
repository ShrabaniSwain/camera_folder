package com.app.camerademo

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.app.camerademo.databinding.DialogInfoBinding
import com.app.camerademo.databinding.FragmentGalleryBinding
import com.bumptech.glide.Glide
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class GalleryFragment : Fragment() {

    private lateinit var binding: FragmentGalleryBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setup()
        if (isVideo().not()) imageSetup()
        super.onViewCreated(view, savedInstanceState)
    }

    private var player: ExoPlayer? = null

    override fun onDestroy() {
        player?.release()
        super.onDestroy()
    }

    override fun onPause() {
        player?.release()
        player = null
        super.onPause()
    }

    override fun onResume() {
        if (isVideo()) {
            player = ExoPlayer
                .Builder(requireContext())
                .build().apply {
                    repeatMode = ExoPlayer.REPEAT_MODE_ALL
                }
            videoSetup()
        }
        setup()
        super.onResume()
    }

    private fun isVideo(): Boolean {
        val data = requireArguments().getString("path") ?: ""
        return data.endsWith(".mp4")
    }

    private fun imageSetup() {
        val data = requireArguments().getString("path") ?: ""
        binding.playerPreview.visibility = View.GONE

        Glide.with(this)
            .load(data)
            .thumbnail(.1f)
            .into(binding.imagePreview)
    }

    private fun videoSetup() {
        val data = requireArguments().getString("path") ?: ""
        val isFront = File(data).name.contains("FRONT_VID", ignoreCase = true)

//        binding.playerPreview.scaleX = if (isFront) -1f else 1f // 👈 Mirror preview if front cam
        if (isFront) {
            // ✅ Mirror only the video rendering surface
            val videoSurfaceView = binding.playerPreview.videoSurfaceView
            videoSurfaceView?.scaleX = -1f
        } else {
            val videoSurfaceView = binding.playerPreview.videoSurfaceView
            videoSurfaceView?.scaleX = 1f
        }

        binding.playerPreview.hideController()
        binding.playerPreview.controllerShowTimeoutMs = 1500
        binding.imagePreview.visibility = View.GONE
        binding.playerPreview.player = player
        player?.setMediaItem(MediaItem.fromUri(data))
        player?.prepare()
        player?.play()
        binding.playerPreview.useController = false
        binding.playerPreview.setOnClickListener {
            if (!binding.playerPreview.useController) {
                binding.playerPreview.useController = true
            }
        }
    }


    private fun setup() {

        val data = requireArguments().getString("path") ?: ""

        binding.share.setOnClickListener {
            val intent = Intent(Intent.ACTION_SEND)

            if (data.endsWith(".jpeg", true) || data.endsWith(".jpg", true)) {
                intent.type = "image/jpeg"
            } else if (data.endsWith(".mp4", true)) {
                intent.type = "video/mp4"
            }

            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider", // ✅ Correct authority format
                File(data)
            )

            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            startActivity(Intent.createChooser(intent, "Share via"))
        }


        binding.delete.setOnClickListener {
            (requireActivity() as PreviewActivity).removeItem(data)
        }

        binding.rotate.setOnClickListener {
            if (isVideo()) {
                binding.playerPreview.rotation += 90f
                val w = binding.playerPreview.measuredWidth
                val h = binding.playerPreview.measuredHeight
                val lp = binding.playerPreview.layoutParams
                lp.height = w
                lp.width = h
                binding.playerPreview.layoutParams = lp
                binding.playerPreviewWrapper.requestLayout()
            } else {
                binding.imagePreview.rotation += 90
            }
        }

        binding.info.setOnClickListener {
            val dialog = Dialog(requireContext())
            val binding = DialogInfoBinding.inflate(LayoutInflater.from(requireContext()))

            val sdf = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault())

            val file = File(data)
            binding.name.text = file.name
            binding.createdAt.text = sdf.format(Date(file.lastModified()))
            binding.size.text = String.format("%.2f %s", file.length().toFloat() / 1048576f, "MB")
            binding.path.text = file.absolutePath

            dialog.setContentView(binding.root)
            dialog.show()
        }

    }



}