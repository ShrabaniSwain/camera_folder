package com.app.camerademo

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.MeteringRectangle
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.app.camerademo.databinding.ActivityMainBinding
import com.bumptech.glide.Glide
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import androidx.core.view.isInvisible
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@SuppressLint("RestrictedApi")
class MainActivity : AppCompatActivity() {

    private val requiredPermission = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    ).apply {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
            }

            Build.VERSION.SDK_INT in Build.VERSION_CODES.Q until Build.VERSION_CODES.TIRAMISU -> {
            }

            else -> {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }


    private lateinit var cameraId: String
    private var zoomLevel = 1.0f
    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private lateinit var previewSize: Size
    private lateinit var previewRequestBuilder: CaptureRequest.Builder
    private lateinit var previewSurface: Surface
    private lateinit var imageReader: ImageReader
    private var isFrontCamera = false
    private var isRecording = false
    private lateinit var mediaRecorder: MediaRecorder
    private var videoFile: File? = null
    private var lastCapturedPath: String? = null
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private var selectedDCIMFolderName: String = "Gallery"
    private var flash = 0
    private var zoom = 0f
    private var isCapture = true
    private var isBackCamera = true
    private var lastPath = ""
    private var path = ""
    private var hasRecordingStarted = false
    private var wasInBackground = false
    private var isDriveMode = false
    private var driveFolderId: String? = null
    private var driveClient: Drive? = null
    private val signInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            if (result.resultCode == Activity.RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                handleSignInResult(task)
            } else {
                handleSignInResult(task)
                Log.e("DriveUpload", "Sign-in cancelled or failed")
            }
        }


    @RequiresPermission(Manifest.permission.CAMERA)
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("TAG", "camera: " + "oncraetecamera")
        setContentView(binding.root)

        if (isPermissionGranted())
            startCamera()
        else
            requestPermission()

        Log.i("423u5", "cache path ${cacheDir.path}")
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        Log.i("TAG", "onCreate: $cameraManager")
        binding.capture.setOnClickListener {
            if (path.isBlank()  && !isDriveMode) return@setOnClickListener
            if (isCapture) {
                binding.capture.isEnabled = false
                binding.progressBar.visibility = View.VISIBLE
                binding.capture.visibility = View.INVISIBLE
                capturePhoto()
            } else {
                recordVideo()
            }
        }

        binding.cameraSwitch.setOnClickListener {
            if (path.isBlank() && !isDriveMode) return@setOnClickListener
            isBackCamera = !isBackCamera
            isFrontCamera = !isBackCamera
            closeCamera()
            startCamera()
        }

        binding.photoClick.isEnabled = false
        binding.videoClick.isEnabled = false
        binding.capture.isEnabled = false
        binding.cameraSwitch.isEnabled = false
        binding.flash.isEnabled = false

        binding.flash.setOnClickListener {
            if (path.isBlank() && !isDriveMode) return@setOnClickListener

            // Cycle flash modes: 0 = OFF, 1 = ON, 2 = AUTO (photo only)
            flash = (flash + 1) % if (isCapture) 3 else 2

            // Update icon
            binding.flash.setImageResource(
                when (flash) {
                    0 -> R.mipmap.ic_flash_off
                    1 -> R.mipmap.ic_flash_on
                    else -> R.mipmap.ic_flash_auto
                }
            )

            if (isCapture && !isRecording) {
                // 💡 Photo Mode Flash config
                previewRequestBuilder.set(
                    CaptureRequest.CONTROL_AE_MODE,
                    when (flash) {
                        0 -> CaptureRequest.CONTROL_AE_MODE_ON
                        1 -> CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH
                        2 -> CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                        else -> CaptureRequest.CONTROL_AE_MODE_ON
                    }
                )

                previewRequestBuilder.set(
                    CaptureRequest.FLASH_MODE,
                    when (flash) {
                        0 -> CaptureRequest.FLASH_MODE_OFF
                        1, 2 -> CaptureRequest.FLASH_MODE_SINGLE
                        else -> CaptureRequest.FLASH_MODE_OFF
                    }
                )

                captureSession?.setRepeatingRequest(previewRequestBuilder.build(), null, null)
            }

            if (!isCapture && !isRecording) {
                // 🔦 Video Mode — toggle torch using CameraManager
                if (flash == 1) {
                    turnFlashOn(this)
                } else {
                    turnFlashOff(this)
                }
            }
        }

        binding.photoClick.setOnClickListener {
            if (!isCapture) {
                isCapture = true
                switchModeUI()
            }
        }

        binding.videoClick.setOnClickListener {
            if (isCapture) {
                isCapture = false
                switchModeUI()
            }
        }


        binding.mediaPreview.setOnClickListener {
            if (lastPath.isBlank() && !isDriveMode || binding.mediaPreview.isInvisible) return@setOnClickListener
            val intent = Intent(this, PreviewActivity::class.java)
            intent.putExtra("path", File(lastPath).parent)
            startActivity(intent)
        }


        binding.newFolder.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveFolder(binding.newFolder.text.toString().trim())
            }
            return@setOnEditorActionListener false
        }

        renderFolders()

        binding.today.setOnClickListener {
            val cal = Calendar.getInstance()
            val name =
                "${cal.get(Calendar.DAY_OF_MONTH)}-${cal.get(Calendar.MONTH) + 1}-${cal.get(Calendar.YEAR)}"
            onFolderSelect(name)
            saveDateFolder(name)
        }

        binding.gallery.setOnClickListener {
            selectedDCIMFolderName = "Gallery"
            onFolderSelect("DCIM/Camera")
        }

        binding.cameraRoll.setOnClickListener {
            selectedDCIMFolderName = "Camera Roll"
            onFolderSelect("DCIM/Camera")
        }

        val scaleGestureDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    zoomLevel *= detector.scaleFactor
                    adjustZoom(zoomLevel) // call Camera2 zoom method
                    return true
                }
            })

        binding.previewView.setOnTouchListener { _, event ->
            if (path.isBlank() && !isDriveMode) return@setOnTouchListener false

            // Handle pinch-to-zoom
            scaleGestureDetector.onTouchEvent(event)

            // Handle tap-to-focus
            if (event.action == MotionEvent.ACTION_UP) {
                // Position focus indicator at touch point (centered on touch)
                binding.focus.visibility = View.VISIBLE
                val (mappedX, mappedY) = mapTouchToPreviewCoordinates(event.x, event.y)
                binding.focus.x = mappedX - (binding.focus.width / 2)
                binding.focus.y = mappedY - (binding.focus.height / 2)


                // Get metering rectangle for focus
                val meteringRect = getFocusMeteringRect(event.x, event.y)

                // Apply focus if possible
                if (::previewRequestBuilder.isInitialized && captureSession != null && meteringRect != null) {
                    try {
                        // Lock focus for this operation
                        captureSession?.stopRepeating()

                        // Set regions and trigger focus
                        previewRequestBuilder.set(
                            CaptureRequest.CONTROL_AF_REGIONS,
                            arrayOf(meteringRect)
                        )
                        previewRequestBuilder.set(
                            CaptureRequest.CONTROL_AE_REGIONS,
                            arrayOf(meteringRect)  // Also set autoexposure region
                        )
                        previewRequestBuilder.set(
                            CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_AUTO
                        )
                        previewRequestBuilder.set(
                            CaptureRequest.CONTROL_AF_TRIGGER,
                            CameraMetadata.CONTROL_AF_TRIGGER_START
                        )

                        // Capture to apply focus
                        captureSession?.capture(
                            previewRequestBuilder.build(),
                            object : CameraCaptureSession.CaptureCallback() {
                                override fun onCaptureCompleted(
                                    session: CameraCaptureSession,
                                    request: CaptureRequest,
                                    result: TotalCaptureResult
                                ) {
                                    // Reset AF trigger
                                    previewRequestBuilder.set(
                                        CaptureRequest.CONTROL_AF_TRIGGER,
                                        CameraMetadata.CONTROL_AF_TRIGGER_IDLE
                                    )

                                    // Resume preview
                                    try {
                                        session.setRepeatingRequest(
                                            previewRequestBuilder.build(),
                                            null,
                                            null
                                        )
                                    } catch (e: Exception) {
                                        Log.e("FocusResume", "Error resuming preview", e)
                                    }
                                }
                            },
                            null
                        )

                        // Animate focus UI slightly to indicate activity
                        binding.focus.animate().scaleX(0.8f).scaleY(0.8f).setDuration(100)
                            .withEndAction {
                                binding.focus.animate().scaleX(1f).scaleY(1f).setDuration(100)
                                    .start()
                            }.start()

                    } catch (e: Exception) {
                        Log.e("TouchFocus", "Error applying focus", e)
                        // Resume preview in case of error
                        try {
                            captureSession?.setRepeatingRequest(
                                previewRequestBuilder.build(),
                                null,
                                null
                            )
                        } catch (e2: Exception) {
                            Log.e("FocusResume", "Error resuming preview after focus failure", e2)
                        }
                    }
                }

                // Hide focus UI after delay
                Handler(Looper.getMainLooper()).postDelayed({
                    binding.focus.visibility = View.GONE
                }, 2000)
            }

            true
        }

        binding.newFolder.setOnFocusChangeListener { _, hasFocus ->
            binding.newFolder.hint = ""
        }

        binding.previewView.setOnClickListener { }

        binding.chooseFolder.setOnClickListener {
            renderFolders()
            path = ""
            binding.mediaPreview.visibility = View.INVISIBLE
            binding.newFolder.setText("")
            binding.newFolder.hint = "+ Create New Folder"
            binding.folder.text = path
            binding.chooseFolder.visibility = View.GONE
            binding.pathUi.visibility = View.VISIBLE

            binding.photoClick.isEnabled = false
            binding.videoClick.isEnabled = false
            binding.capture.isEnabled = false
            binding.cameraSwitch.isEnabled = false
            binding.flash.isEnabled = false

            binding.blurView.visibility = View.VISIBLE
        }
        binding.uploadToDrive.setOnClickListener {
            requestSignIn()
        }


    }

    private fun requestSignIn() {
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        val client = GoogleSignIn.getClient(this, signInOptions)
        signInLauncher.launch(client.signInIntent)
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            Log.d("DriveUpload", "User signed in: ${account.email}")
            Toast.makeText(this, "Signed in as ${account.email}", Toast.LENGTH_SHORT).show()

            val credential = GoogleAccountCredential.usingOAuth2(
                this, listOf(DriveScopes.DRIVE_FILE)
            )
            credential.selectedAccount = account.account

            val driveService = Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("CameraDemo").build()

            // Disable UI during folder setup
            setUiEnabled(false)

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val folderMetadata = com.google.api.services.drive.model.File().apply {
                        name = "camfolder"
                        mimeType = "application/vnd.google-apps.folder"
                    }

                    val result = driveService.files().list()
                        .setQ("name='camfolder' and mimeType='application/vnd.google-apps.folder'")
                        .setSpaces("drive")
                        .execute()

                    val folderId = if (result.files.isEmpty()) {
                        driveService.files().create(folderMetadata)
                            .setFields("id")
                            .execute()
                            .id
                    } else {
                        result.files[0].id
                    }

                    // Store drive setup state
                    driveClient = driveService
                    driveFolderId = folderId
                    isDriveMode = true

                    withContext(Dispatchers.Main) {
                        selectedDCIMFolderName = "Google Drive"
//                        // DO NOT clear path; use isDriveMode for logic separation
                         path = ""
                        binding.folder.text = "Drive: camfolder"
                        binding.chooseFolder.visibility = View.VISIBLE
                        binding.pathUi.visibility = View.GONE
                        binding.blurView.visibility = View.GONE

                        setUiEnabled(true)
                        startPreview()
                        saveFolder("Drive: camfolder")

                        Toast.makeText(this@MainActivity, "Drive folder selected", Toast.LENGTH_SHORT).show()
                        loadPreview()
                    }

                } catch (e: Exception) {
                    Log.e("DriveSetup", "Failed to set up Drive folder", e)
                    withContext(Dispatchers.Main) {
                        setUiEnabled(true)
                        Toast.makeText(this@MainActivity, "Drive folder setup failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }

        } catch (e: ApiException) {
            Log.e("DriveUpload", "Sign-in failed: ${e.statusCode} ${e.message}")
            Toast.makeText(this, "Sign-in failed", Toast.LENGTH_SHORT).show()
            setUiEnabled(true)
        }
    }

    private fun setUiEnabled(enabled: Boolean) {
        binding.photoClick.isEnabled = enabled
        binding.videoClick.isEnabled = enabled
        binding.capture.isEnabled = enabled
        binding.cameraSwitch.isEnabled = enabled
        binding.flash.isEnabled = enabled
    }

    private fun switchModeUI() {
        if (flash == 2) {
            flash = 0
            binding.flash.performClick()
        }

        binding.capture.setImageResource(if (isCapture) R.drawable.camera_capture else R.drawable.video_capture)

        // Highlight active text
        binding.photoClick.setTextColor(
            ContextCompat.getColor(
                this,
                if (isCapture) R.color.camera_folder_color else R.color.white
            )
        )
        binding.videoClick.setTextColor(
            ContextCompat.getColor(
                this,
                if (isCapture) R.color.white else R.color.camera_folder_color
            )
        )

        closeCamera()
        startCamera()
    }

    private fun mapTouchToPreviewCoordinates(x: Float, y: Float): Pair<Float, Float> {
        val matrix = Matrix()
        binding.previewView.getTransform(matrix)

        val inverseMatrix = Matrix()
        matrix.invert(inverseMatrix)

        val mapped = floatArrayOf(x, y)
        inverseMatrix.mapPoints(mapped)

        return Pair(mapped[0], mapped[1])
    }

    // Improved getFocusMeteringRect function for more accurate focus
    private fun getFocusMeteringRect(x: Float, y: Float): MeteringRectangle? {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val sensorArraySize =
            characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return null

        // Transform the touch coordinates to match the sensor orientation and preview transformation
        val previewRect =
            RectF(0f, 0f, binding.previewView.width.toFloat(), binding.previewView.height.toFloat())
        val matrix = Matrix()
        binding.previewView.getTransform(matrix)

        // Create an inverse matrix to map from view to sensor coordinates
        val inverseMatrix = Matrix()
        matrix.invert(inverseMatrix)

        // Map the touch point from view to preview coordinates
        val points = floatArrayOf(x, y)
        inverseMatrix.mapPoints(points)

        val normalizedX = points[0] / previewRect.width()
        val normalizedY = points[1] / previewRect.height()

        val isFrontFacing =
            characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

        val correctedX: Float
        val correctedY: Float

        when (sensorOrientation) {
            90, 270 -> {
                correctedX = normalizedY
                correctedY = if (sensorOrientation == 90) 1f - normalizedX else normalizedX
            }

            180 -> {
                correctedX = 1f - normalizedX
                correctedY = 1f - normalizedY
            }

            else -> {
                correctedX = normalizedX
                correctedY = normalizedY
            }
        }

        // Mirror for front camera if needed
        val finalX = if (isFrontFacing && (sensorOrientation == 90 || sensorOrientation == 270))
            1f - correctedX else correctedX

        // Calculate center in sensor coordinates
        val centerX = (finalX * sensorArraySize.width()).toInt()
        val centerY = (correctedY * sensorArraySize.height()).toInt()

        // Focus area size (adjust as needed for your use case)
        val focusAreaSize = 200

        // Create metering rectangle, ensuring it stays within bounds
        val left = (centerX - focusAreaSize / 2).coerceIn(0, sensorArraySize.width() - 1)
        val top = (centerY - focusAreaSize / 2).coerceIn(0, sensorArraySize.height() - 1)
        val right = (left + focusAreaSize).coerceIn(0, sensorArraySize.width() - 1)
        val bottom = (top + focusAreaSize).coerceIn(0, sensorArraySize.height() - 1)

        // Return MeteringRectangle
        return MeteringRectangle(
            Rect(left, top, right, bottom),
            MeteringRectangle.METERING_WEIGHT_MAX
        )
    }

    private fun turnFlashOn(context: Context) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            cameraId?.let { cameraManager.setTorchMode(it, true) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun turnFlashOff(context: Context) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            cameraId?.let { cameraManager.setTorchMode(it, false) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun renderFolders() {

        val folders = getFolder()

        for (x in folders) {
            val view = binding.foldersParent.findViewWithTag<View>("pro_$x")
            binding.foldersParent.removeView(view)
        }

        for (x in folders) {
            if (x == "Drive: camfolder") continue
            val text = TextView(this)
            val lp = LinearLayout.LayoutParams(-1, toPx(47).toInt())
            lp.setMargins(toPx(20).toInt(), toPx(8).toInt(), toPx(20).toInt(), toPx(8).toInt())
            text.text = x
            text.tag = "pro_$x"
            text.layoutParams = lp
            text.textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            text.gravity = Gravity.CENTER
            text.typeface = ResourcesCompat.getFont(this, R.font.inter_semi_bold)
            text.setOnClickListener { onFolderSelect(x) }
            text.setTextColor(ContextCompat.getColor(this, R.color.camera_folder_color))
            text.setBackgroundResource(R.drawable.camera_box_background)
            text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            val count = binding.foldersParent.childCount
            binding.foldersParent.addView(text, count)
        }

        if (folders.count() > 2) {
            val lp = binding.scrollView.layoutParams
            lp.height = toPx(295).toInt()
            binding.scrollView.layoutParams = lp
        }
    }

    private fun Context.toPx(dp: Int): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp.toFloat(),
        resources.displayMetrics
    )

    private fun onFolderSelect(folder: String) {
        if (folder.startsWith("Drive:")) {
            isDriveMode = true

            if (driveFolderId == null) {
                // 🚨 Drive not set up yet, prompt login again
                Toast.makeText(this, "Please sign in to Drive to use this folder", Toast.LENGTH_SHORT).show()
                requestSignIn()
                return
            }

            // ✅ Setup Drive mode
            selectedDCIMFolderName = folder
            binding.folder.text = folder
            binding.chooseFolder.visibility = View.VISIBLE
            binding.pathUi.visibility = View.GONE
            binding.blurView.visibility = View.GONE

            setUiEnabled(true)
            startPreview()
            loadPreview() // ✅ Ensure preview loads from internal storage (/videos)
            return
        }

        // ✅ Local folder selection logic
        isDriveMode = false
        path = if (folder == "DCIM/Camera") {
            "storage/emulated/0/$folder/"
        } else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).path + "/$folder/"
        }

        if (!File(path).exists()) {
            Log.i("423u5", "path created ${File(path).mkdirs()}")
        }

        // Hide keyboard if visible
        currentFocus?.let {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }

        binding.folder.text = if (folder == "DCIM/Camera") selectedDCIMFolderName else folder
        binding.chooseFolder.visibility = View.VISIBLE
        binding.pathUi.visibility = View.GONE
        binding.blurView.visibility = View.GONE

        setUiEnabled(true)
        loadPreview()
    }

    private fun getFolder(): List<String> {
        val pref = getSharedPreferences(packageName, Context.MODE_PRIVATE)
        val folders = pref.getStringSet("folders", setOf()) ?: setOf()
        return folders.toList().sortedBy { x -> x.lowercase() }
    }

    private fun saveFolder(name: String) {
        if (name.isBlank()) {
            return
        }
        val pref = getSharedPreferences(packageName, Context.MODE_PRIVATE)
        val folders = pref.getStringSet("folders", setOf()) ?: setOf()
        val data = hashSetOf<String>()
        data.add(name)
        data.addAll(folders)
        pref.edit { putStringSet("folders", data) }

        if (name.startsWith("Drive:")) {
            // Save flag to SharedPreferences so we know it's Drive next time
            getSharedPreferences(packageName, Context.MODE_PRIVATE).edit {
                putBoolean("has_drive_folder", true)
            }
        }
        else{
            onFolderSelect(name)

        }
    }

    private fun saveDateFolder(name: String) {
        val pref = getSharedPreferences(packageName, Context.MODE_PRIVATE)
        val folders = pref.getStringSet("date_folders", setOf()) ?: setOf()
        val data = hashSetOf<String>()
        data.add(name)
        data.addAll(folders)
        pref.edit { putStringSet("date_folders", data) }
    }

    private fun loadPreview() {
        Log.i("423u5", "> ${System.currentTimeMillis()}")
        Thread {
            val previewDir = if (isDriveMode) {
                File(getExternalFilesDir("videos") ?: filesDir, "")
            } else {
                File(path)
            }

            val files = previewDir.listFiles()
                ?.filter {
                    it.extension.lowercase() in listOf("jpg", "jpeg", "mp4") &&
                            (it.length() > 100 * 1024 || !it.name.endsWith(".mp4")) // Skip small/corrupt videos
                }
                ?.sortedByDescending { it.lastModified() }

            val file = files?.firstOrNull()

            Handler(Looper.getMainLooper()).post {
                if (file != null && file.exists()) {
                    lastPath = file.path
                    binding.mediaPreview.visibility = View.VISIBLE
                    Glide.with(this@MainActivity).load(file.path).into(binding.media)
                } else {
                    binding.mediaPreview.visibility = View.INVISIBLE
                }
            }
            Log.i("423u5", "> ${System.currentTimeMillis()}")
        }.start()
    }

    @SuppressLint("MissingPermission")
    private fun recordVideo() {
        if (isRecording) {
            stopRecording()
            return
        }

        hideControllers()

        val videoFileName = if (isFrontCamera) {
            "FRONT_VID_${System.currentTimeMillis()}.mp4"
        } else {
            "BACK_VID_${System.currentTimeMillis()}.mp4"
        }

        val storageDir = if (isDriveMode) {
            File(getExternalFilesDir(null), "videos")
        } else {
            File(path)
        }
        if (!storageDir.exists()) storageDir.mkdirs()

        videoFile = File(storageDir, videoFileName)

        try {
            // 1. Setup MediaRecorder with lower quality for compatibility
            setupMediaRecorder()
            val recordSurface = mediaRecorder.surface

            // 2. Create capture request
            previewRequestBuilder =
                cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                    addTarget(previewSurface)
                    addTarget(recordSurface)

                    // Flash handling
                    if (flash == 1 && !isFrontCamera) {
                        set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                    }

                    // Focus mode for video
                    set(
                        CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                    )
                }

            // 3. Create capture session
            cameraDevice!!.createCaptureSession(
                listOf(previewSurface, recordSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            session.setRepeatingRequest(previewRequestBuilder.build(), null, null)
                            mediaRecorder.start()
                            isRecording = true
                            hasRecordingStarted = true
                            binding.capture.setImageResource(R.drawable.video_stop)

                            // Start timer
                            val startTime = System.currentTimeMillis()
                            binding.duration.visibility = View.VISIBLE
                            binding.duration.post(object : Runnable {
                                override fun run() {
                                    if (isRecording) {
                                        val seconds =
                                            (System.currentTimeMillis() - startTime) / 1000
                                        val minute = (seconds / 60).toString().padStart(2, '0')
                                        val second = (seconds % 60).toString().padStart(2, '0')
                                        binding.duration.text = "$minute:$second"
                                        binding.duration.postDelayed(this, 1000)
                                    }
                                }
                            })
                        } catch (e: Exception) {
                            Log.e("CameraDemo", "Failed to start recording", e)
                            Toast.makeText(
                                this@MainActivity,
                                "Failed to start recording",
                                Toast.LENGTH_SHORT
                            ).show()
                            stopRecording()
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Toast.makeText(
                            this@MainActivity,
                            "Recording session failed",
                            Toast.LENGTH_SHORT
                        ).show()
                        cleanupResources()
                    }
                },
                null
            )
        } catch (e: Exception) {
            Log.e("CameraDemo", "Error starting recording", e)
            Toast.makeText(this, "Error starting recording", Toast.LENGTH_SHORT).show()
            cleanupResources()
        }
    }

    private fun setupMediaRecorder() {
        mediaRecorder = MediaRecorder().apply {
            // 1. Set lower quality for better compatibility
            setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(videoFile!!.absolutePath)

            // Reduced quality settings
            setVideoEncodingBitRate(5_000_000) // Lower bitrate
            setVideoFrameRate(24) // Standard frame rate
            setVideoSize(1280, 720) // HD resolution instead of full preview size

            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)

            // Orientation handling
            if (!isFrontCamera) {
                setOrientationHint(90)
            } else {
                setOrientationHint(270)
            }

            try {
                prepare()
            } catch (e: Exception) {
                Log.e("CameraDemo", "MediaRecorder prepare failed", e)
                throw e
            }
        }
    }

    private fun stopRecording() {
        try {
            if (isRecording && hasRecordingStarted) {
                try {
                    mediaRecorder.stop()
                    Toast.makeText(this, "Video saved successfully!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("CameraDemo", "Error stopping recording", e)
//                    Toast.makeText(this, "Error saving video", Toast.LENGTH_SHORT).show()
                }
            }
        } finally {
            cleanupResources()
            isRecording = false
            hasRecordingStarted = false // ✅ Reset the flag

            visibleControllers()
            binding.duration.visibility = View.GONE
            binding.capture.setImageResource(R.drawable.video_capture)

            // Only show success if we actually have a valid file
            videoFile?.let {
                if (it.exists() && it.length() > 0) {
                    updateMediaThumbnail(it)
                    invalidateMedia(it.absolutePath)

                    // ✅ Added: Upload to Google Drive if enabled
                    if (isDriveMode && driveClient != null && driveFolderId != null) {
                        val fileMetadata = com.google.api.services.drive.model.File().apply {
                            name = it.name
                            parents = listOf(driveFolderId!!)
                        }

                        val mediaContent = FileContent("video/mp4", it)
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                driveClient!!.files().create(fileMetadata, mediaContent)
                                    .setFields("id")
                                    .execute()
                                Log.d("DriveUpload", "Video uploaded to Drive: ${it.name}")
                            } catch (e: Exception) {
                                Log.e("DriveUpload", "Video upload to Drive failed", e)
                            }
                        }
                    }
                }
            }

            startPreview()
            turnFlashOff(this)
        }
    }

    private fun capturePhoto() {
        if (path.isBlank() && !isDriveMode) {
            Toast.makeText(this, "Please select a folder first", Toast.LENGTH_SHORT).show()
            return
        }

        if (!::previewSize.isInitialized) {
            Toast.makeText(this, "Camera not ready yet", Toast.LENGTH_SHORT).show()
            return
        }

        if (cameraDevice == null) {
            Toast.makeText(this, "Camera not initialized", Toast.LENGTH_SHORT).show()
            return
        }

        val file = if (isDriveMode) {
            val storageDir = File(getExternalFilesDir(null), "videos")
            if (!storageDir.exists()) storageDir.mkdirs()
            File(storageDir, "IMG_${System.currentTimeMillis()}.jpg")
        } else {
            File(path, "IMG_${System.currentTimeMillis()}.jpg")
        }

        lastPath = file.absolutePath

        imageReader =
            ImageReader.newInstance(previewSize.width, previewSize.height, ImageFormat.JPEG, 1)
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener

            lifecycleScope.launch(Dispatchers.IO) {
                image.use {
                    val buffer = it.planes[0].buffer
                    val rawBytes = ByteArray(buffer.remaining())
                    buffer.get(rawBytes)

//                    // ✅ Save raw image immediately for fast capture experience
//                    FileOutputStream(file).use { out ->
//                        out.write(rawBytes)
//                    }

                    file.parentFile?.mkdirs()

                    FileOutputStream(file).use { out ->
                        out.write(rawBytes)
                    }

                    // ✅ Update UI immediately after saving
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity,
                            "Photo saved successfully!",
                            Toast.LENGTH_SHORT
                        ).show()

                        binding.capture.isEnabled = true
                        binding.progressBar.visibility = View.GONE
                        binding.capture.visibility = View.VISIBLE
                    }

                    try {
                        val originalBitmap =
                            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)

                        val matrix = Matrix()
                        if (isFrontCamera) {
                            matrix.postRotate(270f)
                            matrix.postScale(
                                -1f,
                                1f,
                                originalBitmap.width / 2f,
                                originalBitmap.height / 2f
                            )
                        } else {
                            matrix.postRotate(90f)
                        }

                        val rotatedBitmap = Bitmap.createBitmap(
                            originalBitmap, 0, 0,
                            originalBitmap.width, originalBitmap.height,
                            matrix, true
                        )

                        val previewWidth = binding.previewView.width.toFloat()
                        val previewHeight = binding.previewView.height.toFloat()
                        val previewAspectRatio = previewWidth / previewHeight

                        val bitmapWidth = rotatedBitmap.width
                        val bitmapHeight = rotatedBitmap.height
                        val bitmapAspectRatio = bitmapWidth.toFloat() / bitmapHeight

                        val croppedBitmap = if (bitmapAspectRatio > previewAspectRatio) {
                            val targetWidth = (bitmapHeight * previewAspectRatio).toInt()
                            val xOffset = (bitmapWidth - targetWidth) / 2
                            Bitmap.createBitmap(
                                rotatedBitmap,
                                xOffset,
                                0,
                                targetWidth,
                                bitmapHeight
                            )
                        } else {
                            val targetHeight = (bitmapWidth / previewAspectRatio).toInt()
                            val yOffset = (bitmapHeight - targetHeight) / 2
                            Bitmap.createBitmap(
                                rotatedBitmap,
                                0,
                                yOffset,
                                bitmapWidth,
                                targetHeight
                            )
                        }

                        FileOutputStream(file).use { out ->
                            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                        }

                        withContext(Dispatchers.Main) {
                            updateMediaThumbnail(file)
                            invalidateMedia(file.path)
                            Glide.with(this@MainActivity).load(Uri.fromFile(file))
                                .into(binding.media)
                            turnFlashOff(this@MainActivity)
                        }

                        if (isDriveMode && driveClient != null && driveFolderId != null) {
                            val fileMetadata = com.google.api.services.drive.model.File().apply {
                                name = file.name
                                parents = listOf(driveFolderId!!)
                            }

                            val mediaContent = FileContent("image/jpeg", file)
                            driveClient!!.files().create(fileMetadata, mediaContent)
                                .setFields("id")
                                .execute()
                            Log.d("DriveUpload", "Photo uploaded to Drive: ${file.name}")
                        }

                    } catch (e: Exception) {
                        Log.e("ImageProcessing", "Error processing image", e)
                    }
                }
            }
        }, null)

        val texture = binding.previewView.surfaceTexture!!
        texture.setDefaultBufferSize(previewSize.width, previewSize.height)
        previewSurface = Surface(texture)

        previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        previewRequestBuilder.addTarget(previewSurface)

        if (flash == 1 && !isFrontCamera) {
            previewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
        }

        cameraDevice!!.createCaptureSession(
            listOf(previewSurface, imageReader.surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    session.setRepeatingRequest(previewRequestBuilder.build(), null, null)

                    Handler(Looper.getMainLooper()).postDelayed({
                        if (flash == 1 && isFrontCamera) {
                            binding.frontFlashOverlay.visibility = View.GONE
                        }
                        takePictureNow()
                    }, if (flash == 1) 500 else 0)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Toast.makeText(
                        this@MainActivity,
                        "Camera session config failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            null
        )

        if (flash == 1 && isFrontCamera) {
            binding.frontFlashOverlay.visibility = View.VISIBLE
        }
    }


    private fun takePictureNow() {
        try {
            val captureRequestBuilder =
                cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureRequestBuilder.addTarget(imageReader.surface)

            val currentCropRegion = previewRequestBuilder.get(CaptureRequest.SCALER_CROP_REGION)
            currentCropRegion?.let {
                captureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, it)
            }

            if (flash == 1 && !isFrontCamera) {
                captureRequestBuilder.set(
                    CaptureRequest.FLASH_MODE,
                    CaptureRequest.FLASH_MODE_SINGLE
                )
            }

            captureSession?.capture(
                captureRequestBuilder.build(),
                object : CameraCaptureSession.CaptureCallback() {},
                null
            )
        } catch (e: Exception) {
            Log.e("CameraCapture", "Error capturing photo", e)
            Toast.makeText(this, "Failed to capture photo", Toast.LENGTH_SHORT).show()
        }
    }


    @RequiresPermission(Manifest.permission.CAMERA)
    @SuppressLint("RestrictedApi")
    private fun startCamera() {
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        for (id in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if ((isFrontCamera && facing == CameraCharacteristics.LENS_FACING_FRONT) ||
                (!isFrontCamera && facing == CameraCharacteristics.LENS_FACING_BACK)
            ) {
                cameraId = id
                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                previewSize = map?.getOutputSizes(SurfaceTexture::class.java)
                    ?.maxByOrNull { it.height * it.width }!!
                Log.d(
                    "CameraPreview",
                    "Using preview size: ${previewSize.width}x${previewSize.height}"
                )

                openCamera(cameraId)
                Log.i("TAG", "startCamera: " + cameraId)
                break
            }
        }
    }

    private fun cleanupResources() {
        try {
            mediaRecorder.reset()
        } catch (e: Exception) {
            Log.e("CameraDemo", "Error resetting media recorder", e)
        }

        try {
            mediaRecorder.release()
        } catch (e: Exception) {
            Log.e("CameraDemo", "Error releasing media recorder", e)
        }

        captureSession?.close()
        captureSession = null
        hasRecordingStarted = false
    }

    private fun isAppInForeground(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false
        val packageName = applicationContext.packageName

        for (appProcess in appProcesses) {
            if (appProcess.processName == packageName) {
                return appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            }
        }
        return false
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    private fun openCamera(cameraId: String) {
        if (!isAppInForeground()) {
            Log.w("Camera", "App is not in foreground, skipping openCamera")
            return
        }

        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    cameraDevice = device
                    startPreview()
                }

                override fun onDisconnected(device: CameraDevice) {
                    device.close()
                }

                override fun onError(device: CameraDevice, error: Int) {
                    device.close()
                }
            }, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            Toast.makeText(this, "Camera error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }


    private fun closeCamera() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
    }

    private fun updateMediaThumbnail(file: File) {
        lastCapturedPath = file.absolutePath
        lastPath = file.absolutePath
        // Make media preview button visible
        binding.mediaPreview.visibility = View.VISIBLE

        // Load thumbnail using Glide
        // Load thumbnail using Glide and apply mirror effect if front camera
        Glide.with(this)
            .load(file)
            .centerCrop()
            .into(binding.media)
        binding.media.scaleX = if (isFrontCamera && file.name.endsWith(".mp4", true)) -1f else 1f

    }

    private fun startPreview() {
        val texture = binding.previewView.surfaceTexture
        if (texture == null) {
            Log.e("CameraPreview", "startPreview: surfaceTexture is null, waiting...")
            // Set listener to retry when available
            binding.previewView.surfaceTextureListener =
                object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        Log.i(
                            "CameraPreview",
                            "SurfaceTexture is now available, retrying preview setup"
                        )
                        startPreview() // retry now that it's ready
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                    }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                }
            return
        }

        texture.setDefaultBufferSize(previewSize.width, previewSize.height)
        previewSurface = Surface(texture)

        configureTransform(binding.previewView.width, binding.previewView.height)

        if (!isCapture) {
            setupVideoPreviewSession()
        } else {
            setupPreviewSession()
        }
    }

    private fun setupVideoPreviewSession() {
        try {
            val texture = binding.previewView.surfaceTexture!!
            texture.setDefaultBufferSize(previewSize.width, previewSize.height)
            previewSurface = Surface(texture)

            prepareMediaRecorderPreviewOnly()
            val recordSurface = mediaRecorder.surface

            previewRequestBuilder =
                cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                    addTarget(previewSurface)
                    addTarget(recordSurface)
                    set(
                        CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                    )
                }

            cameraDevice!!.createCaptureSession(
                listOf(previewSurface, recordSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            session.setRepeatingRequest(previewRequestBuilder.build(), null, null)
                        } catch (e: Exception) {
                            Log.e("CameraDemo", "Failed to start video preview", e)
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Toast.makeText(
                            this@MainActivity,
                            "Video preview setup failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                null
            )
        } catch (e: Exception) {
            Log.e("CameraDemo", "Error setting up video preview", e)
        }
    }

    private fun prepareMediaRecorderPreviewOnly() {
        if (::mediaRecorder.isInitialized) {
            mediaRecorder.release()
        }

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

            // Use a dummy output that is immediately discarded
            val dummyPath = File(cacheDir, "dummy_preview.mp4").absolutePath
            setOutputFile(dummyPath)

            setVideoEncodingBitRate(5_000_000)
            setVideoFrameRate(24)
            setVideoSize(1280, 720)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)
            setOrientationHint(if (!isFrontCamera) 90 else 270)

            try {
                prepare()
            } catch (e: Exception) {
                Log.e("CameraDemo", "MediaRecorder preview prepare failed", e)
            }
        }
    }

    private fun setupPreviewSession() {
        previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        previewRequestBuilder.addTarget(previewSurface)

        cameraDevice!!.createCaptureSession(
            listOf(previewSurface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    session.setRepeatingRequest(previewRequestBuilder.build(), null, null)

                    // ✅ Reapply transformation to match orientation/aspect
                    configureTransform(binding.previewView.width, binding.previewView.height)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Toast.makeText(this@MainActivity, "Preview failed", Toast.LENGTH_SHORT).show()
                }
            },
            null
        )
    }


    private fun visibleControllers() {
        binding.chooseFolder.visibility = View.VISIBLE
        binding.duration.visibility = View.GONE
        binding.flash.visibility = View.VISIBLE
        binding.mediaPreview.visibility = View.VISIBLE
        binding.photoClick.visibility = View.VISIBLE
        binding.videoClick.visibility = View.VISIBLE
        binding.cameraSwitch.visibility = View.VISIBLE
    }

    private fun hideControllers() {
        binding.chooseFolder.visibility = View.GONE
        binding.flash.visibility = View.INVISIBLE
        binding.folder.visibility = View.GONE
        binding.mediaPreview.visibility = View.GONE
        binding.photoClick.visibility = View.GONE
        binding.videoClick.visibility = View.GONE
        binding.cameraSwitch.visibility = View.GONE
    }

    private fun invalidateMedia(path: String) {
        lastPath = path
        binding.folder.visibility = View.VISIBLE
        Glide.with(this).load(path).into(binding.media)
        MediaScannerConnection.scanFile(this, arrayOf(path), null, null)
    }

    @SuppressLint("SetTextI18n")
    override fun onResume() {
        super.onResume()
        Log.i("TAG", "camera: " + "onresumecamera")

        if (binding.folder.text.isNotBlank()) {
            binding.toast.text = "Selected folder: ${binding.folder.text}"
            binding.toast.visibility = View.VISIBLE
            binding.toast.postDelayed({ binding.toast.visibility = View.GONE }, 2000)
        }

        if (cameraDevice != null) {
            adjustZoom()
        }

        // ✅ Ensure TextureView is ready before loading preview
        if (binding.previewView.isAvailable) {
            loadPreview()
        } else {
            binding.previewView.surfaceTextureListener =
                object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        loadPreview()
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                    }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                }
        }

        if (wasInBackground) {
            binding.pathUi.visibility = View.GONE
            binding.blurView.visibility = View.GONE
            wasInBackground = false
        } else {
            binding.pathUi.visibility = View.VISIBLE
            binding.blurView.visibility = View.VISIBLE
        }
    }


    override fun onPause() {
        if (isRecording) {
            stopRecording()
        }
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        wasInBackground = true
        Log.i("TAG", "camera: " + "oncStopcamera")
    }

    override fun onDestroy() {
        if (::mediaRecorder.isInitialized) {
            mediaRecorder.release()
        }
        cameraDevice?.close()
        captureSession?.close()
        super.onDestroy()
    }

    private fun adjustZoom(scaleFactor: Float = 1.0f) {
        if (cameraDevice == null || captureSession == null || !::previewRequestBuilder.isInitialized || !::cameraManager.isInitialized || cameraId.isEmpty()) return

        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val rect =
            characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return
        val maxZoom =
            characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: return

        // Calculate new zoom
        zoom *= scaleFactor
        val newZoom = zoom.coerceIn(1f, maxZoom)
        zoom = newZoom

        val ratio = 1f / newZoom
        val croppedWidth = (rect.width() * ratio).toInt()
        val croppedHeight = (rect.height() * ratio).toInt()
        val left = (rect.width() - croppedWidth) / 2
        val top = (rect.height() - croppedHeight) / 2
        val zoomRect = Rect(left, top, left + croppedWidth, top + croppedHeight)

        // Apply zoom crop region
        previewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoomRect)
        try {
            captureSession?.setRepeatingRequest(previewRequestBuilder.build(), null, null)
        } catch (e: IllegalStateException) {
            Log.e("CameraZoom", "Skipping zoom because camera is closed", e)
        }
    }


    private fun isPermissionGranted(): Boolean {
        for (x in requiredPermission) {
            if (checkSelfPermission(x) != PackageManager.PERMISSION_GRANTED)
                return false
        }
        return true
    }

    private fun requestPermission() {
        permissionResultLauncher.launch(requiredPermission.toTypedArray())
    }

    private val permissionResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            for (permission in permissions) {
                if (!permission.value) {
                    Toast.makeText(this, "Permission not granted", Toast.LENGTH_SHORT).show()
                    return@registerForActivityResult
                }
            }
            startCamera()
        }

    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val matrix = Matrix()
        val rotation = windowManager.defaultDisplay.rotation
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(
            0f,
            0f,
            previewSize.height.toFloat(),
            previewSize.width.toFloat()
        ) // camera output

        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())

        // ✅ Fix distortion: Center-crop based scaling
        val scale = maxOf(
            viewHeight.toFloat() / previewSize.width,
            viewWidth.toFloat() / previewSize.height
        )
        matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
        matrix.postScale(scale, scale, centerX, centerY)

        // ✅ Handle rotation
        val rotationDegrees = when (rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)

        binding.previewView.setTransform(matrix)
    }


}