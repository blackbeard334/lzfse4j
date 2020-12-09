package lzfse;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DecodeBaseTest {

    @Test
    void lzfse_decode_v1_freq_value() {
        int[] nbits = {0};
        int i = DecodeBase.lzfse_decode_v1_freq_value(Integer.MIN_VALUE, nbits);
        assertEquals(2, nbits[0]);
        assertEquals(0, i);

        i = DecodeBase.lzfse_decode_v1_freq_value(0, nbits);
        assertEquals(0, i);
        assertEquals(2, nbits[0]);

        i = DecodeBase.lzfse_decode_v1_freq_value(7, nbits);
        assertEquals(8, i);
        assertEquals(8, nbits[0]);

        i = DecodeBase.lzfse_decode_v1_freq_value(15, nbits);
        assertEquals(14, nbits[0]);
        assertEquals(24, i);

        i = DecodeBase.lzfse_decode_v1_freq_value(Integer.MAX_VALUE, nbits);
        assertEquals(14, nbits[0]);
        assertEquals(1047, i);

        assertThrows(NullPointerException.class, () -> DecodeBase.lzfse_decode_v1_freq_value(0, null));
    }

    @Test
    void get_field() {

        // get single bits
        for (int i = 0; i < Long.SIZE - 1; i++) {
            assertEquals(1, DecodeBase.get_field(-1, i, 1));
        }
        assertThrows(AssertionError.class, () -> DecodeBase.get_field(-1, Long.SIZE, 1));
        assertThrows(AssertionError.class, () -> DecodeBase.get_field(-1, 0, Long.SIZE));
        assertEquals(0, DecodeBase.get_field(-1, 0, 0));

        // get chunks of 4 bits -> 0b1111 -> 15
        assertEquals(15, DecodeBase.get_field(-1, 0, 4));
        assertEquals(15, DecodeBase.get_field(-1, 4, 4));
        assertEquals(15, DecodeBase.get_field(-1, 8, 4));
        assertEquals(15, DecodeBase.get_field(-1, 12, 4));

        // any large enough long overflows to a -1 int
        assertEquals(-1, DecodeBase.get_field(Long.MAX_VALUE, 0, 32));
        assertEquals(-1, DecodeBase.get_field(-1, 0, 32));

        assertEquals(Integer.MAX_VALUE, DecodeBase.get_field(Integer.MAX_VALUE, 0, 32));
    }

    @Test
    void copy() {
        final byte[] array = {0, 1, 2, 3, 4, 5, 6, 7};
        final int length = array.length;
        ByteBuffer src = ByteBuffer.wrap(array);
        ByteBuffer dst = ByteBuffer.allocate(length);

        assertEquals(0, src.position());
        assertEquals(0, dst.position());
        assertNotEquals(src.getLong(0), dst.getLong(0));

        DecodeBase.copy(dst, src, length);

        // positions are unchanged
        assertEquals(0, src.position());
        assertEquals(0, dst.position());
        assertEquals(src.getLong(0), dst.getLong(0));
    }
}