package lzfse;

import java.nio.ByteBuffer;

import static lzfse.Internal.__builtin_clz;

public class FSE {
    // Mask the NBITS lsb of X. 0 <= NBITS < 64
    static /*uint64_t*/long fse_mask_lsb64(long/*uint64_t*/ x, int/*fse_bit_count*/ nbits) {
        final /*uint64_t*/ long[] mtable = new long[/*65*/]{  //TODO let's hope the jvm turns this into a constant
                0x0000000000000000L, 0x0000000000000001L, 0x0000000000000003L,
                0x0000000000000007L, 0x000000000000000fL, 0x000000000000001fL,
                0x000000000000003fL, 0x000000000000007fL, 0x00000000000000ffL,
                0x00000000000001ffL, 0x00000000000003ffL, 0x00000000000007ffL,
                0x0000000000000fffL, 0x0000000000001fffL, 0x0000000000003fffL,
                0x0000000000007fffL, 0x000000000000ffffL, 0x000000000001ffffL,
                0x000000000003ffffL, 0x000000000007ffffL, 0x00000000000fffffL,
                0x00000000001fffffL, 0x00000000003fffffL, 0x00000000007fffffL,
                0x0000000000ffffffL, 0x0000000001ffffffL, 0x0000000003ffffffL,
                0x0000000007ffffffL, 0x000000000fffffffL, 0x000000001fffffffL,
                0x000000003fffffffL, 0x000000007fffffffL, 0x00000000ffffffffL,
                0x00000001ffffffffL, 0x00000003ffffffffL, 0x00000007ffffffffL,
                0x0000000fffffffffL, 0x0000001fffffffffL, 0x0000003fffffffffL,
                0x0000007fffffffffL, 0x000000ffffffffffL, 0x000001ffffffffffL,
                0x000003ffffffffffL, 0x000007ffffffffffL, 0x00000fffffffffffL,
                0x00001fffffffffffL, 0x00003fffffffffffL, 0x00007fffffffffffL,
                0x0000ffffffffffffL, 0x0001ffffffffffffL, 0x0003ffffffffffffL,
                0x0007ffffffffffffL, 0x000fffffffffffffL, 0x001fffffffffffffL,
                0x003fffffffffffffL, 0x007fffffffffffffL, 0x00ffffffffffffffL,
                0x01ffffffffffffffL, 0x03ffffffffffffffL, 0x07ffffffffffffffL,
                0x0fffffffffffffffL, 0x1fffffffffffffffL, 0x3fffffffffffffffL,
                0x7fffffffffffffffL, 0xffffffffffffffffL,
        };
        return x & mtable[nbits];
    }

    // Mask the NBITS lsb of X. 0 <= NBITS < 32
    static int/*uint32_t*/ fse_mask_lsb32(int/*uint32_t*/ x, int/*fse_bit_count*/ nbits) {
        final int/*uint32_t*/[] mtable = new int[/*33*/]{
                0x0000000000000000, 0x0000000000000001, 0x0000000000000003,
                0x0000000000000007, 0x000000000000000f, 0x000000000000001f,
                0x000000000000003f, 0x000000000000007f, 0x00000000000000ff,
                0x00000000000001ff, 0x00000000000003ff, 0x00000000000007ff,
                0x0000000000000fff, 0x0000000000001fff, 0x0000000000003fff,
                0x0000000000007fff, 0x000000000000ffff, 0x000000000001ffff,
                0x000000000003ffff, 0x000000000007ffff, 0x00000000000fffff,
                0x00000000001fffff, 0x00000000003fffff, 0x00000000007fffff,
                0x0000000000ffffff, 0x0000000001ffffff, 0x0000000003ffffff,
                0x0000000007ffffff, 0x000000000fffffff, 0x000000001fffffff,
                0x000000003fffffff, 0x000000007fffffff, 0x00000000ffffffff,
        };
        return x & mtable[nbits];
    }

    /*! @abstract Select \c nbits at index \c start from \c x.
     *  0 <= start <= start+nbits <= 64 */
    static long/*uint64_t*/ fse_extract_bits64(long/*uint64_t*/ x, int/*fse_bit_count*/ start,
                                               int/*fse_bit_count*/ nbits) {
//#if defined(__GNUC__)
//        // If START and NBITS are constants, map to bit-field extraction instructions
//        if (__builtin_constant_p(start) && __builtin_constant_p(nbits))
//            return (x >>> start) & ((1LLU << nbits) - 1LLU);
//#endif

        // Otherwise, shift and mask
        return fse_mask_lsb64(x >>> start, nbits);
    }

