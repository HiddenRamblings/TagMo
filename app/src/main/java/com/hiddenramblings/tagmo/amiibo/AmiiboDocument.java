package com.hiddenramblings.tagmo.amiibo;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import com.hiddenramblings.tagmo.eightbit.io.Debug;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Queue;

@SuppressLint("NewApi")
public class AmiiboDocument {
    private final ArrayList<Uri> files = new ArrayList<>();
    private final ContentResolver contentResolver;

    public AmiiboDocument(Context context) {
        contentResolver = context.getContentResolver();
    }

    public ArrayList<Uri> listFiles(Uri uri, boolean recursiveFiles) {
        String docId = DocumentsContract.getTreeDocumentId(uri);
        Uri docUri = DocumentsContract.buildDocumentUriUsingTree(uri, docId);

        Cursor docCursor = contentResolver.query(docUri, new String[] {
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE },
                null, null, null);
        try {
            while (docCursor.moveToNext()) {
                Debug.Log(this.getClass(), "Primary doc=" + docCursor.getString(0)
                        + ", mime=" + docCursor.getString(1));
            }
        } finally {
            closeQuietly(docCursor);
        }

        Queue<String> queue = new ArrayDeque<>();
        queue.add(docId);

        MutableInteger fileCount = new MutableInteger(1);
        while (queue.size() > 0) {
            String currentDocId = queue.remove();
            listFiles(uri, currentDocId, queue, fileCount, recursiveFiles);
        }

        return files;
    }

    private void listFiles(
            Uri rootUri, String documentId, Queue<String> queue,
            MutableInteger fileCount, boolean recursiveFiles) {
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, documentId);

        Cursor cursor = contentResolver.query(childrenUri, new String[] {
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_DOCUMENT_ID },
                null, null, null);
        try {
            while (cursor.moveToNext()) {
                fileCount.increment();
                String displayName = cursor.getString(0);
                String mimeType = cursor.getString(1);
                Debug.Log(this.getClass(), "Child parent=" + documentId
                        + ", doc=" + displayName + ", mime=" + mimeType);

                String childDocumentId = cursor.getString(2);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType) && recursiveFiles) {
                    queue.add(childDocumentId);
                } else if (displayName.toLowerCase(Locale.ROOT).endsWith(".bin")) {
                    files.add(DocumentsContract.buildDocumentUriUsingTree(rootUri, childDocumentId));
                }
            }
        } finally {
            closeQuietly(cursor);
        }
    }

    public void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (RuntimeException rethrown) {
                throw rethrown;
            } catch (Exception ignored) { }
        }
    }

    public static class MutableInteger {
        private int value;

        public MutableInteger(int value) {
            this.value = value;
        }

        public void set(int value) {
            this.value = value;
        }

        public void increment() {
            this.value++;
        }

        public int intValue() {
            return value;
        }
    }
}
