package com.example.storetasks.data.local

import android.content.Context
import java.io.File

/** App-private photo files (filesDir/photos). Exposed to the camera via FileProvider. */
class PhotoStorage(private val context: Context) {

    fun newFile(taskId: String, kind: PhotoKind): File {
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        return File(dir, "${taskId}_${kind.name.lowercase()}_${System.currentTimeMillis()}.jpg")
    }

    fun delete(path: String) {
        File(path).delete()
    }
}