    /*! @abstract Select \c nbits at index \c start from \c x.
     *  0 <= start <= start+nbits <= 32 */
    int/*uint32_t*/ fse_extract_bits32(int/*uint32_t*/ x, int/*fse_bit_count*/ start,
                                       int/*fse_bit_count*/ nbits) {
//#if defined(__GNUC__)
//        // If START and NBITS are constants, map to bit-field extraction instructions
//        if (__builtin_constant_p(start) && __builtin_constant_p(nbits))
//            return (x >>> start) & ((1U << nbits) - 1U);
//#endif

        // Otherwise, shift and mask
        return fse_mask_lsb32(x >>> start, nbits);
    }

    // MARK: - Bit stream

    // I/O streams
    // The streams can be shared between several FSE encoders/decoders, which is why
    // they are not in the state struct

    /*! @abstract Output stream, 64-bit accum. */
    static class fse_out_stream64 {
        long/*uint64_t*/     accum;       // Output bits
        int/*fse_bit_count*/ accum_nbits; // Number of valid bits in ACCUM, other bits are 0
    }

    /*! @abstract Output stream, 32-bit accum. */
    static class fse_out_stream32 {
        int/*uint32_t*/      accum;       // Output bits
        int/*fse_bit_count*/ accum_nbits; // Number of valid bits in ACCUM, other bits are 0
    }

    /*! @abstract Object representing an input stream. */
    static class fse_in_stream64 {
        long/*uint64_t*/     accum;       // Input bits
        int/*fse_bit_count*/ accum_nbits; // Number of valid bits in ACCUM, other bits are 0
    }

    /*! @abstract Object representing an input stream. */
    static class fse_in_stream32 {
        int/*uint32_t*/      accum;       // Input bits
        int/*fse_bit_count*/ accum_nbits; // Number of valid bits in ACCUM, other bits are 0
    }

    /*! @abstract Initialize an output stream object. */
    static void fse_out_init64(fse_out_stream64 s) {
        s.accum = 0;
        s.accum_nbits = 0;
    }

    /*! @abstract Initialize an output stream object. */
    void fse_out_init32(fse_out_stream32 s) {
        s.accum = 0;
        s.accum_nbits = 0;
    }

    /*! @abstract Write full bytes from the accumulator to output buffer, ensuring
     * accum_nbits is in [0, 7].
     * We assume we can write 8 bytes to the output buffer \c (*pbuf[0..7]) in all
     * cases.
     * @note *pbuf is incremented by the number of written bytes. */
    static void fse_out_flush64(fse_out_stream64 s, ByteBuffer/*uint8_t*/ pbuf) {
        final int position = pbuf.position();
        final int/*fse_bit_count*/ nbits = s.accum_nbits & -8; // number of bits written, multiple of 8

        // Write 8 bytes of current accumulator
        pbuf.putLong(s.accum);//memcpy(*pbuf, &(s.accum), 8);
        pbuf.position(position + (nbits >>> 3)); // bytes

        // Update state
        s.accum >>>= nbits; // remove nbits
        s.accum_nbits -= nbits;

        assert (s.accum_nbits >= 0 && s.accum_nbits <= 7);
        assert (s.accum_nbits == 64 || (s.accum >>> s.accum_nbits) == 0);
    }

    /*! @abstract Write full bytes from the accumulator to output buffer, ensuring
     * accum_nbits is in [0, 7].
     * We assume we can write 4 bytes to the output buffer \c (*pbuf[0..3]) in all
     * cases.
     * @note *pbuf is incremented by the number of written bytes. */
    void fse_out_flush32(fse_out_stream32 s, ByteBuffer/*uint8_t*/ pbuf) {
        final int position = pbuf.position();
        final int/*fse_bit_count*/  nbits = s.accum_nbits & -8; // number of bits written, multiple of 8

        // Write 4 bytes of current accumulator
        pbuf.putInt(s.accum);//memcpy(*pbuf, &(s.accum), 4);
        pbuf.position(position + (nbits >>> 3)); // bytes

        // Update state
        s.accum >>>= nbits; // remove nbits
        s.accum_nbits -= nbits;

        assert (s.accum_nbits >= 0 && s.accum_nbits <= 7);
        assert (s.accum_nbits == 32 || (s.accum >>> s.accum_nbits) == 0);
    }

