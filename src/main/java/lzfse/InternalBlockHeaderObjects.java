package lzfse;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

import static lzfse.Internal.LZFSE_ENCODE_D_SYMBOLS;
import static lzfse.Internal.LZFSE_ENCODE_LITERAL_SYMBOLS;
import static lzfse.Internal.LZFSE_ENCODE_L_SYMBOLS;
import static lzfse.Internal.LZFSE_ENCODE_M_SYMBOLS;

class InternalBlockHeaderObjects {
    private InternalBlockHeaderObjects() {
    }

    static final int LZFSE_NO_BLOCK_MAGIC             = 0x00000000; // 0    (invalid)
    static final int LZFSE_ENDOFSTREAM_BLOCK_MAGIC    = 0x24787662; // bvx$ (end of stream)
    static final int LZFSE_UNCOMPRESSED_BLOCK_MAGIC   = 0x2d787662; // bvx- (raw data)
    static final int LZFSE_COMPRESSEDV1_BLOCK_MAGIC   = 0x31787662; // bvx1 (lzfse compressed, uncompressed tables)
    static final int LZFSE_COMPRESSEDV2_BLOCK_MAGIC   = 0x32787662; // bvx2 (lzfse compressed, compressed tables)
    static final int LZFSE_COMPRESSEDLZVN_BLOCK_MAGIC = 0x6e787662; // bvxn (lzvn compressed)

    /*! @abstract Uncompressed block header in encoder stream. */
    static class uncompressed_block_header {
        static final int BYTES = Integer.BYTES * 2;

        //  Magic number, always LZFSE_UNCOMPRESSED_BLOCK_MAGIC.
        /*uint32_t*/ int magic;
        //  Number of raw bytes in block.
        /*uint32_t*/ int n_raw_bytes;

        public uncompressed_block_header(int magic, int n_raw_bytes) {
            this.magic = magic;
            this.n_raw_bytes = n_raw_bytes;
        }

        ByteBuffer toByteBuffer() {
            return LittleEndianByteBuffer.allocate(BYTES)
                    .putInt(magic)
                    .putInt(n_raw_bytes)
                    .flip();
        }
    }

    /*! @abstract Compressed block header with uncompressed tables. */
    static class lzfse_compressed_block_header_v1 {
        static final int BYTES = 772;

        //  Magic number, always LZFSE_COMPRESSEDV1_BLOCK_MAGIC.
        /*uint32_t*/ int magic;
        //  Number of decoded (output) bytes in block.
        /*uint32_t*/ int n_raw_bytes;
        //  Number of encoded (source) bytes in block.
        /*uint32_t*/ int n_payload_bytes;
        //  Number of literal bytes output by block (*not* the number of literals).
        /*uint32_t*/ int n_literals;
        //  Number of matches in block (which is also the number of literals).
        /*uint32_t*/ int n_matches;
        //  Number of bytes used to encode literals.
        /*uint32_t*/ int n_literal_payload_bytes;
        //  Number of bytes used to encode matches.
        /*uint32_t*/ int n_lmd_payload_bytes;

        //  Final encoder states for the block, which will be the initial states for
        //  the decoder:
        //  Final accum_nbits for literals stream.
        int literal_bits;
        //  There are four interleaved streams of literals, so there are four final
        //  states.
        /*uint16_t*/ short[] literal_state = new short[4];
        //  accum_nbits for the l, m, d stream.
        int lmd_bits;
        //  Final L (literal length) state.
        /*uint16_t*/ short l_state;
        //  Final M (match length) state.
        /*uint16_t*/ short m_state;
        //  Final D (match distance) state.
        /*uint16_t*/ short d_state;

