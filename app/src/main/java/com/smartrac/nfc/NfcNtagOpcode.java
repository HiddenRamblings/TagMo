package com.smartrac.nfc;

/*
 * *#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*#*
 * SMARTRAC SDK for Android NFC NTAG
 * ===============================================================================
 * Copyright (C) 2016 SMARTRAC TECHNOLOGY GROUP
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


public class NfcNtagOpcode {
    public static final byte GET_VERSION = 0x60;
    public static final byte READ = 0x30;
    public static final byte FAST_READ = 0x3A;
    public static final byte WRITE = (byte) 0xA2;
    public static final byte READ_CNT = 0x39;
    public static final byte PWD_AUTH = 0x1B;
    public static final byte READ_SIG = 0x3C;
    public static final byte SECTOR_SELECT = (byte) 0xC2;
    public static final byte READ_TT_STATUS = (byte) 0xA4;
    public static final byte MFULC_AUTH1 = 0x1A;
    public static final byte MFULC_AUTH2 = (byte) 0xAF;

    // N2 Elite
    public static final int N2_SELECT_BANK = 0xA7;
    public static final int N2_FAST_READ = 0x3B;
    public static final int N2_FAST_WRITE = 0xAE;
    public static final int AMIIBO_GET_VERSION = 0x55; // N2_GET_INFO
    public static final int N2_LOCK = 0x46;
    public static final int AMIIBO_READ_SIG = 0x43;// N2_GET_ID
    public static final int N2_SET_BANK_CNT = 0xA9;
    public static final int N2_UNLOCK1 = 0x44;
    public static final int N2_UNLOCK2 = 0x45;
    public static final int N2_WRITE = 0xA5;
}
