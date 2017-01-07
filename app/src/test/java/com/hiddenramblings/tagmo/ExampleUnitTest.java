package com.hiddenramblings.tagmo;

import android.test.ActivityTestCase;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * To work on unit tests, switch the Test Artifact in the Build Variants view.
 */
public class ExampleUnitTest  {
    @Test
    public static void main(String... args) throws Exception {
        assertEquals(4, 2 + 2);

        int brand = 0x08 & 0xFF << 8;
        brand |= (0x01 & 0xFF);

        int variant = 0x00 & 0xFF;

        int type = 0x00 & 0xFF;

        assertEquals(0x08010000, brand << 12 & variant << 8 & type );
    }
}