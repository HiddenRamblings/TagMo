package androidmads.library.qrgenearator

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

@Suppress("UNUSED")
class QRGSaver {
    @JvmOverloads
    fun save(
        saveLocation: String, imageName: String, bitmap: Bitmap,
        imageFormat: CompressFormat = CompressFormat.PNG
    ): Boolean {
        var success = false
        val imageDetail = saveLocation + imageName + imgFormat(imageFormat)
        val outStream: FileOutputStream
        val file = File(saveLocation)
        if (!file.exists()) {
            file.mkdir()
        } else {
            Log.d("QRGSaver", "Folder Exists")
        }
        try {
            outStream = FileOutputStream(imageDetail)
            bitmap.compress(imageFormat, 100, outStream)
            outStream.flush()
            outStream.close()
            success = true
        } catch (e: IOException) {
            Log.d("QRGSaver", e.toString())
        }
        return success
    }

    private fun imgFormat(imageFormat: CompressFormat): String {
        return when {
            imageFormat == CompressFormat.PNG -> ".png"
            imageFormat.name.contains("WEBP") -> ".webp"
            else -> ".jpg"
        }
    }
}