        //  Normalized frequency tables for each stream. Sum of values in each
        //  array is the number of states.
        /*uint16_t*/ short[] l_freq       = new short[LZFSE_ENCODE_L_SYMBOLS];
        /*uint16_t*/ short[] m_freq       = new short[LZFSE_ENCODE_M_SYMBOLS];
        /*uint16_t*/ short[] d_freq       = new short[LZFSE_ENCODE_D_SYMBOLS];
        /*uint16_t*/ short[] literal_freq = new short[LZFSE_ENCODE_LITERAL_SYMBOLS];

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            lzfse_compressed_block_header_v1 that = (lzfse_compressed_block_header_v1) o;
            return magic == that.magic && n_raw_bytes == that.n_raw_bytes && n_payload_bytes == that.n_payload_bytes && n_literals == that.n_literals && n_matches == that.n_matches && n_literal_payload_bytes == that.n_literal_payload_bytes && n_lmd_payload_bytes == that.n_lmd_payload_bytes && literal_bits == that.literal_bits && lmd_bits == that.lmd_bits && l_state == that.l_state && m_state == that.m_state && d_state == that.d_state && Arrays.equals(literal_state, that.literal_state) && Arrays.equals(l_freq, that.l_freq) && Arrays.equals(m_freq, that.m_freq) && Arrays.equals(d_freq, that.d_freq) && Arrays.equals(literal_freq, that.literal_freq);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(magic, n_raw_bytes, n_payload_bytes, n_literals, n_matches, n_literal_payload_bytes, n_lmd_payload_bytes, literal_bits, lmd_bits, l_state, m_state, d_state);
            result = 31 * result + Arrays.hashCode(literal_state);
            result = 31 * result + Arrays.hashCode(l_freq);
            result = 31 * result + Arrays.hashCode(m_freq);
            result = 31 * result + Arrays.hashCode(d_freq);
            result = 31 * result + Arrays.hashCode(literal_freq);
            return result;
        }

