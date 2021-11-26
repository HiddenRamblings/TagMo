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

package com.hiddenramblings.tagmo.eightbit.os;

import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.hiddenramblings.tagmo.BuildConfig;
import com.hiddenramblings.tagmo.TagMo;

import java.io.File;
import java.io.InputStream;
import java.util.HashSet;

@SuppressWarnings({"ConstantConditions", "unused"})
public class Storage extends Environment {
    static final String PROVIDER = BuildConfig.APPLICATION_ID + ".provider";
    private static final String STORAGE_ROOT = "/storage";

    private static File storageFile;
    private static boolean isInternalPreferred;
    private static boolean isPhysicalAvailable;

    private static File getRootPath(File directory) {
        return directory.getParentFile().getParentFile().getParentFile().getParentFile();
    }

    public static boolean hasPhysicalStorage() {
        return isPhysicalAvailable;
    }

    private static HashSet<String> getExternalMounts() {
        final HashSet<String> out = new HashSet<>();
        String reg = "(?i).*vold.*(vfat|ntfs|exfat|fat32|ext3|ext4|fuse|sdfat).*rw.*";
        StringBuilder s = new StringBuilder();
        try {
            final Process process = new ProcessBuilder().command("mount")
                    .redirectErrorStream(true).start();
            process.waitFor();
            InputStream is = process.getInputStream();
            byte[] buffer = new byte[1024];
            while (is.read(buffer) != -1) {
                s.append(new String(buffer));
            }
            is.close();

            String[] lines = s.toString().split("\n");
            for (String line : lines) {
                if (line.contains("secure")) continue;
                if (line.contains("asec")) continue;
                if (line.matches(reg)) {
                    for (String part : line.split(" ")) {
                        if (part.startsWith("/") && !part.contains("vold"))
                            out.add(part);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out;
    }

    private static File setFileMounts() {
        HashSet<String> extStorage = getExternalMounts();
        if (null != extStorage && !extStorage.isEmpty()) {
            for (String sd : extStorage) {
                // Workaround for WRITE_MEDIA_STORAGE
                String sdCardPath = sd.replace("mnt/media_rw", "storage");
                if (!sdCardPath.equals(getExternalStorageDirectory().getAbsolutePath())
                        && new File(sdCardPath).canRead()) {
                    isPhysicalAvailable = true;
                    return new File(sdCardPath);
                }
            }
        }
        return getExternalStorageDirectory();
    }

    private static File setFileGeneric(boolean internal) {
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
            if (null != physical && physical != emulated)
                isPhysicalAvailable = true;
        } catch (NullPointerException e) {
            if (internal) return getExternalStorageDirectory();
            return storageFile = setFileMounts();
        }
        if (internal)
            return storageFile = null != emulated ? emulated : physical;
        return storageFile = null != physical ? physical : emulated;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private static File setFileLollipop(boolean internal) {
        File[] storage = ContextCompat.getExternalFilesDirs(TagMo.getContext(), null);
        File emulated;
        File physical;
        try {
            emulated = null != storage[0] && storage[0].canRead() ? getRootPath(storage[0]) : null;
        } catch (IllegalArgumentException | NullPointerException e) {
            emulated = null;
        }
        try {
            physical = storage.length > 1 && null != storage[1] && storage[1].canRead()
                    && !isExternalStorageEmulated(storage[1]) ? getRootPath(storage[1]) : null;
        } catch (IllegalArgumentException | NullPointerException e) {
            physical = null;
        }
        if (null != physical && physical != emulated)
            isPhysicalAvailable = true;
        if (internal)
            return storageFile = null != emulated ? emulated : setFileGeneric(internal);
        return null != physical ? physical : null != emulated ? emulated : setFileGeneric(internal);
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private static File setFileRedVelvet(boolean internal) {
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
            if (null != physical && physical != emulated)
                isPhysicalAvailable = true;
        } catch (IllegalArgumentException | NullPointerException e) {
            return setFileLollipop(internal);
        }
        if (internal)
            return storageFile = null != emulated ? emulated : physical;
        return storageFile = null != physical ? physical : emulated;
    }

    private static File setFile(boolean internal) {
        isInternalPreferred = internal;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            return setFileRedVelvet(internal);
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            return setFileLollipop(internal);
        else
            return setFileGeneric(internal);
    }

    public static File getFile(boolean internal) {
        return null != storageFile && internal == isInternalPreferred
                ? storageFile : setFile(internal);
    }

    public static String getPath(boolean internal) {
        return getFile(internal).getAbsolutePath();
    }

    public static Uri getFileUri(File file) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                ? FileProvider.getUriForFile(TagMo.getContext(), PROVIDER, file)
                : Uri.fromFile(file);
    }

    public static String getRelativePath(File file, boolean internal) {
        String filePath = file.getAbsolutePath();
        String storagePath = getPath(internal);
        return filePath.startsWith(storagePath)
                ? filePath.substring(storagePath.length()) : filePath;
    }

    public static File getDownloadDir(String directory, String subfolder) {
        File downloads = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS);
        if (null != directory && null != subfolder)
            return new File(downloads, directory + File.separator + subfolder);
        else if (null != directory)
            return new File(downloads, directory);
        else
            return downloads;
    }

    public static File getDownloadDir(String directory) {
        return getDownloadDir(directory, null);
    }
}
