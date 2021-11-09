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

package com.eightbit.content;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

@SuppressWarnings("unused")
public class ActionIntent extends Intent {

    public ActionIntent(Intent intent) {
        super(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                ? intent
                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                : intent
        );
    }

    @TargetApi(Build.VERSION_CODES.N)
    public ActionIntent(String action) {
        super(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                ? new Intent(action)
                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                : new Intent(action)
        );
    }

    @TargetApi(Build.VERSION_CODES.N)
    public ActionIntent(Context context, Class<?> cls) {
        super(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                ? new Intent(context, cls)
                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                : new Intent(context, cls)
        );
    }

    @TargetApi(Build.VERSION_CODES.N)
    public ActionIntent(String action, Uri uri) {
        super(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                ? new Intent(action, uri)
                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                : new Intent(action, uri)
        );
    }

    @TargetApi(Build.VERSION_CODES.N)
    public ActionIntent(String action, Uri uri, Context context, Class<?> cls) {
        super(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                ? new Intent(action, uri, context, cls)
                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                : new Intent(action, uri, context, cls)
        );
    }
}
