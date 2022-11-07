package com.hiddenramblings.tagmo.amiibo;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;

import androidx.annotation.RequiresApi;

import com.hiddenramblings.tagmo.BuildConfig;
import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.eightbit.io.Debug;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class AmiiboDocument {
    private final ArrayList<Uri> files = new ArrayList<>();
    private final Resources resources;
    private final ContentResolver contentResolver;

    public AmiiboDocument(Context context) {
        resources = context.getResources();
        contentResolver = context.getContentResolver();
    }

    public ArrayList<Uri> listFiles(Uri uri, boolean recursiveFiles) throws SecurityException {
        String docId = DocumentsContract.getTreeDocumentId(uri);

        if (BuildConfig.DEBUG) {
            Uri docUri = DocumentsContract.buildDocumentUriUsingTree(uri, docId);
            ArrayList<String> items = new ArrayList<>(Arrays.asList(
                    resources.getStringArray(R.array.mimetype_bin)
            ));
            items.add(DocumentsContract.Document.MIME_TYPE_DIR);
            String[] selectionArgs = items.toArray(new String[0]);
            Cursor docCursor = contentResolver.query(docUri, new String[] {
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_MIME_TYPE },
                    DocumentsContract.Document.COLUMN_MIME_TYPE, selectionArgs, null);
            try {
                while (docCursor.moveToNext()) {
                    String displayName = docCursor.getString(0);
                    String mimeType = docCursor.getString(1);
                    Debug.Verbose(this.getClass(), "Primary doc="
                            + displayName + ", mime=" + mimeType);
                }
            } finally {
                closeQuietly(docCursor);
            }
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
            MutableInteger fileCount, boolean recursiveFiles
    ) throws SecurityException {
        List<String> binFiles = Arrays.asList(resources.getStringArray(R.array.mimetype_bin));
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, documentId);

        Cursor cursor;
        try {
            ArrayList<String> items = new ArrayList<>(binFiles);
            items.add(DocumentsContract.Document.MIME_TYPE_DIR);
            String[] selectionArgs = items.toArray(new String[0]);
            cursor = contentResolver.query(childrenUri, new String[] {
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_MIME_TYPE,
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID },
                    DocumentsContract.Document.COLUMN_MIME_TYPE, selectionArgs, null);
        } catch (SecurityException ex) {
            cursor = contentResolver.query(childrenUri, new String[] {
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_MIME_TYPE,
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID },
                    null, null, null);
        }
        try {
            while (cursor.moveToNext()) {
                fileCount.increment();
                String displayName = cursor.getString(0);
                String mimeType = cursor.getString(1);
                String childDocumentId = cursor.getString(2);
                Debug.Verbose(this.getClass(), "Child doc=" + displayName
                        + ", parent=" + documentId + ", mime=" + mimeType);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType) && recursiveFiles) {
                    queue.add(childDocumentId);
                } else if (binFiles.contains(mimeType)) {
                    files.add(DocumentsContract.buildDocumentUriUsingTree(rootUri, childDocumentId));
                }
            }
        } finally {
            closeQuietly(cursor);
        }
    }

    public void closeQuietly(AutoCloseable closeable) {
        if (null != closeable) {
            try {
                closeable.close();
            } catch (RuntimeException runtime) {
                throw runtime;
            } catch (Exception ignored) { }
        }
    }

    public static class MutableInteger {
        private int value;

        public MutableInteger(int value) {
            this.value = value;
        }

        public int get() {
            return value;
        }

        public void set(int value) {
            this.value = value;
        }

        public void increment() {
            this.value++;
        }
    }
}