    /*! @abstract Write the last bytes from the accumulator to output buffer,
     * ensuring accum_nbits is in [-7, 0]. Bits are padded with 0 if needed.
     * We assume we can write 8 bytes to the output buffer \c (*pbuf[0..7]) in all
     * cases.
     * @note *pbuf is incremented by the number of written bytes. */
    static void fse_out_finish64(fse_out_stream64 s, ByteBuffer/*uint8_t*/ pbuf) {
        final int position = pbuf.position();
        final int/*fse_bit_count*/  nbits = (s.accum_nbits + 7) & -8; // number of bits written, multiple of 8

        // Write 8 bytes of current accumulator
        pbuf.putLong(s.accum);//memcpy(*pbuf, &(s.accum), 8);
        pbuf.position(position + (nbits >>> 3)); // bytes

        // Update state
        s.accum = 0; // remove nbits
        s.accum_nbits -= nbits;

        assert (s.accum_nbits >= -7 && s.accum_nbits <= 0);
    }

    /*! @abstract Write the last bytes from the accumulator to output buffer,
     * ensuring accum_nbits is in [-7, 0]. Bits are padded with 0 if needed.
     * We assume we can write 4 bytes to the output buffer \c (*pbuf[0..3]) in all
     * cases.
     * @note *pbuf is incremented by the number of written bytes. */
    void fse_out_finish32(fse_out_stream32 s, ByteBuffer/*uint8_t*/ pbuf) {
        final int position = pbuf.position();
        final int/*fse_bit_count*/ nbits = (s.accum_nbits + 7) & -8; // number of bits written, multiple of 8

        // Write 8 bytes of current accumulator
        pbuf.putInt(s.accum);//memcpy(*pbuf, &(s.accum), 8);
        pbuf.position(position + (nbits >>> 3)); // bytes

        // Update state
        s.accum = 0; // remove nbits
        s.accum_nbits -= nbits;

        assert (s.accum_nbits >= -7 && s.accum_nbits <= 0);
    }

    /*! @abstract Accumulate \c n bits \c b to output stream \c s. We \b must have:
     * 0 <= b < 2^n, and N + s->accum_nbits <= 64.
     * @note The caller must ensure out_flush is called \b before the accumulator
     * overflows to more than 64 bits. */
    static void fse_out_push64(fse_out_stream64 s, int/*fse_bit_count*/ n,
                               long/*uint64_t*/ b) {
        s.accum |= b << s.accum_nbits;
        s.accum_nbits += n;

        assert (s.accum_nbits >= 0 && s.accum_nbits <= 64);
        assert (s.accum_nbits == 64 || (s.accum >>> s.accum_nbits) == 0);
    }

    /*! @abstract Accumulate \c n bits \c b to output stream \c s. We \b must have:
     * 0 <= n < 2^n, and n + s->accum_nbits <= 32.
     * @note The caller must ensure out_flush is called \b before the accumulator
     * overflows to more than 32 bits. */
    void fse_out_push32(fse_out_stream32 s, int/*fse_bit_count*/ n,
                        int/*uint32_t*/ b) {
        s.accum |= b << s.accum_nbits;
        s.accum_nbits += n;

        assert (s.accum_nbits >= 0 && s.accum_nbits <= 32);
        assert (s.accum_nbits == 32 || (s.accum >>> s.accum_nbits) == 0);
    }

    //    #if FSE_IOSTREAM_64
    static void DEBUG_CHECK_INPUT_STREAM_PARAMETERS(fse_in_stream64 s) {
        assert (s.accum_nbits >= 56 && s.accum_nbits < 64);
        assert ((s.accum >>> s.accum_nbits) == 0);
    }

    static void DEBUG_CHECK_INPUT_STREAM_PARAMETERS(fse_in_stream32 s) {
        assert (s.accum_nbits >= 56 && s.accum_nbits < 64);
        assert ((s.accum >>> s.accum_nbits) == 0);
    }
//    #else
//            #define DEBUG_CHECK_INPUT_STREAM_PARAMETERS                                    \
//                assert(s->accum_nbits >= 24 && s->accum_nbits < 32);                         \
//                assert((s->accum >>> s->accum_nbits) == 0);
//    #endif

