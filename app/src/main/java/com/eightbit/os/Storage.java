/* ====================================================================
 * Copyright (c) 2012-2021 AbandonedCart.  All rights reserved.
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
 * 3. All advertising materials mentioning features or use of this
 *    software and redistributions of any form whatsoever
 *    must display the following acknowledgment:
 *    "This product includes software developed by AbandonedCart" unless
 *    otherwise displayed by tagged, public repository entries.
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

package com.eightbit.os;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.hiddenramblings.tagmo.BuildConfig;
import com.hiddenramblings.tagmo.TagMo;
import com.hiddenramblings.tagmo.TagMo_;

import java.io.File;
import java.lang.ref.SoftReference;

@SuppressWarnings({"ConstantConditions", "unused"})
public class Storage extends Environment {
    private static final String STORAGE_ROOT = "/storage";

    private static File storageFile;

    private static File getRootPath(File directory) {
        return directory.getParentFile().getParentFile().getParentFile().getParentFile();
    }

    private static File setFileGeneric() {
        File emulated = null;
        File physical = null;
        try {
            for (File directory : new File(STORAGE_ROOT).listFiles()) {
                if (directory.getAbsolutePath().endsWith("emulated"))
                    emulated = new File(directory, "0");
                else if (!directory.getAbsolutePath().endsWith("self"))
                    physical = directory;
            }
            // Force a possible failure to prevent crash later
            Log.d("EMULATED", emulated.getAbsolutePath());
            Log.d("PHYSICAL", physical.getAbsolutePath());
        } catch (NullPointerException e) {
            return storageFile = getExternalStorageDirectory();
        }
        if (TagMo.getPrefs().ignoreSdcard().get())
            return storageFile = emulated != null ? emulated : physical;
        return storageFile = physical != null ? physical : emulated;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private static File setFileLollipop() {
        File[] storage = ContextCompat.getExternalFilesDirs(TagMo.getContext(), null);
        if (TagMo.getPrefs().ignoreSdcard().get()) {
            return storageFile = storage[0] != null && storage[0].canRead()
                    ? getRootPath(storage[0]) : setFileGeneric();
        }
        try {
            return storageFile = storage.length > 1 && storage[1] != null
                    && storage[1].canRead() && !isExternalStorageEmulated(storage[1])
                    ? getRootPath(storage[1]) : storage[0] != null && storage[0].canRead()
                    ? getRootPath(storage[0]) : setFileGeneric();
            // [TARGET]/Android/data/[PACKAGE]/files
        } catch (IllegalArgumentException | NullPointerException e) {
            return setFileGeneric();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private static File setFileRedVelvet() {
        File emulated = null;
        File physical = null;
        try {
            for (File directory : getStorageDirectory().listFiles()) {
                if (directory.getAbsolutePath().endsWith("emulated"))
                    emulated = new File(directory, "0");
                else if (!directory.getAbsolutePath().endsWith("self"))
                    physical = directory;
            }
            // Force a possible failure to prevent crash later
            Log.d("EMULATED", emulated.getAbsolutePath());
            Log.d("PHYSICAL", physical.getAbsolutePath());
        } catch (IllegalArgumentException | NullPointerException e) {
            return setFileLollipop();
        }
        if (TagMo.getPrefs().ignoreSdcard().get())
            return storageFile = emulated != null ? emulated : physical;
        else
            return storageFile = physical != null ? physical : emulated;
    }

    public static File setFile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            return setFileRedVelvet();
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            return setFileLollipop();
        else
            return setFileGeneric();
    }

    public static File getFile() {
        return storageFile != null ? storageFile : setFile();
    }

    public static String getPath() {
        return (storageFile != null ? storageFile : setFile()).getAbsolutePath();
    }

    public static String getRelativePath(File file) {
        String filePath = file.getAbsolutePath();
        String storagePath = getPath();
        return filePath.startsWith(storagePath)
                ? filePath.substring(storagePath.length()) : filePath;
    }

    public static Uri getFileUri(File file) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? FileProvider.getUriForFile(TagMo.getContext(),
                BuildConfig.APPLICATION_ID + ".provider", file)
                : Uri.fromFile(file);
    }
}
