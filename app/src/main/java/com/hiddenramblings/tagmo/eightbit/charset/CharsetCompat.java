package com.hiddenramblings.tagmo.eightbit.charset;

import android.os.Build;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@SuppressWarnings("all")
public class CharsetCompat {
    public static Charset UTF_8 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
            ? UTF_8 = StandardCharsets.UTF_8
            : Charset.forName("UTF-8");
    public static Charset UTF_16 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
            ? StandardCharsets.UTF_16
            : Charset.forName("UTF-16");
    public static Charset ISO_8859_1 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
            ? StandardCharsets.ISO_8859_1
            : Charset.forName("ISO-8859-1");
    public static Charset US_ASCII = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
            ? StandardCharsets.US_ASCII
            : Charset.forName("US_ASCII");
    public static Charset UTF_16BE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
            ? StandardCharsets.UTF_16BE
            : Charset.forName("UTF-16BE");
    public static Charset UTF_16LE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
            ? StandardCharsets.UTF_16LE
            : Charset.forName("UTF-16LE");
}
