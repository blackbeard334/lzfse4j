package lzfse;

import lzfse.InternalBlockHeaderObjects.lzfse_compressed_block_header_v1;

import java.nio.ByteBuffer;

import static lzfse.FSE.fse_check_freq;
import static lzfse.InternalBlockHeaderObjects.LZFSE_COMPRESSEDV1_BLOCK_MAGIC;
import static lzfse.Tunables.LZFSE_ENCODE_HASH_BITS;
import static lzfse.Tunables.LZFSE_ENCODE_HASH_WIDTH;

public final class Internal {
    private Internal() {
    }

    static int __builtin_clz(/*unsigned*/ int val) {
        return Integer.numberOfLeadingZeros(val);
    }

    static int __builtin_ctzl(/*unsigned*/ long val) {
        return Long.numberOfLeadingZeros(val);
    }

    static int __builtin_ctzll(long/*uint64_t*/ val) {
        return Long.numberOfTrailingZeros(val);
    }

    //  Throughout LZFSE we refer to "L", "M" and "D"; these will always appear as
    //  a triplet, and represent a "usual" LZ-style literal and match pair.  "L"
    //  is the number of literal bytes, "M" is the number of match bytes, and "D"
    //  is the match "distance"; the distance in bytes between the current pointer
    //  and the start of the match.
    static final int LZFSE_ENCODE_HASH_VALUES     = (1 << LZFSE_ENCODE_HASH_BITS);
    static final int LZFSE_ENCODE_L_SYMBOLS       = 20;
    static final int LZFSE_ENCODE_M_SYMBOLS       = 20;
    static final int LZFSE_ENCODE_D_SYMBOLS       = 64;
    static final int LZFSE_ENCODE_LITERAL_SYMBOLS = 256;
    static final int LZFSE_ENCODE_L_STATES        = 64;
    static final int LZFSE_ENCODE_M_STATES        = 64;
    static final int LZFSE_ENCODE_D_STATES        = 256;
    static final int LZFSE_ENCODE_LITERAL_STATES  = 1024;
    static final int LZFSE_MATCHES_PER_BLOCK      = 10000;
    static final int LZFSE_LITERALS_PER_BLOCK     = (4 * LZFSE_MATCHES_PER_BLOCK);
//    static final int LZFSE_DECODE_LITERALS_PER_BLOCK = (4 * LZFSE_DECODE_MATCHES_PER_BLOCK);

    //  LZFSE internal status. These values are used by internal LZFSE routines
    //  as return codes.  There should not be any good reason to change their
    //  values; it is plausible that additional codes might be added in the
    //  future.
    static final int LZFSE_STATUS_OK        = 0;
    static final int LZFSE_STATUS_SRC_EMPTY = -1;
    static final int LZFSE_STATUS_DST_FULL  = -2;
    static final int LZFSE_STATUS_ERROR     = -3;

//  Type representing an offset between elements in a buffer. On 64-bit
//  systems, this is stored in a 64-bit container to avoid extra sign-
//  extension operations in addressing arithmetic, but the value is always
//  representable as a 32-bit signed value in LZFSE's usage.
//            #if defined(_M_AMD64) || defined(__x86_64__) || defined(__arm64__)
//    typedef int64_t lzfse_offset;
//#else
//    typedef int32_t lzfse_offset;
//#endif           //TODO offsets are different per architecture...is this important for java?

    /*! @abstract History table set. Each line of the history table represents a set
     *  of candidate match locations, each of which begins with four bytes with the
     *  same hash. The table contains not only the positions, but also the first
     *  four bytes at each position. This doubles the memory footprint of the
     *  table, but allows us to quickly eliminate false-positive matches without
     *  doing any pointer chasing and without pulling in any additional cachelines.
     *  This provides a large performance win in practice. */
    static class lzfse_history_set {
                    final int[] pos   = new int[LZFSE_ENCODE_HASH_WIDTH];
        /*uint32_t*/final int[] value = new int[LZFSE_ENCODE_HASH_WIDTH];

        public lzfse_history_set() {
        }

        public lzfse_history_set(final lzfse_history_set src) {
            set(src);
        }

        void set(final lzfse_history_set src) {
            System.arraycopy(src.pos, 0, this.pos, 0, src.pos.length);
            System.arraycopy(src.value, 0, this.value, 0, src.value.length);
        }
    }

