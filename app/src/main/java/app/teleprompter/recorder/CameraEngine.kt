package app.teleprompter.recorder

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Camera preview and recording. Takes go straight into a gallery file, so length is limited by storage, not memory. */
class CameraEngine(private val context: Context, private val owner: LifecycleOwner) {
    val previewView = PreviewView(context).apply {
        scaleType = PreviewView.ScaleType.FIT_CENTER
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    }

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    fun bind(front: Boolean, quality: Int, loudVoice: Boolean, onError: (String) -> Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val target = when (quality) {
                720 -> Quality.HD
                2160 -> Quality.UHD
                else -> Quality.FHD
            }
            val selector = QualitySelector.from(target, FallbackStrategy.lowerQualityOrHigherThan(target))
            // The CameraX default, CAMCORDER, has no automatic gain, so speech comes out quiet.
            val audioSource = if (loudVoice) {
                MediaRecorder.AudioSource.VOICE_COMMUNICATION
            } else {
                MediaRecorder.AudioSource.CAMCORDER
            }
            val recorder = Recorder.Builder().setQualitySelector(selector).setAudioSource(audioSource).build()
            val capture = VideoCapture.withOutput(recorder)
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val camera = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            try {
                provider.unbindAll()
                provider.bindToLifecycle(owner, camera, preview, capture)
                videoCapture = capture
            } catch (e: Exception) {
                onError("Не удалось включить камеру. Текст суфлёра работает и без неё.")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun unbind() {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            future.get().unbindAll()
            videoCapture = null
        }, ContextCompat.getMainExecutor(context))
    }

    @SuppressLint("MissingPermission")
    fun start(
        onProgress: (seconds: Long) -> Unit,
        onFinished: (uri: Uri?, interrupted: Boolean) -> Unit,
    ): Boolean {
        val capture = videoCapture ?: return false
        previewView.display?.let { capture.targetRotation = it.rotation }

        val stamp = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "teleprompter-$stamp")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/Teleprompter")
            }
        }
        val output = MediaStoreOutputOptions
            .Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(values)
            .build()

        val pending = capture.output.prepareRecording(context, output)
        val mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
        if (mic == PackageManager.PERMISSION_GRANTED) pending.withAudioEnabled()

        recording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Status ->
                    onProgress(event.recordingStats.recordedDurationNanos / 1_000_000_000)
                is VideoRecordEvent.Finalize -> {
                    recording = null
                    val uri = event.outputResults.outputUri.takeIf { it != Uri.EMPTY }
                    onFinished(uri, event.hasError())
                }
            }
        }
        return true
    }

    fun stop() {
        recording?.stop()
    }
}
