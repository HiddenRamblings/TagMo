/*
 * ====================================================================
 * Copyright (C) 2022 AbandonedCart @ TagMo
 * ====================================================================
 */

package com.hiddenramblings.tagmo;

import android.bluetooth.BluetoothDevice;

import com.mumumusuc.libjoycon.BluetoothHelper;
import com.mumumusuc.libjoycon.BluetoothHidHost;

import java.security.InvalidParameterException;

public final class JoyCon {
    private static final char[] template = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    public static final String JOYCON_L = "Joy-Con (L)";
    public static final String JOYCON_R = "Joy-Con (R)";
    public static final String PRO_CONTROLLER = "Pro Controller";

    public enum PollType {
        STANDARD(0x30);
        private final int type;

        PollType(int type) {
            this.type = type;
        }
    }

    private final BluetoothHelper helper;
    public final BluetoothDevice device;
    private final long mHandle;

    public JoyCon(BluetoothHelper helper, BluetoothDevice device) {
        String name = device.getName();
        switch (name) {
            case JOYCON_L:
                mHandle = create(1);
                break;
            case JOYCON_R:
                mHandle = create(2);
                break;
            case PRO_CONTROLLER:
                mHandle = create(0);
                break;
            default:
                throw new InvalidParameterException("unknown device " + device);
        }
        this.helper = helper;
        this.device = device;
    }

    private String byte2AsciiString(char[] data) {
        final StringBuilder sb = new StringBuilder();
        for (char b : data) {
            sb.append(template[(b >> 4) & 0xf]);
            sb.append(template[b & 0xf]);
        }
        return sb.toString();
    }

    private void setReport(String report) throws InterruptedException {
        //Log.d(TAG, "sendReport");
        final BluetoothHidHost host = helper.getHidHost();
        if (host != null)
            host.setReport(device, BluetoothHidHost.REPORT_TYPE_OUTPUT, report);
    }

    private void sendData(String report) {
        //Log.d(TAG, "sendData");
        final BluetoothHidHost host = helper.getHidHost();
        if (host != null) {
            host.sendData(device, report);
        }
    }

    public void enableRumble(boolean enable) {
        set_rumble(mHandle, enable);
    }

    public void rumble(byte[] data) {
        rumble(mHandle, data[0], data[1], data[2], data[3], data[4], data[5], data[6], data[7]);
    }

    public void rumble(byte hf_l, byte hfa_l, byte lf_l, byte lfa_l, byte hf_r, byte hfa_r, byte lf_r, byte lfa_r) {
        rumble(mHandle, hf_l, hfa_l, lf_l, lfa_l, hf_r, hfa_r, lf_r, lfa_r);
    }

    public void rumblef(RumbleDataF data) {
        rumblef(mHandle, data.hf_l, data.hfa_l, data.lf_l, data.lfa_l, data.hf_r, data.hfa_r, data.lf_r, data.lfa_r);
    }

    public void poll(PollType type) {
        poll(mHandle, (byte) (type.type));
    }

    public void setPlayer(int player, int flash) {
        set_player(mHandle, player, flash);
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        destroy(mHandle);
    }

    private static native void classInitNative();

    private native long create(int category);

    private native void destroy(long handle);

    private native int pair(long handle);

    private native int poll(long handle, byte poll_type);

    private native int set_low_power(long handle, boolean enable);

    private native int set_player(long handle, int player, int flash);

    private native int set_rumble(long handle, boolean enable);

    private native int rumble(long handle, short hf_l, byte hfa_l, byte lf_l, byte lfa_l, byte hf_r, byte hfa_r, byte lf_r, byte lfa_r);

    private native int rumblef(long handle, float hf_l, float hfa_l, float lf_l, float lfa_l, float hf_r, float hfa_r, float lf_r, float lfa_r);

    static {
        System.loadLibrary("joycon");
        classInitNative();
    }

    public static class RumbleDataF {
        final float hf_l;
        final float hfa_l;
        final float lf_l;
        final float lfa_l;
        final float hf_r;
        final float hfa_r;
        final float lf_r;
        final float lfa_r;

        public RumbleDataF(float hf_l, float hfa_l, float lf_l, float lfa_l, float hf_r, float hfa_r, float lf_r, float lfa_r) {
            this.hf_l = hf_l;
            this.hfa_l = hfa_l;
            this.lf_l = lf_l;
            this.lfa_l = lfa_l;
            this.hf_r = hf_r;
            this.hfa_r = hfa_r;
            this.lf_r = lf_r;
            this.lfa_r = lfa_r;
        }
    }
}