    /*! @abstract   Initialize the fse input stream so that accum holds between 56
     *  and 63 bits. We never want to have 64 bits in the stream, because that allows
     *  us to avoid a special case in the fse_in_pull function (eliminating an
     *  unpredictable branch), while not requiring any additional fse_flush
     *  operations. This is why we have the special case for n == 0 (in which case
     *  we want to load only 7 bytes instead of 8). */
    static int fse_in_checked_init64(fse_in_stream64 s, int/*fse_bit_count*/ n,
                              final ByteBuffer/*uint8_t*/ pbuf,
                              final int/*uint8_t*/ buf_start) {
        if (n != 0) {
            if (pbuf.position() < buf_start + 8)
                return -1; // out of range
            LittleEndianByteBuffer.skip(pbuf, -8);
            s.accum = pbuf.getLong();//memcpy( & (s.accum), *pbuf, 8);
            LittleEndianByteBuffer.skip(pbuf, -8);
            s.accum_nbits = n + 64;
        } else {
            if (pbuf.position() < buf_start + 7)
                return -1; // out of range
            LittleEndianByteBuffer.skip(pbuf, -7);
            s.accum = LittleEndianByteBuffer.getLongBytes(pbuf, 7);//memcpy( & (s.accum), *pbuf, 7);
            LittleEndianByteBuffer.skip(pbuf, -7);
            s.accum &= 0xffffffffffffffL;
            s.accum_nbits = n + 56;
        }

        if ((s.accum_nbits < 56 || s.accum_nbits >= 64) ||
                ((s.accum >>> s.accum_nbits) != 0)) {
            return -1; // the incoming input is wrong (encoder should have zeroed the
            // upper bits)
        }

        return 0; // OK
    }

    /*! @abstract Identical to previous function, but for 32-bit operation
     * (resulting bit count is between 24 and 31 bits). */
    int fse_in_checked_init32(fse_in_stream32 s, int/*fse_bit_count*/ n,
                              final ByteBuffer/*uint8_t*/ pbuf,
                              final ByteBuffer/*uint8_t*/ buf_start) {
        if (n != 0) {
            if (pbuf.position() < buf_start.position() + 4)
                return -1; // out of range
            pbuf.position(pbuf.position() - 4);
            pbuf.putInt(s.accum);//memcpy(&(s->accum), *pbuf, 4);
            s.accum_nbits = n + 32;
        } else {
            if (pbuf.position() < buf_start.position() + 3)
                return -1; // out of range
            pbuf.position(pbuf.position() - 3);
            pbuf.put(Util.itob(s.accum), 0, 3);//memcpy(&(s->accum), *pbuf, 3);
            s.accum &= 0xffffff;
            s.accum_nbits = n + 24;
        }

        if ((s.accum_nbits < 24 || s.accum_nbits >= 32) ||
                ((s.accum >>> s.accum_nbits) != 0)) {
            return -1; // the incoming input is wrong (encoder should have zeroed the
            // upper bits)
        }

        return 0; // OK
    }

    /*! @abstract  Read in new bytes from buffer to ensure that we have a full
     * complement of bits in the stream object (again, between 56 and 63 bits).
     * checking the new value of \c *pbuf remains >= \c buf_start.
     * @return 0 if OK.
     * @return -1 on failure. */
    static int fse_in_checked_flush64(fse_in_stream64 s, final ByteBuffer/*uint8_t*/ pbuf,
                                      final int/*uint8_t*/ buf_start) {
        //  Get number of bits to add to bring us into the desired range.
        final int /*fse_bit_count*/ nbits = (63 - s.accum_nbits) & -8;
        //  Convert bits to bytes and decrement buffer address, then load new data.
        final int newPosition = pbuf.position() - (nbits >>> 3);
        if (newPosition < buf_start) {
            return -1; // out of range
        }
        pbuf.position(newPosition);//*pbuf = buf;
        long/*uint64_t*/ incoming;
        {
            incoming = pbuf.getLong();//memcpy(&incoming, buf, 8);
            pbuf.position(newPosition);
        }
        // Update the state object and verify its validity (in DEBUG).
        s.accum = (s.accum << nbits) | fse_mask_lsb64(incoming, nbits);
        s.accum_nbits += nbits;
        DEBUG_CHECK_INPUT_STREAM_PARAMETERS(s);
        return 0; // OK
    }

