package com.hiddenramblings.tagmo.charset;

import android.os.Build;

import com.hiddenramblings.tagmo.eightbit.io.Debug;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@SuppressWarnings("all")
public class CharsetCompat {
    public static Charset UTF_8 = Debug.isNewer(Build.VERSION_CODES.KITKAT)
            ? UTF_8 = StandardCharsets.UTF_8
            : Charset.forName("UTF-8");
    public static Charset UTF_16 = Debug.isNewer(Build.VERSION_CODES.KITKAT)
            ? StandardCharsets.UTF_16
            : Charset.forName("UTF-16");
    public static Charset ISO_8859_1 = Debug.isNewer(Build.VERSION_CODES.KITKAT)
            ? StandardCharsets.ISO_8859_1
            : Charset.forName("ISO-8859-1");
    public static Charset US_ASCII = Debug.isNewer(Build.VERSION_CODES.KITKAT)
            ? StandardCharsets.US_ASCII
            : Charset.forName("US_ASCII");
    public static Charset UTF_16BE = Debug.isNewer(Build.VERSION_CODES.KITKAT)
            ? StandardCharsets.UTF_16BE
            : Charset.forName("UTF-16BE");
    public static Charset UTF_16LE = Debug.isNewer(Build.VERSION_CODES.KITKAT)
            ? StandardCharsets.UTF_16LE
            : Charset.forName("UTF-16LE");
}
