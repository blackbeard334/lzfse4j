package lzfse;

import lzfse.InternalBlockHeaderObjects.lzfse_compressed_block_header_v1;
import lzfse.InternalBlockHeaderObjects.lzfse_compressed_block_header_v2;

import java.nio.ByteBuffer;

import static lzfse.Internal.LZFSE_ENCODE_D_SYMBOLS;
import static lzfse.Internal.LZFSE_ENCODE_LITERAL_SYMBOLS;
import static lzfse.Internal.LZFSE_ENCODE_L_SYMBOLS;
import static lzfse.Internal.LZFSE_ENCODE_MAX_M_VALUE;
import static lzfse.Internal.LZFSE_ENCODE_M_SYMBOLS;
import static lzfse.InternalBlockHeaderObjects.LZFSE_COMPRESSEDV2_BLOCK_MAGIC;
import static lzfse.Tunables.LZFSE_ENCODE_HASH_BITS;

public class EncodeBase {
    private EncodeBase() {
    }

    /*! @abstract Get hash in range [0, LZFSE_ENCODE_HASH_VALUES-1] from 4 bytes in X. */
    static int/*uint32_t*/ hashX(int/*uint32_t*/ x) {
        return ((int) (x * 2654435761L)) >>>
                (32 - LZFSE_ENCODE_HASH_BITS); // Knuth multiplicative hash
    }

    /*! @abstract Return value with all 0 except nbits<=32 unsigned bits from V
     * at bit offset OFFSET.
     * V is assumed to fit on nbits bits. */
    static long/*uint64_t*/ setField(int/*uint32_t*/ v, int offset, int nbits) {
        assert (offset + nbits < 64 && offset >= 0 && nbits <= 32);
        assert (nbits == 32 || (v < (1L << nbits)));
        return ((long/*uint64_t*/) v << (long/*uint64_t*/) offset);
    }

    /*! @abstract Encode all fields, except freq, from a
     * lzfse_compressed_block_header_v1 to a lzfse_compressed_block_header_v2.
     * All but the header_size and freq fields of the output are modified. */
    static void
    lzfse_encode_v1_state(lzfse_compressed_block_header_v2 out,
                          final lzfse_compressed_block_header_v1 in) {
        out.magic = LZFSE_COMPRESSEDV2_BLOCK_MAGIC;
        out.n_raw_bytes = in.n_raw_bytes;

        // Literal state
        out.packed_fields[0] = setField(in.n_literals, 0, 20) |
                setField(in.n_literal_payload_bytes, 20, 20) |
                setField(in.n_matches, 40, 20) |
                setField(7 + in.literal_bits, 60, 3);
        out.packed_fields[1] = setField(in.literal_state[0], 0, 10) |
                setField(in.literal_state[1], 10, 10) |
                setField(in.literal_state[2], 20, 10) |
                setField(in.literal_state[3], 30, 10) |
                setField(in.n_lmd_payload_bytes, 40, 20) |
                setField(7 + in.lmd_bits, 60, 3);
        out.packed_fields[2] = out.packed_fields[2] // header_size already stored in v[2]
                | setField(in.l_state, 32, 10) | setField(in.m_state, 42, 10) |
                setField(in.d_state, 52, 10);
    }

    /*! @abstract Encode an entry value in a freq table. Return bits, and sets
     * *nbits to the number of bits to serialize. */
    static int/*uint32_t*/ lzfse_encode_v1_freq_value(int value, int[] nbits) {
        // Fixed Huffman code, bits are read from LSB.
        // Note that we rely on the position of the first '0' bit providing the number
        // of bits.
        switch (value) {
            case 0:
                nbits[0] = 2;
                return 0; //    0.0
            case 1:
                nbits[0] = 2;
                return 2; //    1.0
            case 2:
                nbits[0] = 3;
                return 1; //   0.01
            case 3:
                nbits[0] = 3;
                return 5; //   1.01
            case 4:
                nbits[0] = 5;
                return 3; // 00.011
            case 5:
                nbits[0] = 5;
                return 11; // 01.011
            case 6:
                nbits[0] = 5;
                return 19; // 10.011
            case 7:
                nbits[0] = 5;
                return 27; // 11.011
            default:
                break;
        }
        if (value < 24) {
            nbits[0] = 8;                  // 4+4
            return 7 + ((value - 8) << 4); // xxxx.0111
        }
        // 24..1047
        nbits[0] = 14;                   // 4+10
        return ((value - 24) << 4) + 15; // xxxxxxxxxx.1111
    }

    /*! @abstract Encode all tables from a lzfse_compressed_block_header_v1
     * to a lzfse_compressed_block_header_v2.
     * Only the header_size and freq fields of the output are modified.
     * @return Size of the lzfse_compressed_block_header_v2 */
    static int/*size_t*/
    lzfse_encode_v1_freq_table(lzfse_compressed_block_header_v2 out,
                               final lzfse_compressed_block_header_v1 in) {
        int/*uint32_t*/ accum = 0;
        int accum_nbits = 0;
        final short/*uint16_t*/[] src = Util.mergeArrays(in.l_freq, in.m_freq, in.d_freq, in.literal_freq); // first value of first table (struct
        // will not be modified, so this code
        // will remain valid)
        ByteBuffer/*uint8_t*/ dst = ByteBuffer.wrap(out.freq);
        for (int i = 0; i < LZFSE_ENCODE_L_SYMBOLS + LZFSE_ENCODE_M_SYMBOLS +
                LZFSE_ENCODE_D_SYMBOLS + LZFSE_ENCODE_LITERAL_SYMBOLS;
             i++) {
            // Encode one value to accum
            int[] nbits = {0};
            int/*uint32_t*/ bits = lzfse_encode_v1_freq_value(src[i], nbits);
            assert (bits < (1 << nbits[0]));
            accum |= bits << accum_nbits;
            accum_nbits += nbits[0];

            // Store bytes from accum to output buffer
            while (accum_nbits >= 8) {
                dst.put((byte/*uint8_t*/) (accum & 0xff));
                accum >>= 8;
                accum_nbits -= 8;
            }
        }
        // Store final byte if needed
        if (accum_nbits > 0) {
            dst.put((byte/*uint8_t*/) (accum & 0xff));
        }

        // Return final size of out
        int/*uint32_t*/ header_size = dst.position() + 2 * Integer.BYTES + 3 * Long.BYTES;//(uint32_t)(dst - (uint8_t *)out);
        /*
         TODO not sure I understand this yet, but we are calculating the distance from
          the START of the object, instaed of the start of the array!?
          so magic + n_raw_bytes + packed_fields + offset within freq[]
        */
        out.packed_fields[0] = 0;
        out.packed_fields[1] = 0;
        out.packed_fields[2] = setField(header_size, 0, 32);

        return header_size;
    }

    // We need to limit forward match length to make sure it won't split into a too
    // large number of LMD.
    // The limit itself is quite large, so it doesn't really impact compression
    // ratio.
    // The matches may still be expanded backwards by a few bytes, so the final
    // length may be greater than this limit, which is OK.
    static final int LZFSE_ENCODE_MAX_MATCH_LENGTH = (100 * LZFSE_ENCODE_MAX_M_VALUE);
}
