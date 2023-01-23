package com.hiddenramblings.tagmo.amiibo

import android.content.ContentResolver
import android.content.Context
import android.content.res.Resources
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.annotation.RequiresApi
import com.hiddenramblings.tagmo.BuildConfig
import com.hiddenramblings.tagmo.Preferences
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.TagMo
import com.hiddenramblings.tagmo.eightbit.io.Debug
import java.util.*

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class AmiiboDocument(context: Context) {
    private val files = ArrayList<Uri>()
    private val resources: Resources
    private val contentResolver: ContentResolver

    init {
        resources = context.resources
        contentResolver = context.contentResolver
    }

    fun listFiles(uri: Uri, recursiveFiles: Boolean): ArrayList<Uri> {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        if (BuildConfig.DEBUG) {
            val docUri = DocumentsContract.buildDocumentUriUsingTree(uri, docId)
            val items = ArrayList(listOf(*resources.getStringArray(R.array.mimetype_bin)))
            items.add(DocumentsContract.Document.MIME_TYPE_DIR)
            val selectionArgs = items.toTypedArray()
            val docCursor = contentResolver.query(
                docUri, arrayOf(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                DocumentsContract.Document.COLUMN_MIME_TYPE, selectionArgs, null
            )
            try {
                while (docCursor!!.moveToNext()) {
                    val displayName = docCursor.getString(0)
                    val mimeType = docCursor.getString(1)
                    Debug.verbose(
                        this.javaClass, "Primary doc=$displayName, mime=$mimeType"
                    )
                }
            } finally {
                closeQuietly(docCursor)
            }
        }
        val queue: Queue<String> = ArrayDeque()
        queue.add(docId)
        val fileCount = MutableInteger(1)
        while (queue.size > 0) {
            val currentDocId = queue.remove()
            listFiles(uri, currentDocId, queue, fileCount, recursiveFiles)
        }
        return files
    }

    private fun listFiles(
        rootUri: Uri, documentId: String, queue: Queue<String>,
        fileCount: MutableInteger, recursiveFiles: Boolean
    ) {
        val binFiles = listOf(*resources.getStringArray(R.array.mimetype_bin))
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, documentId)
        val cursor: Cursor = try {
            val items = ArrayList(binFiles)
            items.add(DocumentsContract.Document.MIME_TYPE_DIR)
            val selectionArgs = items.toTypedArray()
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
        try {
            while (cursor.moveToNext()) {
                fileCount.increment()
                val displayName = cursor.getString(0)
                val mimeType = cursor.getString(1)
                val childDocumentId = cursor.getString(2)
                if (DocumentsContract.Document.MIME_TYPE_DIR == mimeType) {
                    Debug.verbose(
                        this.javaClass, "Child doc=$displayName, parent=$documentId, mime=$mimeType"
                    )
                    if (recursiveFiles) queue.add(childDocumentId)
                } else if (binFiles.contains(mimeType)) {
                    files.add(DocumentsContract.buildDocumentUriUsingTree(rootUri, childDocumentId))
                }
            }
        } finally {
            closeQuietly(cursor)
        }
    }

    private fun closeQuietly(closeable: AutoCloseable?) {
        try {
            closeable?.close()
        } catch (runtime: RuntimeException) {
            throw runtime
        } catch (ignored: Exception) { }
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