    /*! @abstract An lzfse match is a sequence of bytes in the source buffer that
     *  exactly matches an earlier (but possibly overlapping) sequence of bytes in
     *  the same buffer.
     *  @code
     *  exeMPLARYexaMPLE
     *  |  |     | ||-|--- lzfse_match2.length=3
     *  |  |     | ||----- lzfse_match2.pos
     *  |  |     |-|------ lzfse_match1.length=3
     *  |  |     |-------- lzfse_match1.pos
     *  |  |-------------- lzfse_match2.ref
     *  |----------------- lzfse_match1.ref
     *  @endcode
     */
    static class lzfse_match {
        //  Offset of the first byte in the match.
        long pos;
        //  First byte of the source -- the earlier location in the buffer with the
        //  same contents.
        long ref;
        //  Length of the match.
        /*uint32_t*/ int length;

        public lzfse_match() {
        }

        public lzfse_match(long pos, long ref, int length) {
            this.pos = pos;
            this.ref = ref;
            this.length = length;
        }
    }

    // MARK: - LZFSE encode/decode interfaces

//    int lzfse_encode_init(lzfse_encoder_state *s);
//    int lzfse_encode_translate(lzfse_encoder_state *s, lzfse_offset delta);
//    int lzfse_encode_base(lzfse_encoder_state *s);
//    int lzfse_encode_finish(lzfse_encoder_state *s);
//    int lzfse_decode(lzfse_decoder_state *s);

    // MARK: - LZVN encode/decode interfaces

    //  Minimum source buffer size for compression. Smaller buffers will not be
    //  compressed; the lzvn encoder will simply return.
    static final long LZVN_ENCODE_MIN_SRC_SIZE = 8;//((size_t)8)

    //  Maximum source buffer size for compression. Larger buffers will be
    //  compressed partially.
    static final long LZVN_ENCODE_MAX_SRC_SIZE = 0xffffffffL;//  ((size_t)0xffffffffU)

    //  Minimum destination buffer size for compression. No compression will take
    //  place if smaller.
    static final long LZVN_ENCODE_MIN_DST_SIZE = 8;// ((size_t)8)

//    size_t lzvn_decode_scratch_size(void);
//
//    size_t lzvn_encode_scratch_size(void);
//
//    size_t lzvn_encode_buffer(void *__restrict dst, size_t dst_size,
//                          const void *__restrict src, size_t src_size,
//                              void *__restrict work);
//
//    size_t lzvn_decode_buffer(void *__restrict dst, size_t dst_size,
//                          const void *__restrict src, size_t src_size);

    /*! @abstract Signed offset in buffers, stored on either 32 or 64 bits. */
//#if defined(_M_AMD64) || defined(__x86_64__) || defined(__arm64__)
//    typedef int64_t lzvn_offset;
//#else
//    typedef int32_t lzvn_offset;
//#endif

    // MARK: - LZFSE utility functions

    /*! @abstract Load bytes from memory location SRC. */
    static short/*uint16_t*/ load2(final ByteBuffer ptr) {
//        uint16_t data;
//        memcpy(&data, ptr, sizeof data);
//        return data;
        return ptr.getShort();
    }

    static int/*uint32_t*/ load4(final ByteBuffer ptr) {
//        uint32_t data;
//        memcpy(&data, ptr, sizeof data);
//        return data;
        return ptr.getInt();
    }

    static long/*uint64_t*/ load8(final ByteBuffer ptr) {
//        uint64_t data;
//        memcpy(&data, ptr, sizeof data);
//        return data;
        return ptr.getLong();
    }

    /*! @abstract Store bytes to memory location DST. */
    static void store2(ByteBuffer ptr, short/*uint16_t*/ data) {
        ptr.putShort(data);//memcpy(ptr, &data, sizeof data);
    }

    static void store4(ByteBuffer ptr, int/*uint32_t*/ data) {
        ptr.putInt(data);//memcpy(ptr, &data, sizeof data);
    }

    static void store8(ByteBuffer ptr, long/*uint64_t*/ data) {
        ptr.putLong(data);//memcpy(ptr, &data, sizeof data);
    }

