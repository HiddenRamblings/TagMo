/* ====================================================================
 * Copyright (c) 2012-2021 Abandoned Cart.  All rights reserved.
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
 *    "This product includes software developed by Abandoned Cart" unless
 *    otherwise displayed by tagged, public repository entries.
 *
 * 4. The names "8-Bit Dream", "TwistedUmbrella" and "Abandoned Cart"
 *    must not be used in any form to endorse or promote products
 *    derived from this software without prior written permission. For
 *    written permission, please contact enderinexiledc@gmail.com
 *
 * 5. Products derived from this software may not be called "8-Bit Dream",
 *    "TwistedUmbrella" or "Abandoned Cart" nor may these labels appear
 *    in their names without prior written permission of Abandoned Cart.
 *
 * THIS SOFTWARE IS PROVIDED BY Abandoned Cart ``AS IS'' AND ANY
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

package com.endgames.environment;

import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import com.hiddenramblings.tagmo.TagMo;

import java.io.File;

import static android.os.Environment.isExternalStorageEmulated;

@SuppressWarnings("ConstantConditions")
public class Storage {
    private static final String STORAGE_ROOT = "/storage";

    private static File storagePath;

    private static File getRootPath(File directory) {
        return directory.getParentFile().getParentFile().getParentFile().getParentFile();
    }

    private static File setFileStorageGeneric() {
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
            return storagePath = Environment.getExternalStorageDirectory();
        }
        if (TagMo.getPrefs().ignoreSdcard().get())
            return storagePath = emulated != null ? emulated : physical;
        return storagePath = physical != null ? physical : emulated;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private static File setFileStorageLollipop() {
        File[] storage = ContextCompat.getExternalFilesDirs(TagMo.getContext(), null);
        if (TagMo.getPrefs().ignoreSdcard().get()) {
            return storagePath = storage[0] != null && storage[0].canRead()
                    ? getRootPath(storage[0]) : setFileStorageGeneric();
        }
        try {
            return storagePath = storage.length > 1 && storage[1] != null
                    && storage[1].canRead() && !isExternalStorageEmulated(storage[1])
                    ? getRootPath(storage[1]) : storage[0] != null && storage[0].canRead()
                    ? getRootPath(storage[0]) : setFileStorageGeneric();
            // [TARGET]/Android/data/[PACKAGE]/files
        } catch (IllegalArgumentException | NullPointerException e) {
            return setFileStorageGeneric();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private static File setFileStorageRedVelvet() {
        File emulated = null;
        File physical = null;
        try {
            for (File directory : Environment.getStorageDirectory().listFiles()) {
                if (directory.getAbsolutePath().endsWith("emulated"))
                    emulated = new File(directory, "0");
                else if (!directory.getAbsolutePath().endsWith("self"))
                    physical = directory;
            }
            // Force a possible failure to prevent crash later
            Log.d("EMULATED", emulated.getAbsolutePath());
            Log.d("PHYSICAL", physical.getAbsolutePath());
        } catch (IllegalArgumentException | NullPointerException e) {
            return setFileStorageLollipop();
        }
        if (TagMo.getPrefs().ignoreSdcard().get())
            return storagePath = emulated != null ? emulated : physical;
        return storagePath = physical != null ? physical : emulated;
    }

    public static File setFileStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            return setFileStorageRedVelvet();
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            return setFileStorageLollipop();
        else
            return setFileStorageGeneric();
    }

    public static File getStorageFile() {
        return storagePath != null ? storagePath : setFileStorage();
    }
}
