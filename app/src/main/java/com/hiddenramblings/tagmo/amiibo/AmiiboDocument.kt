package com.hiddenramblings.tagmo.amiibo

import android.content.ContentResolver
import android.content.Context
import android.content.res.Resources
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.annotation.RequiresApi
import com.hiddenramblings.tagmo.Preferences
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.TagMo
import java.util.ArrayDeque
import java.util.Queue

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class AmiiboDocument(context: Context) {
    private val files = ArrayList<Uri>()
    private val resources: Resources
    private val contentResolver: ContentResolver

    init {
        resources = context.resources
        contentResolver = context.contentResolver
    }

    private fun listFiles(
        rootUri: Uri, documentId: String, queue: Queue<String>,
        fileCount: MutableInteger, recursiveFiles: Boolean
    ) {
        val binFiles = listOf(*resources.getStringArray(R.array.mimetype_bin))
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, documentId)
        val cursor: Cursor = try {
            val selectionArgs = ArrayList(binFiles).apply{
                add(DocumentsContract.Document.MIME_TYPE_DIR)
            }.toTypedArray()
            contentResolver.query(
                childrenUri, arrayOf(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID
                ),
                DocumentsContract.Document.COLUMN_MIME_TYPE, selectionArgs, null
            )
        } catch (ex: SecurityException) {
            try {
                contentResolver.query(
                    childrenUri, arrayOf(
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID
                    ),
                    null, null, null
                )
            } catch (sx: SecurityException) {
                Preferences(TagMo.appContext).browserRootDocument(null)
                return
            }
        } ?: return
        cursor.use {
            while (it.moveToNext()) {
                fileCount.increment()
                val mimeType = it.getString(1)
                val childDocumentId = it.getString(2)
                if (DocumentsContract.Document.MIME_TYPE_DIR == mimeType) {
                    if (recursiveFiles) queue.add(childDocumentId)
                } else if (binFiles.contains(mimeType)) {
                    files.add(DocumentsContract.buildDocumentUriUsingTree(rootUri, childDocumentId))
                }
            }
        }
    }

    fun listFiles(uri: Uri, recursiveFiles: Boolean): ArrayList<Uri> {
        val queue: Queue<String> = ArrayDeque<String>().apply {
            add(DocumentsContract.getTreeDocumentId(uri))
        }
        val fileCount = MutableInteger(1)
        while (queue.size > 0) {
            val currentDocId = queue.remove()
            listFiles(uri, currentDocId, queue, fileCount, recursiveFiles)
        }
        return files
    }

    class MutableInteger(private var value: Int) {
        fun get(): Int {
            return value
        }

        fun set(value: Int) {
            this.value = value
        }

        fun increment() {
            value++
        }
    }
}