    /*! @abstract Identical to previous function (but again, we're only filling
     * a 32-bit field with between 24 and 31 bits). */
    int fse_in_checked_flush32(fse_in_stream32 s, final ByteBuffer /*uint8_t*/ pbuf,
                               final ByteBuffer /*uint8_t*/ buf_start) {
        //  Get number of bits to add to bring us into the desired range.
        final int /*fse_bit_count*/ nbits = (31 - s.accum_nbits) & -8;

        if (nbits > 0) {
            //  Convert bits to bytes and decrement buffer address, then load new data.
            final int newPosition = pbuf.position() - nbits >>> 3;

            if (newPosition < buf_start.position()) {
                return -1; // out of range
            }

            pbuf.position(newPosition);//*pbuf = buf;

            int/*uint32_t*/ incoming;
            {
                incoming = pbuf.getInt();//= *((uint32_t *)buf);
                pbuf.position(newPosition);
            }

            // Update the state object and verify its validity (in DEBUG).
            s.accum = (s.accum << nbits) | fse_mask_lsb32(incoming, nbits);
            s.accum_nbits += nbits;
        }
        DEBUG_CHECK_INPUT_STREAM_PARAMETERS(s);
        return 0; // OK
    }

    /*! @abstract Pull n bits out of the fse stream object. */
    static long/*uint64_t*/ fse_in_pull64(fse_in_stream64 s, int/*fse_bit_count*/ n) {
        assert (n >= 0 && n <= s.accum_nbits);
        s.accum_nbits -= n;
        long/*uint64_t*/ result = s.accum >>> s.accum_nbits;
        s.accum = fse_mask_lsb64(s.accum, s.accum_nbits);
        return result;
    }

    /*! @abstract Pull n bits out of the fse stream object. */
    int/*uint32_t*/ fse_in_pull32(fse_in_stream32 s, int/*fse_bit_count*/ n) {
        assert (n >= 0 && n <= s.accum_nbits);
        s.accum_nbits -= n;
        int/*uint32_t*/ result = s.accum >>> s.accum_nbits;
        s.accum = fse_mask_lsb32(s.accum, s.accum_nbits);
        return result;
    }

    // MARK: - Encode/Decode

// Map to 32/64-bit implementations and types for I/O //TODO won't fix...we just assume 64 bits
//#if FSE_IOSTREAM_64
//
//    typedef uint64_t fse_bits;
//    typedef fse_out_stream64 fse_out_stream;
//    typedef fse_in_stream64 fse_in_stream;
//#define fse_mask_lsb fse_mask_lsb64
//#define fse_extract_bits fse_extract_bits64
//#define fse_out_init fse_out_init64
//#define fse_out_flush fse_out_flush64
//#define fse_out_finish fse_out_finish64
//#define fse_out_push fse_out_push64
//#define fse_in_init fse_in_checked_init64
//#define fse_in_checked_init fse_in_checked_init64
//#define fse_in_flush fse_in_checked_flush64
//#define fse_in_checked_flush fse_in_checked_flush64
//#define fse_in_flush2(_unused, _parameters, _unused2) 0 /* nothing */
//            #define fse_in_checked_flush2(_unused, _parameters)     /* nothing */
//#define fse_in_pull fse_in_pull64
//
//#else
//
//    typedef uint32_t fse_bits;
//    typedef fse_out_stream32 fse_out_stream;
//    typedef fse_in_stream32 fse_in_stream;
//#define fse_mask_lsb fse_mask_lsb32
//#define fse_extract_bits fse_extract_bits32
//#define fse_out_init fse_out_init32
//#define fse_out_flush fse_out_flush32
//#define fse_out_finish fse_out_finish32
//#define fse_out_push fse_out_push32
//#define fse_in_init fse_in_checked_init32
//#define fse_in_checked_init fse_in_checked_init32
//#define fse_in_flush fse_in_checked_flush32
//#define fse_in_checked_flush fse_in_checked_flush32
//#define fse_in_flush2 fse_in_checked_flush32
//#define fse_in_checked_flush2 fse_in_checked_flush32
//#define fse_in_pull fse_in_pull32
//
//#endif

