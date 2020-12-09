package lzfse;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static lzfse.Internal.lzfse_history_set;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InternalTest {

    @Test
    void __builtin_clz() {
        for (int i = 0; i < Integer.SIZE; i++) {
            assertEquals(i, Internal.__builtin_clz(-1 >>> i));
        }
    }

    @Test
    void __builtin_ctzl() {
        for (int i = 0; i < Long.SIZE; i++) {
            assertEquals(i, Internal.__builtin_ctzl(-1L >>> i));
        }
    }

    @Test
    void __builtin_ctzll() {
        for (int i = 0; i < Long.SIZE; i++) {
            assertEquals(i, Internal.__builtin_ctzll(1L << i));
        }
    }

    @Test
    void load2() {
        final ByteBuffer buffer = LittleEndianByteBuffer.wrap(new byte[]{0, 1, 2, 3, 4, 5, 6, 7});
        assertEquals(256, Internal.load2(buffer));
        assertEquals(2, buffer.position());
    }

    @Test
    void load4() {
        final ByteBuffer buffer = LittleEndianByteBuffer.wrap(new byte[]{0, 1, 2, 3, 4, 5, 6, 7});
        assertEquals(50462976, Internal.load4(buffer));
        assertEquals(4, buffer.position());
    }

    @Test
    void load8() {
        final ByteBuffer buffer = LittleEndianByteBuffer.wrap(new byte[]{0, 1, 2, 3, 4, 5, 6, 7});
        assertEquals(506097522914230528L, Internal.load8(buffer));
        assertEquals(8, buffer.position());
    }

    @Test
    void store2() {
        final ByteBuffer buffer = LittleEndianByteBuffer.allocate(2);
        Internal.store2(buffer, (short) 256);
        assertEquals(2, buffer.position());
        assertEquals(0, buffer.get(0));
        assertEquals(1, buffer.get(1));
    }

    @Test
    void store4() {
        final ByteBuffer buffer = LittleEndianByteBuffer.allocate(4);
        Internal.store4(buffer, 50462976);
        assertEquals(4, buffer.position());
        assertEquals(0, buffer.get(0));
        assertEquals(1, buffer.get(1));
        assertEquals(2, buffer.get(2));
        assertEquals(3, buffer.get(3));
    }

    @Test
    void store8() {
        final ByteBuffer buffer = LittleEndianByteBuffer.allocate(8);
        Internal.store8(buffer, 506097522914230528L);
        assertEquals(8, buffer.position());
        assertEquals(0, buffer.get(0));
        assertEquals(1, buffer.get(1));
        assertEquals(2, buffer.get(2));
        assertEquals(3, buffer.get(3));
        assertEquals(4, buffer.get(4));
        assertEquals(5, buffer.get(5));
        assertEquals(6, buffer.get(6));
        assertEquals(7, buffer.get(7));
    }

    @Test
    void copy8() {
        final ByteBuffer src = LittleEndianByteBuffer.wrap(new byte[]{0, 1, 2, 3, 4, 5, 6, 7});
        final ByteBuffer dst = LittleEndianByteBuffer.allocate(8);
        Internal.copy8(dst, src);
        assertEquals(8, src.position());
        assertEquals(8, dst.position());
        assertEquals(src.getLong(0), dst.getLong(0));
    }

    @Test
    void copy16() {
        final ByteBuffer src = LittleEndianByteBuffer.wrap(new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15});
        final ByteBuffer dst = LittleEndianByteBuffer.allocate(16);
        Internal.copy16(dst, src);
        assertEquals(16, src.position());
        assertEquals(16, dst.position());
        assertEquals(src.getLong(0), dst.getLong(0));
        assertEquals(src.getLong(8), dst.getLong(8));
    }

    @Test
    void extract() {
        assertThrows(AssertionError.class, () -> Internal.extract(0, Long.SIZE + 1, 0));
        assertThrows(AssertionError.class, () -> Internal.extract(0, Long.SIZE, -1));
        assertThrows(AssertionError.class, () -> Internal.extract(0, Long.SIZE, 1));
        assertThrows(AssertionError.class, () -> Internal.extract(0, Long.SIZE, Long.SIZE + 1));

        assertEquals(-1, Internal.extract(-1, 0, Long.SIZE));
        assertEquals(4294967294L, Internal.extract(-2, 0, Integer.SIZE));
        assertEquals(4294967295L, Internal.extract(-2, Integer.SIZE, Integer.SIZE));
    }

    @Test
    void insert() {
        assertThrows(AssertionError.class, () -> Internal.insert(0, 0, Long.SIZE + 1, 0));
        assertThrows(AssertionError.class, () -> Internal.insert(0, 0, Long.SIZE, -1));
        assertThrows(AssertionError.class, () -> Internal.insert(0, 0, Long.SIZE, 1));
        assertThrows(AssertionError.class, () -> Internal.insert(0, 0, Long.SIZE, Long.SIZE + 1));

        // no inserts
        assertEquals(-1, Internal.insert(-1, 123, 0, Long.SIZE));

        assertEquals(-9223372036854775807L, Internal.insert(-1, +1, 0, Long.SIZE - 1));
        assertEquals(+9223372036854775807L, Internal.insert(+1, -1, 0, Long.SIZE - 1));
        assertEquals(-4294967173L, Internal.insert(-1, +123, 0, Integer.SIZE));
        assertEquals(+4294967173L, Internal.insert(+1, -123, 0, Integer.SIZE));
        assertEquals(-4294967295L, Internal.insert(-1, +1, 0, Integer.SIZE));
        assertEquals(+4294967295L, Internal.insert(+1, -1, 0, Integer.SIZE));
    }

    @Test
    void lzfse_history_set() {
        lzfse_history_set history1 = new lzfse_history_set();
        lzfse_history_set history2 = new lzfse_history_set();

        Arrays.fill(history1.pos, 1);
        Arrays.fill(history1.value, 2);

        assertNotEquals(history1.pos[0], history2.pos[0]);
        assertNotEquals(history1.value[0], history2.value[0]);

        history2.set(history1);

        assertArrayEquals(history1.pos, history2.pos);
        assertArrayEquals(history1.value, history2.value);
    }
}