    /*! @abstract Load+store bytes from locations SRC to DST. Not intended for use
     * with overlapping buffers. Note that for LZ-style compression, you need
     * copies to behave like naive memcpy( ) implementations do, splatting the
     * leading sequence if the buffers overlap. This copy does not do that, so
     * should not be used with overlapping buffers. */
    static void copy8(ByteBuffer dst, final ByteBuffer src) {
        store8(dst, load8(src));
    }

    static void copy16(ByteBuffer dst, final ByteBuffer src) {
        long/*uint64_t*/ m0 = load8(src);
        long/*uint64_t*/ m1 = load8(src);//load8((const unsigned char *)src + 8);
        store8(dst, m0);
        store8(dst, m1);//store8((unsigned char *)dst + 8, m1);
    }

// ===============================================================
// Bitfield Operations

    /*! @abstract Extracts \p width bits from \p container, starting with \p lsb; if
     * we view \p container as a bit array, we extract \c container[lsb:lsb+width]. */
    static /*uintmax_t*/ long extract(long/*uintmax_t*/ container, long/*unsigned*/ lsb,
                                      long /*unsigned*/ width) {
        final long/*size_t*/ container_width = Long.SIZE;//sizeof container * 8;//TODO we assume uintmax_t is a long long
        assert (lsb < container_width);
        assert (width > 0 && width <= container_width);
        assert (lsb + width <= container_width);
        if (width == container_width)
            return container;
        return (container >>> lsb) & ((/*(uintmax_t)*/ 1L << width) - 1);
    }

    /*! @abstract Inserts \p width bits from \p data into \p container, starting with \p lsb.
     *  Viewed as bit arrays, the operations is:
     * @code
     * container[:lsb] is unchanged
     * container[lsb:lsb+width] <-- data[0:width]
     * container[lsb+width:] is unchanged
     * @endcode
     */
    static/*uintmax_t*/ long insert(long/*uintmax_t*/ container, long/*uintmax_t*/ data, long /*unsigned*/ lsb,
                                    long /*unsigned*/ width) {
        final long/*size_t*/ container_width = Long.SIZE;//sizeof container * 8;//TODO we assume uintmax_t is a long long
        assert (lsb < container_width);
        assert (width > 0 && width <= container_width);
        assert (lsb + width <= container_width);
        if (width == container_width)
            return container;
        /*uintmax_t*/
        long mask = (/*(uintmax_t)*/ 1L << width) - 1;
        return (container & ~(mask << lsb)) | (data & mask) << lsb;
    }

    /*! @abstract Perform sanity checks on the values of lzfse_compressed_block_header_v1.
     * Test that the field values are in the allowed limits, verify that the
     * frequency tables sum to value less than total number of states.
     * @return 0 if all tests passed.
     * @return negative error code with 1 bit set for each failed test. */
    static int lzfse_check_block_header_v1(final lzfse_compressed_block_header_v1 header) {
        int tests_results = 0;
        tests_results =
                tests_results |
                        ((header.magic == LZFSE_COMPRESSEDV1_BLOCK_MAGIC) ? 0 : (1 << 0));
        tests_results =
                tests_results |
                        ((header.n_literals <= LZFSE_LITERALS_PER_BLOCK) ? 0 : (1 << 1));
        tests_results =
                tests_results |
                        ((header.n_matches <= LZFSE_MATCHES_PER_BLOCK) ? 0 : (1 << 2));

        short/*uint16_t*/[] literal_state = new short[4];
        System.arraycopy(header.literal_state, 0, literal_state, 0, 4);//memcpy(literal_state, header->literal_state, sizeof(uint16_t) * 4);

        tests_results =
                tests_results |
                        ((literal_state[0] < LZFSE_ENCODE_LITERAL_STATES) ? 0 : (1 << 3));
        tests_results =
                tests_results |
                        ((literal_state[1] < LZFSE_ENCODE_LITERAL_STATES) ? 0 : (1 << 4));
        tests_results =
                tests_results |
                        ((literal_state[2] < LZFSE_ENCODE_LITERAL_STATES) ? 0 : (1 << 5));
        tests_results =
                tests_results |
                        ((literal_state[3] < LZFSE_ENCODE_LITERAL_STATES) ? 0 : (1 << 6));

        tests_results = tests_results |
                ((header.l_state < LZFSE_ENCODE_L_STATES) ? 0 : (1 << 7));
        tests_results = tests_results |
                ((header.m_state < LZFSE_ENCODE_M_STATES) ? 0 : (1 << 8));
        tests_results = tests_results |
                ((header.d_state < LZFSE_ENCODE_D_STATES) ? 0 : (1 << 9));

        int res;
        res = fse_check_freq(header.l_freq, LZFSE_ENCODE_L_SYMBOLS,
                LZFSE_ENCODE_L_STATES);
        tests_results = tests_results | ((res == 0) ? 0 : (1 << 10));
        res = fse_check_freq(header.m_freq, LZFSE_ENCODE_M_SYMBOLS,
                LZFSE_ENCODE_M_STATES);
        tests_results = tests_results | ((res == 0) ? 0 : (1 << 11));
        res = fse_check_freq(header.d_freq, LZFSE_ENCODE_D_SYMBOLS,
                LZFSE_ENCODE_D_STATES);
        tests_results = tests_results | ((res == 0) ? 0 : (1 << 12));
        res = fse_check_freq(header.literal_freq, LZFSE_ENCODE_LITERAL_SYMBOLS,
                LZFSE_ENCODE_LITERAL_STATES);
        tests_results = tests_results | ((res == 0) ? 0 : (1 << 13));

        if (tests_results != 0) {
            return tests_results | 0x80000000; // each 1 bit is a test that failed
            // (except for the sign bit)
        }

        return 0; // OK
    }

