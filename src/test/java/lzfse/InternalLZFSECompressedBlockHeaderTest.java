package lzfse;

import lzfse.InternalBlockHeaderObjects.lzfse_compressed_block_header_v1;
import lzfse.InternalBlockHeaderObjects.lzfse_compressed_block_header_v2;
import lzfse.InternalBlockHeaderObjects.lzvn_compressed_block_header;
import lzfse.InternalBlockHeaderObjects.uncompressed_block_header;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static lzfse.InternalBlockHeaderObjects.LZFSE_COMPRESSEDV1_BLOCK_MAGIC;
import static lzfse.Internal.LZFSE_ENCODE_D_STATES;
import static lzfse.Internal.LZFSE_ENCODE_D_SYMBOLS;
import static lzfse.Internal.LZFSE_ENCODE_LITERAL_SYMBOLS;
import static lzfse.Internal.LZFSE_ENCODE_L_STATES;
import static lzfse.Internal.LZFSE_ENCODE_L_SYMBOLS;
import static lzfse.Internal.LZFSE_ENCODE_M_STATES;
import static lzfse.Internal.LZFSE_ENCODE_M_SYMBOLS;
import static lzfse.Internal.LZFSE_LITERALS_PER_BLOCK;
import static lzfse.Internal.LZFSE_MATCHES_PER_BLOCK;
import static lzfse.InternalBlockHeaderObjects.LZFSE_UNCOMPRESSED_BLOCK_MAGIC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class InternalLZFSECompressedBlockHeaderTest {
    @Test
    void lzfse_check_block_header_v1() {
        final lzfse_compressed_block_header_v1 header = create_valid_lzfse_compressed_block_header_v1();

        assertEquals(0, Internal.lzfse_check_block_header_v1(header));
    }

    @Test
    void uncompressed_block_header() {
        final int n_raw_bytes = 4;
        final uncompressed_block_header header = new uncompressed_block_header(LZFSE_UNCOMPRESSED_BLOCK_MAGIC, n_raw_bytes);
        final ByteBuffer buffer = header.toByteBuffer();

        assertEquals(uncompressed_block_header.BYTES, buffer.capacity());
        assertEquals(ByteOrder.LITTLE_ENDIAN, buffer.order());
        assertEquals(LZFSE_UNCOMPRESSED_BLOCK_MAGIC, buffer.getInt(0));
        assertEquals(n_raw_bytes, buffer.getInt(4));
    }

    @Test
    void lzfse_compressed_block_header_v1() {
        final lzfse_compressed_block_header_v1 header = lzfse_compressed_block_header_v1.fromByteBuffer(create_valid_lzfse_compressed_block_header_v1_bytebuffer());
        final lzfse_compressed_block_header_v1 header2 = create_valid_lzfse_compressed_block_header_v1();
        final lzfse_compressed_block_header_v1 empty_header = new lzfse_compressed_block_header_v1();
        assertNotEquals(header, empty_header);
        assertEquals(header, header2);
    }

    @Test
    void lzfse_compressed_block_header_v2() {
        ByteBuffer buffer1 = LittleEndianByteBuffer.allocate(lzfse_compressed_block_header_v2.BYTES);
        for (int i = 0; i < lzfse_compressed_block_header_v2.BYTES; i++) {
            buffer1.put((byte) i);
        }
        buffer1.position(0);

        final lzfse_compressed_block_header_v2 header1 = lzfse_compressed_block_header_v2.fromByteBuffer(buffer1);

        final ByteBuffer buffer2 = lzfse_compressed_block_header_v2.toByteBuffer(header1);
        buffer2.position(0);
        assertEquals(buffer1, buffer2);

        final lzfse_compressed_block_header_v2 header2 = lzfse_compressed_block_header_v2.fromByteBuffer(buffer2);

        assertEquals(header1, header2);
    }

    @Test
    void lzvn_compressed_block_header() {
        final int magic = 1;
        final int n_raw_bytes = 2;
        final int n_payload_bytes = 3;

        final lzvn_compressed_block_header header = new lzvn_compressed_block_header();
        header.magic = magic;
        header.n_raw_bytes = n_raw_bytes;
        header.n_payload_bytes = n_payload_bytes;

        final ByteBuffer buffer = header.toByteBuffer();

        assertEquals(magic, buffer.getInt());
        assertEquals(n_raw_bytes, buffer.getInt());
        assertEquals(n_payload_bytes, buffer.getInt());
    }

    private static lzfse_compressed_block_header_v1 create_valid_lzfse_compressed_block_header_v1() {
        final short short_one = (short) 1;
        final lzfse_compressed_block_header_v1 header = new lzfse_compressed_block_header_v1();
        header.magic = LZFSE_COMPRESSEDV1_BLOCK_MAGIC;
        header.n_raw_bytes = 0;
        header.n_payload_bytes = 0;
        header.n_literals = LZFSE_LITERALS_PER_BLOCK;
        header.n_matches = LZFSE_MATCHES_PER_BLOCK;
        header.n_literal_payload_bytes = 0;
        header.n_lmd_payload_bytes = 0;
        header.literal_bits = 0;
        Arrays.fill(header.literal_state, short_one);
        header.lmd_bits = 0;
        header.l_state = LZFSE_ENCODE_L_STATES - 1;
        header.m_state = LZFSE_ENCODE_M_STATES - 1;
        header.d_state = LZFSE_ENCODE_D_STATES - 1;
        Arrays.fill(header.l_freq, short_one);
        Arrays.fill(header.m_freq, short_one);
        Arrays.fill(header.d_freq, short_one);
        Arrays.fill(header.literal_freq, short_one);
        return header;
    }

    private static ByteBuffer create_valid_lzfse_compressed_block_header_v1_bytebuffer() {
        ByteBuffer buffer = LittleEndianByteBuffer.allocate(lzfse_compressed_block_header_v1.BYTES);
        final short short_one = (short) 1;
        buffer.putInt(LZFSE_COMPRESSEDV1_BLOCK_MAGIC);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putInt(LZFSE_LITERALS_PER_BLOCK);
        buffer.putInt(LZFSE_MATCHES_PER_BLOCK);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putInt(0);
        for (int i = 0; i < 4; i++) {// literal_state
            buffer.putShort(short_one);
        }
        buffer.putInt(0);
        buffer.putShort((short) (LZFSE_ENCODE_L_STATES - 1));
        buffer.putShort((short) (LZFSE_ENCODE_M_STATES - 1));
        buffer.putShort((short) (LZFSE_ENCODE_D_STATES - 1));
        for (int i = 0; i < LZFSE_ENCODE_L_SYMBOLS; i++) {// l_freq
            buffer.putShort(short_one);
        }
        for (int i = 0; i < LZFSE_ENCODE_M_SYMBOLS; i++) {// m_freq
            buffer.putShort(short_one);
        }
        for (int i = 0; i < LZFSE_ENCODE_D_SYMBOLS; i++) {// d_freq
            buffer.putShort(short_one);
        }
        for (int i = 0; i < LZFSE_ENCODE_LITERAL_SYMBOLS; i++) {// literal_freq
            buffer.putShort(short_one);
        }

        return buffer.position(0);
    }
}