        static lzfse_compressed_block_header_v1 fromByteBuffer(final ByteBuffer buffer) {
            final ByteBuffer duplicate = LittleEndianByteBuffer.duplicate(buffer);
            final lzfse_compressed_block_header_v1 header = new lzfse_compressed_block_header_v1();
            header.magic = duplicate.getInt();
            header.n_raw_bytes = duplicate.getInt();
            header.n_payload_bytes = duplicate.getInt();
            header.n_literals = duplicate.getInt();
            header.n_matches = duplicate.getInt();
            header.n_literal_payload_bytes = duplicate.getInt();
            header.n_lmd_payload_bytes = duplicate.getInt();
            header.literal_bits = duplicate.getInt();
            for (int i = 0; i < header.literal_state.length; i++) {
                header.literal_state[i] = duplicate.getShort();
            }
            header.lmd_bits = duplicate.getInt();
            header.l_state = duplicate.getShort();
            header.m_state = duplicate.getShort();
            header.d_state = duplicate.getShort();
            for (int i = 0; i < header.l_freq.length; i++) {
                header.l_freq[i] = duplicate.getShort();
            }
            for (int i = 0; i < header.m_freq.length; i++) {
                header.m_freq[i] = duplicate.getShort();
            }
            for (int i = 0; i < header.d_freq.length; i++) {
                header.d_freq[i] = duplicate.getShort();
            }
            for (int i = 0; i < header.literal_freq.length; i++) {
                header.literal_freq[i] = duplicate.getShort();
            }
            assert (duplicate.position() == BYTES - 2);// 2 alignment bytes?
            return header;
        }
    }

    /*! @abstract Compressed block header with compressed tables. Note that because
     *  freq[] is compressed, the structure-as-stored-in-the-stream is *truncated*;
     *  we only store the used bytes of freq[]. This means that some extra care must
     *  be taken when reading one of these headers from the stream. */
    static class lzfse_compressed_block_header_v2 {
        public static final int BYTES = 752;

        static final int OFFSET_OF_FREQ = 32;

        //  Magic number, always LZFSE_COMPRESSEDV2_BLOCK_MAGIC.
        /*uint32_t*/ int    magic;
        //  Number of decoded (output) bytes in block.
        /*uint32_t*/ int    n_raw_bytes;
        //  The fields n_payload_bytes ... d_state from the
        //  lzfse_compressed_block_header_v1 object are packed into three 64-bit
        //  fields in the compressed header, as follows:
        //
        //    offset  bits  value
        //    0       20    n_literals
        //    20      20    n_literal_payload_bytes
        //    40      20    n_matches
        //    60      3     literal_bits
        //    63      1     --- unused ---
        //
        //    0       10    literal_state[0]
        //    10      10    literal_state[1]
        //    20      10    literal_state[2]
        //    30      10    literal_state[3]
        //    40      20    n_lmd_payload_bytes
        //    60      3     lmd_bits
        //    63      1     --- unused ---
        //
        //    0       32    header_size (total header size in bytes; this does not
        //                  correspond to a field in the uncompressed header version,
        //                  but is required; we wouldn't know the size of the
        //                  compresssed header otherwise.
        //    32      10    l_state
        //    42      10    m_state
        //    52      10    d_state
        //    62      2     --- unused ---
        /*uint64_t*/ long[] packed_fields = new long[3];
        //  Variable size freq tables, using a Huffman-style fixed encoding.
        //  Size allocated here is an upper bound (all values stored on 16 bits).
        /*uint8_t*/  byte[] freq          = new byte[2 * (LZFSE_ENCODE_L_SYMBOLS + LZFSE_ENCODE_M_SYMBOLS +
                LZFSE_ENCODE_D_SYMBOLS + LZFSE_ENCODE_LITERAL_SYMBOLS)];

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            lzfse_compressed_block_header_v2 that = (lzfse_compressed_block_header_v2) o;
            return magic == that.magic && n_raw_bytes == that.n_raw_bytes && Arrays.equals(packed_fields, that.packed_fields) && Arrays.equals(freq, that.freq);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(magic, n_raw_bytes);
            result = 31 * result + Arrays.hashCode(packed_fields);
            result = 31 * result + Arrays.hashCode(freq);
            return result;
        }

        static ByteBuffer toByteBuffer(final lzfse_compressed_block_header_v2 header) {
            final ByteBuffer buffer = LittleEndianByteBuffer.allocate(BYTES);
            buffer.putInt(header.magic);
            buffer.putInt(header.n_raw_bytes);
            for (int i = 0; i < header.packed_fields.length; i++) {
                buffer.putLong(header.packed_fields[i]);
            }
            buffer.put(header.freq);
            assert (buffer.position() == BYTES);
            return buffer;
        }

        static lzfse_compressed_block_header_v2 fromByteBuffer(final ByteBuffer buffer) {
            final ByteBuffer duplicate = LittleEndianByteBuffer.duplicate(buffer);
            final lzfse_compressed_block_header_v2 header = new lzfse_compressed_block_header_v2();
            header.magic = duplicate.getInt();
            header.n_raw_bytes = duplicate.getInt();
            for (int i = 0; i < header.packed_fields.length; i++) {
                header.packed_fields[i] = duplicate.getLong();
            }
            duplicate.get(header.freq);
            assert (duplicate.position() == BYTES);//TODO remaining? and what if the buffer is bigger?
            return header;
        }
    }// __attribute__((__packed__, __aligned__(1)))


    /*! @abstract LZVN compressed block header. */
    static class lzvn_compressed_block_header {
        static final int BYTES = Integer.BYTES * 3;

        //  Magic number, always LZFSE_COMPRESSEDLZVN_BLOCK_MAGIC.
        /*uint32_t*/ int magic;
        //  Number of decoded (output) bytes.
        /*uint32_t*/ int n_raw_bytes;
        //  Number of encoded (source) bytes.
        /*uint32_t*/ int n_payload_bytes;

        ByteBuffer toByteBuffer() {
            return LittleEndianByteBuffer.allocate(BYTES)
                    .putInt(magic)
                    .putInt(n_raw_bytes)
                    .putInt(n_payload_bytes)
                    .flip();
        }
    }
}