    // MARK: - L, M, D encoding constants for LZFSE

    //  Largest encodable L (literal length), M (match length) and D (match
    //  distance) values.
    static final int LZFSE_ENCODE_MAX_L_VALUE = 315;
    static final int LZFSE_ENCODE_MAX_M_VALUE = 2359;
    static final int LZFSE_ENCODE_MAX_D_VALUE = 262139;

    /*! @abstract The L, M, D data streams are all encoded as a "base" value, which is
     * FSE-encoded, and an "extra bits" value, which is the difference between
     * value and base, and is simply represented as a raw bit value (because it
     * is the low-order bits of a larger number, not much entropy can be
     * extracted from these bits by more complex encoding schemes). The following
     * tables represent the number of low-order bits to encode separately and the
     * base values for each of L, M, and D.
     *
     * @note The inverse tables for mapping the other way are significantly larger.
     * Those tables have been split out to lzfse_encode_tables.h in order to keep
     * this file relatively small. */
    /*uint8_t*/static final byte[] l_extra_bits = new byte[/*LZFSE_ENCODE_L_SYMBOLS*/]{
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 3, 5, 8
    };
   static final int[]  l_base_value = new int[/*LZFSE_ENCODE_L_SYMBOLS*/]{
           0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 20, 28, 60
   };
    /*uint8_t*/static final byte[] m_extra_bits = new byte[/*LZFSE_ENCODE_L_SYMBOLS*/]{
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 5, 8, 11
    };
   static final int[]  m_base_value = new int[/*LZFSE_ENCODE_M_SYMBOLS*/]{
           0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 24, 56, 312
   };
    /*uint8_t*/static final byte[] d_extra_bits = new byte[/*LZFSE_ENCODE_L_SYMBOLS*/]{
            0,  0,  0,  0,  1,  1,  1,  1,  2,  2,  2,  2,  3,  3,  3,  3,
            4,  4,  4,  4,  5,  5,  5,  5,  6,  6,  6,  6,  7,  7,  7,  7,
            8,  8,  8,  8,  9,  9,  9,  9,  10, 10, 10, 10, 11, 11, 11, 11,
            12, 12, 12, 12, 13, 13, 13, 13, 14, 14, 14, 14, 15, 15, 15, 15
    };
    static final int[] d_base_value = new int[/*LZFSE_ENCODE_D_SYMBOLS*/]{
            0,      1,      2,      3,     4,     6,     8,     10,    12,    16,
            20,     24,     28,     36,    44,    52,    60,    76,    92,    108,
            124,    156,    188,    220,   252,   316,   380,   444,   508,   636,
            764,    892,    1020,   1276,  1532,  1788,  2044,  2556,  3068,  3580,
            4092,   5116,   6140,   7164,  8188,  10236, 12284, 14332, 16380, 20476,
            24572,  28668,  32764,  40956, 49148, 57340, 65532, 81916, 98300, 114684,
            131068, 163836, 196604, 229372
    };


}
