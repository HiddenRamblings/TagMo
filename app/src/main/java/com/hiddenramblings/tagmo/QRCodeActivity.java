package com.hiddenramblings.tagmo;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.net.Uri;
import android.support.v4.content.FileProvider;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.ShareActionProvider;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.ImageView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.OptionsMenu;
import org.androidannotations.annotations.OptionsMenuItem;
import org.androidannotations.annotations.ViewById;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

@EActivity(R.layout.activity_qr_code)
@OptionsMenu(R.menu.qr_menu)
public class QRCodeActivity extends AppCompatActivity {
    public static final int WHITE = 0xFFFFFFFF;
    public static final int BLACK = 0xFF000000;
    public static final int WIDTH = 500;

    @ViewById(R.id.imgQRCode)
    ImageView imgQRCode;

    @OptionsMenuItem(R.id.action_share)
    MenuItem shareMenu;

    ShareActionProvider shareAction;
    Bitmap image;

    @AfterViews
    void afterViews() {
        try {
            this.image = encodeAsBitmap(this.getIntent().getStringExtra("ENCODE_DATA"));
            this.imgQRCode.setImageBitmap(this.image);

            this.setShareIntent(this.shareAction, this.image);
        } catch (WriterException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean result = super.onCreateOptionsMenu(menu);

        this.shareAction = (ShareActionProvider) MenuItemCompat.getActionProvider(shareMenu);
        this.setShareIntent(this.shareAction, this.image);

        return result;
    }

    void setShareIntent(ShareActionProvider shareAction, Bitmap image) {
        if (shareAction == null || image == null)
            return;

        File cacheDir = new File(this.getCacheDir(), "qr_codes");
        cacheDir.mkdirs();
        File tempFile = new File(cacheDir, "image.png");

        OutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(tempFile);
            this.image.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        Log.d("this", String.valueOf(tempFile));
        Uri uri = FileProvider.getUriForFile(this, "com.hiddenramblings.tagmo", tempFile);

        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setDataAndType(uri, getContentResolver().getType(uri));
        intent.putExtra(Intent.EXTRA_STREAM, uri);

        this.shareAction.setShareIntent(intent);
    }

    Bitmap encodeAsBitmap(String content) throws WriterException {
        int dimension;
        {
            WindowManager manager = (WindowManager) getSystemService(WINDOW_SERVICE);
            Display display = manager.getDefaultDisplay();
            Point displaySize = new Point();
            display.getSize(displaySize);
            int width = displaySize.x;
            int height = displaySize.y;
            int smallerDimension = width < height ? width : height;
            dimension = smallerDimension * 7 / 8;
        }

        BitMatrix result;
        try {
            result = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, dimension, dimension, null);
        } catch (IllegalArgumentException e) {
            // Unsupported format
            return null;
        }

        int width = result.getWidth();
        int height = result.getHeight();
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            int offset = y * width;
            for (int x = 0; x < width; x++) {
                pixels[offset + x] = result.get(x, y) ? BLACK : WHITE;
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }
}