    /*! @abstract Entry for one symbol in the encoder table (64b). */
    static class fse_encoder_entry {
        short s0;     // First state requiring a K-bit shift
        short k;      // States S >= S0 are shifted K bits. States S < S0 are
        // shifted K-1 bits
        short delta0; // Relative increment used to compute next state if S >= S0
        short delta1; // Relative increment used to compute next state if S < S0
    }

    /*! @abstract  Entry for one state in the decoder table (32b). */
    static class fse_decoder_entry {  // DO NOT REORDER THE FIELDS
        byte k;      // Number of bits to read
        byte/*uint8_t*/  symbol; // Emitted symbol
        short delta;  // Signed increment used to compute next state (+bias)

        int toInt() {
            return LittleEndianByteBuffer.allocate(4)
                    .put(k)
                    .put(symbol)
                    .putShort(delta)
                    .getInt(0);
        }
    }

    /*! @abstract  Entry for one state in the value decoder table (64b). */
    static class fse_value_decoder_entry {      // DO NOT REORDER THE FIELDS
        byte/*uint8_t*/  total_bits; // state bits + extra value bits = shift for next decode
        byte/*uint8_t*/  value_bits; // extra value bits
        short delta;      // state base (delta)
        int   vbase;      // value base

        public fse_value_decoder_entry() {

        }

        public fse_value_decoder_entry(fse_value_decoder_entry ei) {
            this.total_bits = ei.total_bits;
            this.value_bits = ei.value_bits;
            this.delta = ei.delta;
            this.vbase = ei.vbase;
        }
    }


    /*! @abstract Encode SYMBOL using the encoder table, and update \c *pstate,
     *  \c out.
     *  @note The caller must ensure we have enough bits available in the output
     *  stream accumulator. */
    static void fse_encode(short/*fse_state*/[] pstate,
                           final fse_encoder_entry[] encoder_table,
                           fse_out_stream64 out, byte/*uint8_t*/ symbol) {
        int s = pstate[0];
        fse_encoder_entry e = encoder_table[Byte.toUnsignedInt(symbol)];
        int s0 = e.s0;
        int k = e.k;
        int delta0 = e.delta0;
        int delta1 = e.delta1;

        // Number of bits to write
        boolean hi = s >= s0;
        int/*fse_bit_count*/ nbits = hi ? k : (k - 1);
        short/*fse_state*/ delta = (short) (hi ? delta0 : delta1);

        // Write lower NBITS of state
        long/*fse_bits*/ b = fse_mask_lsb64(s, nbits);
        fse_out_push64(out, nbits, b);

        // Update state with remaining bits and delta
        pstate[0] = (short) (delta + (s >>> nbits));
    }

    /*! @abstract Decode and return symbol using the decoder table, and update
     *  \c *pstate, \c in.
     *  @note The caller must ensure we have enough bits available in the input
     *  stream accumulator. */
    static byte/*uint8_t*/ fse_decode(short/*fse_state*/[] pstate,
                                      final int[] decoder_table,
                                      fse_in_stream64 in) {
        int e = decoder_table[pstate[0]];

        // Update state from K bits of input + DELTA
        pstate[0] = (short) ((short/*fse_state*/) (e >>> 16) + (short/*fse_state*/) fse_in_pull64(in, e & 0xff));

        // Return the symbol for this state
        return (byte) fse_extract_bits64(e, 8, 8); // symbol
    }

    /*! @abstract Decode and return value using the decoder table, and update \c
     *  *pstate, \c in.
     * \c value_decoder_table[nstates]
     * @note The caller must ensure we have enough bits available in the input
     * stream accumulator. */
    static int fse_value_decode(short/*fse_state*/[] pstate,
                                final fse_value_decoder_entry[] value_decoder_table,
                                fse_in_stream64 in) {
        fse_value_decoder_entry entry = value_decoder_table[pstate[0]];
        int/*uint32_t*/ state_and_value_bits = (int/*uint32_t*/) fse_in_pull64(in, entry.total_bits);
        pstate[0] = (short/*fse_state*/) (entry.delta + (state_and_value_bits >>> entry.value_bits));
        return (int) (entry.vbase + fse_mask_lsb64(state_and_value_bits, entry.value_bits));
    }

    // MARK: - Tables

