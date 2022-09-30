package com.hiddenramblings.tagmo.eightbit.bluetooth;

import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GattArray {

    public static List<byte[]> byteToPortions(byte[] largeByteArray, int sizePerPortion) {
        List<byte[]> byteArrayPortions = new ArrayList<>();
        int offset = 0;
        while (offset < largeByteArray.length) {
            byte[] portion = Arrays.copyOfRange(largeByteArray, offset, offset + sizePerPortion);
            offset += sizePerPortion;
            byteArrayPortions.add(portion);
        }
        return byteArrayPortions;
    }

    public static List<String> stringToPortions(String largeString, int sizePerPortion) {
        List<String> stringPortions = new ArrayList<>();
        int size = largeString.length();
        if (size <= sizePerPortion) {
            stringPortions.add(largeString);
        } else {
            int index = 0;
            while (index < size) {
                stringPortions.add(largeString.substring(index,
                        Math.min(index + sizePerPortion, largeString.length())));
                index += sizePerPortion;
            }
        }
        return stringPortions;
    }

    public static String stringToUnicode(String s) {
        StringBuilder sb = new StringBuilder(s.length() * 3);
        for (char c : s.toCharArray()) {
            if (c < 256) {
                sb.append(c);
            } else {
                String strHex = Integer.toHexString(c);
                sb.append("\\u").append(strHex);
            }
        }
        return sb.toString();
    }
}
