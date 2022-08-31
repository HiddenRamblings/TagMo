/* ====================================================================
 * Copyright (c) 2012-2022 AbandonedCart.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * For the purpose of this license, the phrase "TagMo labels" shall
 * be used to refer to the labels "8-Bit Dream", "TwistedUmbrella",
 * "TagMo" and "AbandonedCart" and these labels should be considered
 * the equivalent of any usage of the aforementioned phrase.
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
 *    "This product includes software developed for TagMo by AbandonedCart"
 *
 * 4. The TagMo labels must not be used in any form to endorse or promote
 *    products derived from this software without prior written permission.
 *    For written permission, please contact enderinexiledc@gmail.com
 *
 * 5. Products derived from this software may not be called by the TagMo labels
 *    nor may these labels appear in their names or product information without
 *    prior written permission of AbandonedCart.
 *
 * THIS SOFTWARE IS PROVIDED BY AbandonedCart AND TagMo ``AS IS'' AND ANY
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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import com.heinrichreimersoftware.androidissuereporter.IssueReporterLauncher;
import com.hiddenramblings.tagmo.BuildConfig;
import com.hiddenramblings.tagmo.R;
import com.hiddenramblings.tagmo.TagMo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.Objects;

@SuppressWarnings("unused")
public class Debug {

    private static Context getContext() {
        return TagMo.getContext();
    }

    private static boolean hasDebugging() {
        return !TagMo.getPrefs().settings_disable_debug().get();
    }

    public static String TAG(Class<?> source) {
        return source.getSimpleName();
    }

    public static void Error(Class<?> source, String params) {
        if (hasDebugging()) Log.e(TAG(source), params);
    }
    public static void Error(Class<?> source, int resource) {
        Error(source, getContext().getString(resource));
    }
    public static void Error(Class<?> source, int resource, String params) {
        Error(source,getContext().getString(resource, params));
    }
    public static void Error(Exception ex) {
        if (!hasDebugging()) return;
        if (ex.getStackTrace().length > 0) {
            StringWriter exception = new StringWriter();
            ex.printStackTrace(new PrintWriter(exception));
            Error(ex.getClass(), exception.toString());
        }
    }
    public static void Error(int resource, Exception ex) {
        if (hasDebugging()) Log.e(TAG(ex.getClass()), getContext().getString(resource), ex);
    }

    public static void Warn(Class<?> source, String params) {
        if (hasDebugging()) Log.w(TAG(source), params);
    }
    public static void Warn(Class<?> source, int resource) {
        Warn(source, getContext().getString(resource));
    }
    public static void Warn(Class<?> source, int resource, String params) {
        Warn(source,getContext().getString(resource, params));
    }
    public static void Warn(Exception ex) {
        if (!hasDebugging()) return;
        if (ex.getStackTrace().length > 0) {
            StringWriter exception = new StringWriter();
            ex.printStackTrace(new PrintWriter(exception));
            Warn(ex.getClass(), exception.toString());
        }
    }
    public static void Warn(int resource, Exception ex) {
        if (hasDebugging()) Log.w(TAG(ex.getClass()), getContext().getString(resource), ex);
    }

    public static void Info(Class<?> source, String params) {
        if (hasDebugging()) Log.i(TAG(source), params);
    }
    public static void Info(Class<?> source, int resource) {
        Info(source, getContext().getString(resource));
    }
    public static void Info(Class<?> source, int resource, String params) {
        Info(source,getContext().getString(resource, params));
    }
    public static void Info(Exception ex) {
        if (!hasDebugging()) return;
        if (ex.getStackTrace().length > 0) {
            StringWriter exception = new StringWriter();
            ex.printStackTrace(new PrintWriter(exception));
            Info(ex.getClass(), exception.toString());
        }
    }
    public static void Info(int resource, Exception ex) {
        if (hasDebugging()) Log.i(TAG(ex.getClass()), getContext().getString(resource), ex);
    }

    public static void Verbose(Class<?> source, String params) {
        if (hasDebugging()) Log.d(TAG(source), params);
    }
    public static void Verbose(Class<?> source, int resource) {
        Verbose(source, getContext().getString(resource));
    }
    public static void Verbose(Class<?> source, int resource, String params) {
        Verbose(source,getContext().getString(resource, params));
    }
    public static void Verbose(Exception ex) {
        if (!hasDebugging()) return;
        if (ex.getStackTrace().length > 0) {
            StringWriter exception = new StringWriter();
            ex.printStackTrace(new PrintWriter(exception));
            Verbose(ex.getClass(), exception.toString());
        }
    }
    public static void Verbose(int resource, Exception ex) {
        if (hasDebugging()) Log.d(TAG(ex.getClass()), getContext().getString(resource), ex);
    }

    private static String getRepositoryToken() {
        String hex = "6768705f74314953736669344f4c4158315373657167636a4f5a42783641736b6f33314f7650697a";
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < hex.length(); i+=2) {
            String str = hex.substring(i, i+2);
            output.append((char)Integer.parseInt(str, 16));
        }
        return output.toString();
    }

    private static final String issueUrl = "https://github.com/HiddenRamblings/TagMo/issues/new?"
            + "labels=logcat&template=bug_report.yml&title=[Bug]%3A+";

    public static StringBuilder getDeviceProfile(Context context) {
        String separator = System.getProperty("line.separator") != null
                ? Objects.requireNonNull(System.getProperty("line.separator")) : "\n";
        final StringBuilder log = new StringBuilder(separator);
        log.append(context.getString(R.string.build_hash, BuildConfig.COMMIT));
        log.append(separator);
        log.append("Android ");
        Field[] fields = Build.VERSION_CODES.class.getFields();
        String codeName = "UNKNOWN";
        for (Field field : fields) {
            try {
                if (field.getInt(Build.VERSION_CODES.class) == Build.VERSION.SDK_INT) {
                    codeName = field.getName();
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        log.append(codeName);
        log.append(" (");
        log.append(Build.VERSION.RELEASE);
        log.append(")");
        log.append(separator).append(context.getString(R.string.install_src, BuildConfig.BUILD_TYPE));
        return log;
    }

    private static void submitLogcat(Context context, String logText) {
        final Intent emailIntent = new Intent(Intent.ACTION_SEND);
        emailIntent.setType("text/plain");
        emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{"samsprungtoo@gmail.com"});
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "TagMo Logcat");
        emailIntent.putExtra(Intent.EXTRA_TEXT, logText);
        emailIntent.setType("message/rfc822");
        try {
            context.startActivity(Intent.createChooser(emailIntent, "Email logcat via..."));
        } catch (android.content.ActivityNotFoundException ex) {
            ClipboardManager clipboard = (ClipboardManager) context
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("TagMo", logText));
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(issueUrl)));
        }
    }

    public static void processException(Context context, String exception) {
        String separator = System.getProperty("line.separator") != null
                ? Objects.requireNonNull(System.getProperty("line.separator")) : "\n";
        final StringBuilder log = getDeviceProfile(context);
        log.append(separator).append(separator).append(exception);
        submitLogcat(context, log.toString());
    }

    public static boolean processLogcat(Context context) throws IOException {
        String project = context.getString(R.string.tagmo);
        String username = "HiddenRamblings";

        String separator = System.getProperty("line.separator") != null
                ? Objects.requireNonNull(System.getProperty("line.separator")) : "\n";
        final StringBuilder log = getDeviceProfile(context);
        Process mLogcatProc = Runtime.getRuntime().exec(new String[]{
                "logcat", "-d", "-t", "192", BuildConfig.APPLICATION_ID,
                "AndroidRuntime", "System.err",
                "ViewRootImpl*:S", "IssueReporterActivity:S", "*:W"
        });
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                mLogcatProc.getInputStream()));
        log.append(separator).append(separator);
        String line;
        while (null != (line = reader.readLine())) {
            log.append(line).append(separator);
        }
        reader.close();
        String logText = log.toString();

        if (!logText.contains("AndroidRuntime")) {
            submitLogcat(context, logText);
            return false;
        }
        try {
            IssueReporterLauncher.forTarget(username, project)
                    .theme(R.style.AppTheme_NoActionBar)
                    .guestToken(getRepositoryToken())
                    .guestEmailRequired(false)
                    .publicIssueUrl(issueUrl)
                    .titleTextDefault(context.getString(R.string.git_issue_title, BuildConfig.COMMIT))
                    .minDescriptionLength(1)
                    .putExtraInfo("logcat", logText)
                    .homeAsUpEnabled(false).launch(context);
            return true;
        } catch (Exception ignored) {
            submitLogcat(context, logText);
            return true;
        }
    }
}