    // IMPORTANT: To properly decode an FSE encoded stream, both encoder/decoder
    // tables shall be initialized with the same parameters, including the
    // FREQ[NSYMBOL] array.
    //

    /*! @abstract Sanity check on frequency table, verify sum of \c freq
     *  is <= \c number_of_states. */
    static int fse_check_freq(final short/*uint16_t*/[] freq_table,
                              final int /*size_t*/ table_size,
                              final int /*size_t*/ number_of_states) {
        int/*size_t*/ sum_of_freq = 0;
        for (int i = 0; i < table_size; i++) {
            sum_of_freq += freq_table[i];
        }
        return (sum_of_freq > number_of_states) ? -1 : 0;
    }

    /*! @abstract Initialize encoder table \c t[nsymbols].
     *
     * @param nstates
     * sum \c freq[i]; the number of states (a power of 2).
     *
     * @param nsymbols
     * the number of symbols.
     *
     * @param freq[nsymbols]
     * is a normalized histogram of symbol frequencies, with \c freq[i] >= 0.
     * Some symbols may have a 0 frequency. In that case they should not be
     * present in the data.
     */
    static void fse_init_encoder_table(int nstates, int nsymbols,
                                       final short/*uint16_t*/[] freq,
                                       fse_encoder_entry[] t) {
        int offset = 0; // current offset
        int n_clz = __builtin_clz(nstates);
        for (int i = 0; i < nsymbols; i++) {
            int f = (int) freq[i];
            if (f == 0)
                continue; // skip this symbol, no occurrences
            int k =
                    __builtin_clz(f) - n_clz; // shift needed to ensure N <= (F<<K) < 2*N
            t[i] = new fse_encoder_entry();
            t[i].s0 = (short) ((f << k) - nstates);
            t[i].k = (short) k;
            t[i].delta0 = (short) (offset - f + (nstates >>> k));
            t[i].delta1 = (short) (offset - f + (nstates >>> (k - 1)));
            offset += f;
        }
    }

    /*! @abstract Initialize decoder table \c t[nstates].
     *
     * @param nstates
     * sum \c freq[i]; the number of states (a power of 2).
     *
     * @param nsymbols
     * the number of symbols.
     *
     * @param feq[nsymbols]
     * a normalized histogram of symbol frequencies, with \c freq[i] >= 0.
     * Some symbols may have a 0 frequency. In that case they should not be
     * present in the data.
     *
     * @return 0 if OK.
     * @return -1 on failure.
     */
    static int fse_init_decoder_table(int nstates, int nsymbols,
                                      final short/*uint16_t*/[] freq,
                                      int[] t) {
        assert (nsymbols <= 256);
        assert (fse_check_freq(freq, nsymbols, nstates) == 0);
        int n_clz = __builtin_clz(nstates);
        int sum_of_freq = 0;
        for (int i = 0, t_index = 0; i < nsymbols; i++) {
            int f = (int) freq[i];
            if (f == 0)
                continue; // skip this symbol, no occurrences

            sum_of_freq += f;

            if (sum_of_freq > nstates) {
                return -1;
            }

            int k =
                    __builtin_clz(f) - n_clz; // shift needed to ensure N <= (F<<K) < 2*N
            int j0 = ((2 * nstates) >>> k) - f;

            // Initialize all states S reached by this symbol: OFFSET <= S < OFFSET + F
            for (int j = 0; j < f; j++) {
                fse_decoder_entry e = new fse_decoder_entry();

                e.symbol = (byte/*uint8_t*/) i;
                if (j < j0) {
                    e.k = (byte) k;
                    e.delta = (short) (((f + j) << k) - nstates);
                } else {
                    e.k = (byte) (k - 1);
                    e.delta = (short) ((j - j0) << (k - 1));
                }

//                memcpy(t, & e, sizeof(e));
//                t++;
                t[t_index++] = e.toInt();
            }
        }

        return 0; // OK
    }

