package com.hiddenramblings.tagmo;

import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.documentfile.provider.DocumentFile;

import com.hiddenramblings.tagmo.eightbit.io.Debug;
import com.hiddenramblings.tagmo.eightbit.os.Storage;
import com.hiddenramblings.tagmo.github.InstallReceiver;
import com.hiddenramblings.tagmo.github.JSONExecutor;
import com.hiddenramblings.tagmo.settings.Preferences_;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.Executors;

public class CheckUpdatesTask {

    private static final String TAGMO_GIT_API =
            "https://api.github.com/repos/HiddenRamblings/TagMo/releases/tags/";
    private final Preferences_ prefs = TagMo.getPrefs();
    private final SoftReference<BrowserActivity> activity;

    CheckUpdatesTask(BrowserActivity activity) {
        this.activity = new SoftReference<>(activity);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            PackageInstaller installer = activity.getApplicationContext()
                    .getPackageManager().getPackageInstaller();
            for (PackageInstaller.SessionInfo session : installer.getMySessions()) {
                installer.abandonSession(session.getSessionId());
            }
        }
        Executors.newSingleThreadExecutor().execute(() -> {
            File[] files = activity.getExternalCacheDir().listFiles((dir, name) ->
                    name.toLowerCase(Locale.ROOT).endsWith(".apk"));
            if (null != files && files.length > 0) {
                for (File file : files) {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
            }
        });
        Executors.newSingleThreadExecutor().execute(() -> {
            boolean isMaster = prefs.settings_stable_channel().get();
            new JSONExecutor(TAGMO_GIT_API + (isMaster
                    ? "master" : "experimental")).setResultListener(result -> {
                if (null != result) parseUpdateJSON(result, isMaster);
            });
        });
    }

    void installUpdateTask(String apkUrl) {
        Executors.newSingleThreadExecutor().execute(() -> {
            File apk = new File(activity.get().getExternalCacheDir(), apkUrl.substring(
                    apkUrl.lastIndexOf(File.separator) + 1));
            try {
                DataInputStream dis = new DataInputStream(new URL(apkUrl).openStream());

                byte[] buffer = new byte[1024];
                int length;
                FileOutputStream fos = new FileOutputStream(apk);
                while ((length = dis.read(buffer)) > 0) {
                    fos.write(buffer, 0, length);
                }
                fos.close();

                if (!apk.getName().toLowerCase(Locale.ROOT).endsWith(".apk"))
                    //noinspection ResultOfMethodCallIgnored
                    apk.delete();

                Context applicationContext = activity.get().getApplicationContext();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    PackageInstaller installer = applicationContext
                            .getPackageManager().getPackageInstaller();
                    ContentResolver resolver = applicationContext.getContentResolver();
                    Uri apkUri = Storage.getFileUri(apk);
                    InputStream apkStream = resolver.openInputStream(apkUri);
                    PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                            PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                    int sessionId = installer.createSession(params);
                    PackageInstaller.Session session = installer.openSession(sessionId);
                    DocumentFile document = DocumentFile.fromSingleUri(applicationContext, apkUri);
                    if (document == null)
                        throw new IOException(activity.get().getString(R.string.fail_invalid_size));
                    OutputStream sessionStream = session.openWrite(
                            "NAME", 0, document.length());
                    byte[] buf = new byte[8192];
                    int size;
                    while ((size = apkStream.read(buf)) > 0) {
                        sessionStream.write(buf, 0, size);
                    }
                    session.fsync(sessionStream);
                    apkStream.close();
                    sessionStream.close();
                    PendingIntent pi = PendingIntent.getBroadcast(applicationContext, 8675309,
                            new Intent(applicationContext, InstallReceiver.class),
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                                    ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
                                    : PendingIntent.FLAG_UPDATE_CURRENT);
                    session.commit(pi.getIntentSender());
                } else {
                    Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                    intent.setDataAndType(Storage.getFileUri(apk),
                            activity.get().getString(R.string.mimetype_apk));
                    intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
                    intent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME,
                            activity.get().getApplicationInfo().packageName);
                    try {
                        activity.get().startActivity(NFCIntent.getIntent(intent));
                    } catch (ActivityNotFoundException anf) {
                        try {
                            activity.get().startActivity(intent.setAction(Intent.ACTION_VIEW));
                        } catch (ActivityNotFoundException ignored) {

                        }
                    }
                }
            } catch (MalformedURLException mue) {
                Debug.Log(mue);
            } catch (IOException ioe) {
                Debug.Log(ioe);
            } catch (SecurityException se) {
                Debug.Log(se);
            }
        });
    }

    private void installUpdateCompat(String apkUrl) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (activity.get().getPackageManager().canRequestPackageInstalls()) {
                installUpdateTask(apkUrl);
            } else {
                prefs.downloadUrl().put(apkUrl);
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                intent.setData(Uri.parse(String
                        .format("package:%s", activity.get().getPackageName())));
                activity.get().onRequestInstall.launch(intent);
            }
        } else {
            installUpdateTask(apkUrl);
        }
    }

    private void parseUpdateJSON(String result, boolean isMaster) {
        int offset = activity.get().getString(R.string.tagmo).length() + 1;
        String lastCommit = null, downloadUrl = null;
        try {
            JSONObject jsonObject = (JSONObject) new JSONTokener(result).nextValue();
            lastCommit = ((String) jsonObject.get("name")).substring(offset);
            JSONArray assets = (JSONArray) jsonObject.get("assets");
            JSONObject asset = (JSONObject) assets.get(0);
            downloadUrl = (String) asset.get("browser_download_url");
            if (!isMaster && !BuildConfig.COMMIT.equals(lastCommit))
                installUpdateCompat(downloadUrl);
        } catch (JSONException e) {
            Debug.Log(e);
        }

        if (isMaster && null != lastCommit && null != downloadUrl) {
            String finalLastCommit = lastCommit;
            String finalDownloadUrl = downloadUrl;
            new JSONExecutor(TAGMO_GIT_API + "experimental").setResultListener(experimental -> {
                try {
                    JSONObject jsonObject = (JSONObject) new JSONTokener(experimental).nextValue();
                    String extraCommit = ((String) jsonObject.get("name")).substring(offset);
                    if (!BuildConfig.COMMIT.equals(extraCommit)
                            && !BuildConfig.COMMIT.equals(finalLastCommit))
                        installUpdateCompat(finalDownloadUrl);
                } catch (JSONException e) {
                    Debug.Log(e);
                }
            });
        }
    }
}
