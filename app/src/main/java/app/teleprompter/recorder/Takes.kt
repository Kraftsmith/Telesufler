package app.teleprompter.recorder

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import java.io.IOException

data class Take(val uri: Uri, val name: String, val durationMs: Long, val addedAtMs: Long)

/** Lists the videos this app recorded, newest first. Files are named by [CameraEngine]. */
fun queryTakes(context: Context): List<Take> {
    val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.DATE_ADDED,
    )
    val takes = mutableListOf<Take>()
    context.contentResolver.query(
        collection,
        projection,
        "${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?",
        arrayOf("teleprompter-%"),
        "${MediaStore.Video.Media.DATE_ADDED} DESC",
    )?.use { c ->
        while (c.moveToNext()) {
            takes += Take(ContentUris.withAppendedId(collection, c.getLong(0)), c.getString(1), c.getLong(2), c.getLong(3) * 1000)
        }
    }
    return takes
}

fun loadThumbnail(context: Context, take: Take): Bitmap? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.contentResolver.loadThumbnail(take.uri, Size(320, 320), null)
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Video.Thumbnails.getThumbnail(
            context.contentResolver,
            ContentUris.parseId(take.uri),
            MediaStore.Video.Thumbnails.MINI_KIND,
            null,
        )
    }
} catch (_: IOException) {
    null
}
