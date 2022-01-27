/* ====================================================================
 * Copyright (c) 2012-2022 AbandonedCart.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. All materials mentioning features or use of this software and
 *    redistributions of any form whatsoever must display the following
 *    acknowledgment unless made available by tagged, public "commits":
 *    "This product includes software developed by AbandonedCart"
 *
 * 4. The names "8-Bit Dream", "TwistedUmbrella" and "AbandonedCart"
 *    must not be used in any form to endorse or promote products
 *    derived from this software without prior written permission. For
 *    written permission, please contact enderinexiledc@gmail.com
 *
 * 5. Products derived from this software may not be called "8-Bit Dream",
 *    "TwistedUmbrella" or "AbandonedCart" nor may these labels appear
 *    in their names without prior written permission of AbandonedCart.
 *
 * THIS SOFTWARE IS PROVIDED BY AbandonedCart ``AS IS'' AND ANY
 * EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE OpenSSL PROJECT OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * ====================================================================
 *
 * The license and distribution terms for any publicly available version or
 * derivative of this code cannot be changed.  i.e. this code cannot simply be
 * copied and put under another distribution license
 * [including the GNU Public License.] Content not subject to these terms is
 * subject to to the terms and conditions of the Apache License, Version 2.0.
 */

package com.hiddenramblings.tagmo.eightbit.io;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import com.hiddenramblings.tagmo.BuildConfig;
import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.eightbit.os.Storage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

@SuppressWarnings("unused")
public class Debug {

    public static String TAG(Class<?> source) {
        return source.getSimpleName();
    }

    public static void Log(Class<?> source, String params) {
        if (!TagMo.getPrefs().settings_disable_debug().get()) Log.w(TAG(source), params);
    }

    public static void Log(Class<?> source, int resource) {
        Log(source, TagMo.getContext().getString(resource));
    }

    public static void Log(Class<?> source, int resource, String params) {
        Log(source, TagMo.getContext().getString(resource, params));
    }

    public static void Log(Exception ex) {
        if (TagMo.getPrefs().settings_disable_debug().get()) return;
        if (ex.getStackTrace().length > 0) {
            StringWriter exception = new StringWriter();
            ex.printStackTrace(new PrintWriter(exception));
            Log(ex.getClass(), exception.toString());
        }
    }

    public static void Log(int resource, Exception ex) {
        if (!TagMo.getPrefs().settings_disable_debug().get())
            Log.w(TAG(ex.getClass()), TagMo.getContext().getString(resource), ex);
    }

    public static Uri processLogcat(Context context, String displayName) throws IOException {
        final StringBuilder log = new StringBuilder();
        String separator = System.getProperty("line.separator");
        log.append(context.getString(R.string.build_hash, BuildConfig.COMMIT));
        Process mLogcatProc = Runtime.getRuntime().exec(new String[]{
                "logcat", "-d", "-t", "256", BuildConfig.APPLICATION_ID,
                "ViewRootImpl:S", "IssueReporterActivity:S",
        });
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                mLogcatProc.getInputStream()));
        log.append(separator).append(separator);
        String line;
        while (null != (line = reader.readLine())) {
            log.append(line).append(separator);
        }
        reader.close();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE,
                        context.getString(R.string.mimetype_text));
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + File.separator
                                + "TagMo" + File.separator + "Logcat");
                ContentResolver resolver = context.getContentResolver();
                Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);
                try (OutputStream fos = resolver.openOutputStream(uri)) {
                    fos.write(log.toString().getBytes());
                }
                return uri;
            } else {
                File file = new File(Storage.getDownloadDir("TagMo",
                        "Logcat"), displayName + ".txt");
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(log.toString().getBytes());
                }
                try {
                    MediaScannerConnection.scanFile(context, new String[]{
                            file.getAbsolutePath()
                    }, new String[]{context.getString(R.string.mimetype_text)}, null);
                } catch (Exception e) {
                    // Media scan can fail without adverse consequences
                }
                return Uri.fromFile(file);
            }
        } catch (Exception e) {
            if (!"crash_logcat".equals(displayName))
                throw new IOException(context.getString(R.string.fail_logcat));

            File file = new File(context.getExternalFilesDir(null),
                    displayName + ".txt");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(log.toString().getBytes());
            }
            return Uri.fromFile(file);
        }
    }
}
