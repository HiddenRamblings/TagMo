package com.smartrac.nfc;

/*
 * *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*
 * SMARTRAC SDK for Android NFC NTAG
 * ===============================================================================
 * Copyright (C) 2016 - 2017 Smartrac Technology Fletcher, Inc.
 * ===============================================================================
 * SMARTRAC SDK
 * (C) Copyright 2016, Smartrac Technology Fletcher, Inc.
 * 267 Cane Creek Rd, Fletcher, NC, 28732, USA
 * All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#
 */

import java.util.Arrays;

public class NfcNtagTtStatus {

    public static final byte STATE_CLOSED = 0x43;
    public static final byte STATE_OPEN = 0x4F;
    public static final byte STATE_INCORRECT = 0x49;

    public NfcNtagTtStatus(byte[] ttStatusBytes) throws IllegalArgumentException {

        if (ttStatusBytes.length != 5) {
            throw new IllegalArgumentException("Read TT status response length must be 5");
        }
        message = new byte[4];
        currentLoopState = ttStatusBytes[4];
        System.arraycopy(ttStatusBytes, 0, message, 0, message.length);
    }

    public boolean isTampered() {

        return !Arrays.equals(TT_INITIAL, message) || !(STATE_CLOSED == currentLoopState);
    }

    public byte[] getMessage() {

        return message.clone();
    }

    public byte getCurrentLoopState() {

         return currentLoopState;
    }

    private static final byte[] TT_INITIAL = {0x30, 0x30, 0x30, 0x30};

    private byte[] message;

    private byte currentLoopState;
}