    /*! @abstract Initialize value decoder table \c t[nstates].
     *
     * @param nstates
     * sum \cfreq[i]; the number of states (a power of 2).
     *
     * @param nsymbols
     * the number of symbols.
     *
     * @param freq[nsymbols]
     * a normalized histogram of symbol frequencies, with \c freq[i] >= 0.
     * \c symbol_vbits[nsymbols] and \c symbol_vbase[nsymbols] are the number of
     * value bits to read and the base value for each symbol.
     * Some symbols may have a 0 frequency.  In that case they should not be
     * present in the data.
     */
    static void fse_init_value_decoder_table(int nstates, int nsymbols,
                                             final short/*uint16_t*/[] freq,
                                             final byte/*uint8_t*/[] symbol_vbits,
                                             final int[] symbol_vbase,
                                             fse_value_decoder_entry[] t) {
        assert (nsymbols <= 256);
        assert (fse_check_freq(freq, nsymbols, nstates) == 0);

        int n_clz = __builtin_clz(nstates);
        for (int i = 0, t_index = 0; i < nsymbols; i++) {
            int f = (int) freq[i];
            if (f == 0)
                continue; // skip this symbol, no occurrences

            int k =
                    __builtin_clz(f) - n_clz; // shift needed to ensure N <= (F<<K) < 2*N
            int j0 = ((2 * nstates) >>> k) - f;

            fse_value_decoder_entry ei = new fse_value_decoder_entry();
            ei.value_bits = symbol_vbits[i];
            ei.vbase = symbol_vbase[i];

            // Initialize all states S reached by this symbol: OFFSET <= S < OFFSET + F
            for (int j = 0; j < f; j++) {
                fse_value_decoder_entry e = new fse_value_decoder_entry(ei);

                if (j < j0) {
                    e.total_bits = (byte/*uint8_t*/) (k + e.value_bits);
                    e.delta = (short) (((f + j) << k) - nstates);
                } else {
                    e.total_bits = (byte/*uint8_t*/) ((k - 1) + e.value_bits);
                    e.delta = (short) ((j - j0) << (k - 1));
                }

//                memcpy(t, & e, 8);
//                t++;
                t[t_index++] = e;
            }
        }
    }

    // Remove states from symbols until the correct number of states is used.
    static void fse_adjust_freqs(short/*uint16_t*/[] freq, int overrun, int nsymbols) {
        for (int shift = 3; overrun != 0; shift--) {
            for (int sym = 0; sym < nsymbols; sym++) {
                if (freq[sym] > 1) {
                    int n = (freq[sym] - 1) >>> shift;
                    if (n > overrun)
                        n = overrun;
                    freq[sym] -= n;
                    overrun -= n;
                    if (overrun == 0)
                        break;
                }
            }
        }
    }

    /*! @abstract Normalize a table \c t[nsymbols] of occurrences to
     *  \c freq[nsymbols]. */
    static void fse_normalize_freq(int nstates, int nsymbols, final int/*uint32_t*/[] t,
                                   short/*uint16_t*/[] freq) {
        int/*uint32_t*/ s_count = 0;
        int remaining = nstates; // must be signed; this may become < 0
        int max_freq = 0;
        int max_freq_sym = 0;
        int shift = __builtin_clz(nstates) - 1;
        long/*uint32_t*/ highprec_step;

        // Compute the total number of symbol occurrences
        for (int i = 0; i < nsymbols; i++)
            s_count += t[i];

        if (s_count == 0)
            highprec_step = 0; // no symbols used
        else
            highprec_step = (/*uint32_t*/ 1L << 31) / s_count;

        for (int i = 0; i < nsymbols; i++) {

            // Rescale the occurrence count to get the normalized frequency.
            // Round up if the fractional part is >= 0.5; otherwise round down.
            // For efficiency, we do this calculation using integer arithmetic.
            int f = (int) ((((t[i] * highprec_step) >>> shift) + 1) >>> 1);

            // If a symbol was used, it must be given a nonzero normalized frequency.
            if (f == 0 && t[i] != 0)
                f = 1;

            freq[i] = (short) f;
            remaining -= f;

            // Remember the maximum frequency and which symbol had it.
            if (f > max_freq) {
                max_freq = f;
                max_freq_sym = i;
            }
        }

        // If there remain states to be assigned, then just assign them to the most
        // frequent symbol.  Alternatively, if we assigned more states than were
        // actually available, then either remove states from the most frequent symbol
        // (for minor overruns) or use the slower adjustment algorithm (for major
        // overruns).
        if (-remaining < (max_freq >>> 2)) {
            freq[max_freq_sym] += remaining;
        } else {
            fse_adjust_freqs(freq, -remaining, nsymbols);
        }
    }
}
