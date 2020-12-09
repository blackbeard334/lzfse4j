package lzfse;

import lzfse.FSE.fse_in_stream64;
import lzfse.InternalStateObjects.lzfse_compressed_block_decoder_state;
import lzfse.InternalStateObjects.lzfse_decoder_state;
import lzfse.InternalStateObjects.lzvn_compressed_block_decoder_state;
import lzfse.InternalStateObjects.uncompressed_block_decoder_state;
import lzfse.InternalBlockHeaderObjects.lzfse_compressed_block_header_v1;
import lzfse.InternalBlockHeaderObjects.lzfse_compressed_block_header_v2;
import lzfse.InternalBlockHeaderObjects.uncompressed_block_header;

import java.nio.ByteBuffer;

import static lzfse.FSE.fse_decode;
import static lzfse.FSE.fse_in_checked_flush64;
import static lzfse.FSE.fse_in_checked_init64;
import static lzfse.FSE.fse_init_decoder_table;
import static lzfse.FSE.fse_init_value_decoder_table;
import static lzfse.FSE.fse_value_decode;
import static lzfse.Internal.LZFSE_ENCODE_D_STATES;
import static lzfse.Internal.LZFSE_ENCODE_D_SYMBOLS;
import static lzfse.Internal.LZFSE_ENCODE_LITERAL_STATES;
import static lzfse.Internal.LZFSE_ENCODE_LITERAL_SYMBOLS;
import static lzfse.Internal.LZFSE_ENCODE_L_STATES;
import static lzfse.Internal.LZFSE_ENCODE_L_SYMBOLS;
import static lzfse.Internal.LZFSE_ENCODE_M_STATES;
import static lzfse.Internal.LZFSE_ENCODE_M_SYMBOLS;
import static lzfse.Internal.LZFSE_STATUS_DST_FULL;
import static lzfse.Internal.LZFSE_STATUS_ERROR;
import static lzfse.Internal.LZFSE_STATUS_OK;
import static lzfse.Internal.LZFSE_STATUS_SRC_EMPTY;
import static lzfse.Internal.copy8;
import static lzfse.Internal.d_base_value;
import static lzfse.Internal.d_extra_bits;
import static lzfse.Internal.l_base_value;
import static lzfse.Internal.l_extra_bits;
import static lzfse.Internal.load4;
import static lzfse.Internal.lzfse_check_block_header_v1;
import static lzfse.Internal.m_base_value;
import static lzfse.Internal.m_extra_bits;
import static lzfse.InternalBlockHeaderObjects.LZFSE_COMPRESSEDLZVN_BLOCK_MAGIC;
import static lzfse.InternalBlockHeaderObjects.LZFSE_COMPRESSEDV1_BLOCK_MAGIC;
import static lzfse.InternalBlockHeaderObjects.LZFSE_COMPRESSEDV2_BLOCK_MAGIC;
import static lzfse.InternalBlockHeaderObjects.LZFSE_ENDOFSTREAM_BLOCK_MAGIC;
import static lzfse.InternalBlockHeaderObjects.LZFSE_NO_BLOCK_MAGIC;
import static lzfse.InternalBlockHeaderObjects.LZFSE_UNCOMPRESSED_BLOCK_MAGIC;

class DecodeBase {
    private DecodeBase() {
    }

    /*! @abstract Decode an entry value from next bits of stream.
     *  Return \p value, and set \p *nbits to the number of bits to consume
     *  (starting with LSB). */
    static int lzfse_decode_v1_freq_value(int/*uint32_t*/ bits, int[] nbits) {
        final byte lzfse_freq_nbits_table[/*32*/] = {
                2, 3, 2, 5, 2, 3, 2, 8, 2, 3, 2, 5, 2, 3, 2, 14,
                2, 3, 2, 5, 2, 3, 2, 8, 2, 3, 2, 5, 2, 3, 2, 14};
        final byte lzfse_freq_value_table[/*32*/] = {
                0, 2, 1, 4, 0, 3, 1, -1, 0, 2, 1, 5, 0, 3, 1, -1,
                0, 2, 1, 6, 0, 3, 1, -1, 0, 2, 1, 7, 0, 3, 1, -1};

        int/*uint32_t*/ b = bits & 31; // lower 5 bits
        int n = lzfse_freq_nbits_table[b];
        nbits[0] = n;

        // Special cases for > 5 bits encoding
        if (n == 8)
            return 8 + ((bits >>> 4) & 0xf);
        if (n == 14)
            return 24 + ((bits >>> 4) & 0x3ff);

        // <= 5 bits encoding from table
        return lzfse_freq_value_table[b];
    }

    /*! @abstract Extracts up to 32 bits from a 64-bit field beginning at
     *  \p offset, and zero-extends them to a \p uint32_t.
     *
     *  If we number the bits of \p v from 0 (least significant) to 63 (most
     *  significant), the result is bits \p offset to \p offset+nbits-1. */
    @Deprecated
    static int/*uint32_t*/ get_field(long/*uint64_t*/ v, int offset, int nbits) {
        assert (offset + nbits < 64 && offset >= 0 && nbits <= 32);
        if (nbits == 32)
            return (int/*uint32_t*/) (v >>> offset);
        return (int/*uint32_t*/) ((v >>> offset) & ((1 << nbits) - 1));
    }

    /*! @abstract Return \c header_size field from a \c lzfse_compressed_block_header_v2. */
    static int/*uint32_t*/
    lzfse_decode_v2_header_size(final lzfse_compressed_block_header_v2 in) {
        return get_field(in.packed_fields[2], 0, 32);
    }

    /*! @abstract Decode all fields from a \c lzfse_compressed_block_header_v2 to a
     * \c lzfse_compressed_block_header_v1.
     * @return 0 on success.
     * @return -1 on failure. */
    static int lzfse_decode_v1(lzfse_compressed_block_header_v1 out,
                               final lzfse_compressed_block_header_v2 in) {
        // Clear all fields
//        memset(out, 0x00, sizeof(lzfse_compressed_block_header_v1));

        long/*uint64_t*/ v0 = in.packed_fields[0];
        long/*uint64_t*/ v1 = in.packed_fields[1];
        long/*uint64_t*/ v2 = in.packed_fields[2];

        out.magic = LZFSE_COMPRESSEDV1_BLOCK_MAGIC;
        out.n_raw_bytes = in.n_raw_bytes;

        // Literal state
        out.n_literals = get_field(v0, 0, 20);
        out.n_literal_payload_bytes = get_field(v0, 20, 20);
        out.literal_bits = (int) get_field(v0, 60, 3) - 7;
        out.literal_state[0] = (short) get_field(v1, 0, 10);
        out.literal_state[1] = (short) get_field(v1, 10, 10);
        out.literal_state[2] = (short) get_field(v1, 20, 10);
        out.literal_state[3] = (short) get_field(v1, 30, 10);

        // L,M,D state
        out.n_matches = get_field(v0, 40, 20);
        out.n_lmd_payload_bytes = get_field(v1, 40, 20);
        out.lmd_bits = (int) get_field(v1, 60, 3) - 7;
        out.l_state = (short) get_field(v2, 32, 10);
        out.m_state = (short) get_field(v2, 42, 10);
        out.d_state = (short) get_field(v2, 52, 10);

        // Total payload size
        out.n_payload_bytes = out.n_literal_payload_bytes + out.n_lmd_payload_bytes;

        // Freq tables
        short/*uint16_t*/[] dst = new short[out.l_freq.length + out.m_freq.length + out.d_freq.length + out.literal_freq.length];//&(out->l_freq[0]);
        final ByteBuffer/*uint8_t*/ src = LittleEndianByteBuffer.wrap(in.freq);
        final int/*uint8_t*/ src_end =/*(const uint8_t *)in*/ get_field(v2, 0, 32) - in.OFFSET_OF_FREQ; // first byte after header
        int/*uint32_t*/ accum = 0;
        int accum_nbits = 0;

        // No freq tables?
        if (src_end == src.position())
            return 0; // OK, freq tables were omitted

        for (int i = 0; i < LZFSE_ENCODE_L_SYMBOLS + LZFSE_ENCODE_M_SYMBOLS +
                LZFSE_ENCODE_D_SYMBOLS + LZFSE_ENCODE_LITERAL_SYMBOLS;
             i++) {
            // Refill accum, one byte at a time, until we reach end of header, or accum
            // is full
            while (src.position() < src_end && accum_nbits + 8 <= 32) {
                accum |= /*(uint32_t)*/(Byte.toUnsignedInt(src.get())) << accum_nbits;
                accum_nbits += 8;
//                src++;
            }

            // Decode and store value
            int[] nbits = {0};
            dst[i] = (short) lzfse_decode_v1_freq_value(accum, nbits);

            if (nbits[0] > accum_nbits) {
                Util.flushArrays(dst, i + 1, out.l_freq, out.m_freq, out.d_freq, out.literal_freq);
                return -1; // failed
            }

            // Consume nbits bits
            accum >>>= nbits[0];
            accum_nbits -= nbits[0];
        }
        Util.flushArrays(dst, dst.length, out.l_freq, out.m_freq, out.d_freq, out.literal_freq);

        if (accum_nbits >= 8 || src.position() != src_end)
            return -1; // we need to end up exactly at the end of header, with less than
        // 8 bits in accumulator

        return 0;
    }

    static void copy(ByteBuffer/*uint8_t*/ dst, final ByteBuffer /*uint8_t*/ src, int/*size_t*/ length) {
        final ByteBuffer dst1 = LittleEndianByteBuffer.duplicate(dst);
        final ByteBuffer src1 = LittleEndianByteBuffer.duplicate(src);
        final int/*uint8_t*/dst_end = dst1.position() + length;
        do {
            copy8(dst1, src1);
//            dst += 8;
//            src += 8;
        } while (dst1.position() < dst_end);
    }

    static int lzfse_decode_lmd(lzfse_decoder_state s) {
        lzfse_compressed_block_decoder_state bs = s.compressed_lzfse_block_state;
        short/*fse_state*/[] l_state = {bs.l_state};
        short/*fse_state*/[] m_state = {bs.m_state};
        short/*fse_state*/[] d_state = {bs.d_state};
        fse_in_stream64 in = bs.lmd_in_stream;
        final int/*uint8_t*/ src_start = s.src_begin;
        final ByteBuffer/*uint8_t*/ src = LittleEndianByteBuffer.duplicate(s.src, bs.lmd_in_buf);
        final ByteBuffer/*uint8_t*/ lit = LittleEndianByteBuffer.wrap(bs.current_literal);
        ByteBuffer/*uint8_t*/ dst = s.dst;
        int/*uint32_t*/ symbols = bs.n_matches;
        int L = bs.l_value;
        int M = bs.m_value;
        int D = bs.d_value;

        assert (l_state[0] < LZFSE_ENCODE_L_STATES);
        assert (m_state[0] < LZFSE_ENCODE_M_STATES);
        assert (d_state[0] < LZFSE_ENCODE_D_STATES);

        //  Number of bytes remaining in the destination buffer, minus 32 to
        //  provide a margin of safety for using overlarge copies on the fast path.
        //  This is a signed quantity, and may go negative when we are close to the
        //  end of the buffer.  That's OK; we're careful about how we handle it
        //  in the slow-and-careful match execution path.
        int/*ptrdiff_t*/ remaining_bytes = s.dst_end - dst.position() - 32;

        //  If L or M is non-zero, that means that we have already started decoding
        //  this block, and that we needed to interrupt decoding to get more space
        //  from the caller.  There's a pending L, M, D triplet that we weren't
        //  able to completely process.  Jump ahead to finish executing that symbol
        //  before decoding new values.

        boolean goto_ExecuteMatch = (L != 0 || M != 0);//not gonna extract this goto
//    goto ExecuteMatch;

        while (symbols > 0) {
            if (!goto_ExecuteMatch) {
                int res;
                //  Decode the next L, M, D symbol from the input stream.
                res = fse_in_checked_flush64(in, src, src_start);
                if (res != 0) {
                    return LZFSE_STATUS_ERROR;
                }
                L = fse_value_decode(l_state, bs.l_decoder, in);
                assert (l_state[0] < LZFSE_ENCODE_L_STATES);
                if ((lit.position() + L) >= bs.BYTES) {//(bs->literals + LZFSE_LITERALS_PER_BLOCK + 64)) {
                    return LZFSE_STATUS_ERROR;
                }
                res = fse_in_flush2(in, src, src_start);
                if (res != 0) {
                    return LZFSE_STATUS_ERROR;
                }
                M = fse_value_decode(m_state, bs.m_decoder, in);
                assert (m_state[0] < LZFSE_ENCODE_M_STATES);
                res = fse_in_flush2(in, src, src_start);
                if (res != 0) {
                    return LZFSE_STATUS_ERROR;
                }
                int new_d = fse_value_decode(d_state, bs.d_decoder, in);
                assert (d_state[0] < LZFSE_ENCODE_D_STATES);
                D = new_d != 0 ? new_d : D;
                symbols--;
            }
            goto_ExecuteMatch = false;

            ExecuteMatch:
            //  Error if D is out of range, so that we avoid passing through
            //  uninitialized data or accesssing memory out of the destination
            //  buffer.
            if ((int/*uint32_t*/) D > dst.position() + L - s.dst_begin)
                return LZFSE_STATUS_ERROR;

            if (L + M <= remaining_bytes) {
                //  If we have plenty of space remaining, we can copy the literal
                //  and match with 16- and 32-byte operations, without worrying
                //  about writing off the end of the buffer.
                remaining_bytes -= L + M;
                copy(dst, lit, L);
                LittleEndianByteBuffer.skip(dst, L);
                LittleEndianByteBuffer.skip(lit, L);
                //  For the match, we have two paths; a fast copy by 16-bytes if
                //  the match distance is large enough to allow it, and a more
                //  careful path that applies a permutation to account for the
                //  possible overlap between source and destination if the distance
                //  is small.
                if (D >= 8 || D >= M)
                    copy(dst, LittleEndianByteBuffer.duplicate(dst, -D), M);
                else
                    for (int/*size_t*/ i = 0; i < M; i++) {
                        final int index = dst.position() + i;
                        dst.put(index, dst.get(index - D));
                    }
                LittleEndianByteBuffer.skip(dst, M);//dst += M;
            } else {
                //  Otherwise, we are very close to the end of the destination
                //  buffer, so we cannot use wide copies that slop off the end
                //  of the region that we are copying to. First, we restore
                //  the true length remaining, rather than the sham value we've
                //  been using so far.
                remaining_bytes += 32;
                //  Now, we process the literal. Either there's space for it
                //  or there isn't; if there is, we copy the whole thing and
                //  update all the pointers and lengths to reflect the copy.
                if (L <= remaining_bytes) {
                    for (int/*size_t*/ i = 0; i < L; i++)
                        dst.put(lit.get(lit.position() + i));
//                    dst += L;
                    LittleEndianByteBuffer.skip(lit, L);//lit += L;
                    remaining_bytes -= L;
                    L = 0;
                }
                //  There isn't enough space to fit the whole literal. Copy as
                //  much of it as we can, update the pointers and the value of
                //  L, and report that the destination buffer is full. Note that
                //  we always write right up to the end of the destination buffer.
                else {
                    for (int/*size_t*/ i = 0; i < remaining_bytes; i++)
                        dst.put(lit.get(lit.position() + i));
//                    dst += remaining_bytes;
                    LittleEndianByteBuffer.skip(lit, remaining_bytes);//lit += remaining_bytes;
                    L -= remaining_bytes;
                    return lzfse_decode_lmd_goto_DestinationBufferIsFull(bs, L, M, D, l_state, m_state, d_state, in, symbols, src, s, lit, dst);
                }
                //  The match goes just like the literal does. We copy as much as
                //  we can byte-by-byte, and if we reach the end of the buffer
                //  before finishing, we return to the caller indicating that
                //  the buffer is full.
                if (M <= remaining_bytes) {
                    for (int/*size_t*/ i = 0; i < M; i++) {
                        final int index = dst.position() + i;
                        dst.put(index, dst.get(index - D));
                    }
                    LittleEndianByteBuffer.skip(dst, M);//dst += M;
                    remaining_bytes -= M;
                    M = 0;
//                    (void) M; // no dead store warning
                    //  We don't need to update M = 0, because there's no partial
                    //  symbol to continue executing. Either we're at the end of
                    //  the block, in which case we will never need to resume with
                    //  this state, or we're going to decode another L, M, D set,
                    //  which will overwrite M anyway.
                    //
                    // But we still set M = 0, to maintain the post-condition.
                } else {
                    for (int/*size_t*/ i = 0; i < remaining_bytes; i++){
                        final int index = dst.position() + i;
                        dst.put(index, dst.get(index - D));
                    }
                    LittleEndianByteBuffer.skip(dst, remaining_bytes);//dst += remaining_bytes;
                    M -= remaining_bytes;
                    return lzfse_decode_lmd_goto_DestinationBufferIsFull(bs, L, M, D, l_state, m_state, d_state, in, symbols, src, s, lit, dst);
                }
                //  Restore the "sham" decremented value of remaining_bytes and
                //  continue to the next L, M, D triple. We'll just be back in
                //  the careful path again, but this only happens at the very end
                //  of the buffer, so a little minor inefficiency here is a good
                //  tradeoff for simpler code.
                remaining_bytes -= 32;
            }
        }
        //  Because we've finished with the whole block, we don't need to update
        //  any of the blockstate fields; they will not be used again. We just
        //  update the destination pointer in the state object and return.
        s.dst = dst;
        return LZFSE_STATUS_OK;
    }

    private static int lzfse_decode_lmd_goto_DestinationBufferIsFull(lzfse_compressed_block_decoder_state bs,
                                                                     final int L, final int M, final int D,
                                                                     final short[] l_state, final short[] m_state, final short[] d_state,
                                                                     final fse_in_stream64 in, final int symbols, final ByteBuffer/*uint8_t*/ src,
                                                                     lzfse_decoder_state s, final ByteBuffer/*uint8_t*/ lit, final ByteBuffer/*uint8_t*/ dst) {
        //  Because we want to be able to resume decoding where we've left
        //  off (even in the middle of a literal or match), we need to
        //  update all of the block state fields with the current values
        //  so that we can resume execution from this point once the
        //  caller has given us more space to write into.
        bs.l_value = L;
        bs.m_value = M;
        bs.d_value = D;
        bs.l_state = l_state[0];
        bs.m_state = m_state[0];
        bs.d_state = d_state[0];
        bs.lmd_in_stream = in;
        bs.n_matches = symbols;
        bs.lmd_in_buf = /*(uint32_t)*/(src.position() - s.src.position());
        bs.current_literal = lit.array();
        s.dst = dst;
        return LZFSE_STATUS_DST_FULL;
    }

    private static int fse_in_flush2(fse_in_stream64 in, ByteBuffer src, int src_start) {
        //do nothing
        return 0;
    }

    static int lzfse_decode(lzfse_decoder_state s) {
        while (true) {
            // Are we inside a block?
            switch (s.block_magic) {
                case LZFSE_NO_BLOCK_MAGIC: {
                    // We need at least 4 bytes of magic number to identify next block
                    if (s.src.position() + 4 > s.src_end)
                        return LZFSE_STATUS_SRC_EMPTY; // SRC truncated
                    int/*uint32_t*/ magic = load4(s.src);

                    if (magic == LZFSE_ENDOFSTREAM_BLOCK_MAGIC) {
//                        s.src += 4;
                        s.end_of_stream = 1;
                        return LZFSE_STATUS_OK; // done
                    } else {
                        LittleEndianByteBuffer.skip(s.src, -4);//rollback
                    }

                    if (magic == LZFSE_UNCOMPRESSED_BLOCK_MAGIC) {
                        if (s.src.position() + uncompressed_block_header.BYTES > s.src_end)
                            return LZFSE_STATUS_SRC_EMPTY; // SRC truncated
                        // Setup state for uncompressed block
                        uncompressed_block_decoder_state bs = (s.uncompressed_block_state);
                        bs.n_raw_bytes = load4(s.src/* + offsetof(uncompressed_block_header, n_raw_bytes)*/);
//                        s.src += uncompressed_block_header.BYTES;
                        s.block_magic = magic;
                        break;
                    }

//                    if (magic == LZFSE_COMPRESSEDLZVN_BLOCK_MAGIC) {//TODO implement lzvn
//                        if (s.src + sizeof(lzvn_compressed_block_header) > s.src_end)
//                            return LZFSE_STATUS_SRC_EMPTY; // SRC truncated
//                        // Setup state for compressed LZVN block
//                        lzvn_compressed_block_decoder_state bs = (s.compressed_lzvn_block_state);
//                        bs.n_raw_bytes =
//                                load4(s.src + offsetof(lzvn_compressed_block_header, n_raw_bytes));
//                        bs.n_payload_bytes = load4(
//                                s.src + offsetof(lzvn_compressed_block_header, n_payload_bytes));
//                        bs.d_prev = 0;
//                        s.src += sizeof(lzvn_compressed_block_header);
//                        s.block_magic = magic;
//                        break;
//                    }

                    if (magic == LZFSE_COMPRESSEDV1_BLOCK_MAGIC ||
                            magic == LZFSE_COMPRESSEDV2_BLOCK_MAGIC) {
                        lzfse_compressed_block_header_v1 header1 = new lzfse_compressed_block_header_v1();
                        int/*size_t*/ header_size = 0;

                        // Decode compressed headers
                        if (magic == LZFSE_COMPRESSEDV2_BLOCK_MAGIC) {
                            // Check we have the fixed part of the structure
//                            if (s.src.position() + offsetof(lzfse_compressed_block_header_v2, freq) > s.src_end)
                            if (s.src.position() + lzfse_compressed_block_header_v2.OFFSET_OF_FREQ > s.src_end)
                                return LZFSE_STATUS_SRC_EMPTY; // SRC truncated

                            // Get size, and check we have the entire structure
                            final lzfse_compressed_block_header_v2 header2 =
                                    lzfse_compressed_block_header_v2.fromByteBuffer(s.src); // not aligned, OK
                            header_size = lzfse_decode_v2_header_size(header2);
                            if (s.src.position() + header_size > s.src_end)
                                return LZFSE_STATUS_SRC_EMPTY; // SRC truncated
                            int decodeStatus = lzfse_decode_v1(header1, header2);
                            if (decodeStatus != 0)
                                return LZFSE_STATUS_ERROR;
                        } else {
                            if (s.src.position() + lzfse_compressed_block_header_v1.BYTES > s.src_end)
                                return LZFSE_STATUS_SRC_EMPTY; // SRC truncated
                            header1 = lzfse_compressed_block_header_v1.fromByteBuffer(s.src);//memcpy( & header1, s.src, lzfse_compressed_block_header_v1.BYTES);
                            header_size = lzfse_compressed_block_header_v1.BYTES;
                        }

                        // We require the header + entire encoded block to be present in SRC
                        // during the entire block decoding.
                        // This can be relaxed somehow, if it becomes a limiting factor, at the
                        // price of a more complex state maintenance.
                        // For DST, we can't easily require space for the entire decoded block,
                        // because it may expand to something very very large.
                        if (s.src.position() + header_size + header1.n_literal_payload_bytes +
                                header1.n_lmd_payload_bytes >
                                s.src_end)
                            return LZFSE_STATUS_SRC_EMPTY; // need all encoded block

                        // Sanity checks
                        if (lzfse_check_block_header_v1(header1) != 0) {
                            return LZFSE_STATUS_ERROR;
                        }

                        // Skip header
                        LittleEndianByteBuffer.skip(s.src, header_size);

                        // Setup state for compressed V1 block from header
                        lzfse_compressed_block_decoder_state bs = (s.compressed_lzfse_block_state);
                        bs.n_lmd_payload_bytes = header1.n_lmd_payload_bytes;
                        bs.n_matches = header1.n_matches;
                        fse_init_decoder_table(LZFSE_ENCODE_LITERAL_STATES,
                                LZFSE_ENCODE_LITERAL_SYMBOLS,
                                header1.literal_freq, bs.literal_decoder);
                        fse_init_value_decoder_table(
                                LZFSE_ENCODE_L_STATES, LZFSE_ENCODE_L_SYMBOLS, header1.l_freq,
                                l_extra_bits, l_base_value, bs.l_decoder);
                        fse_init_value_decoder_table(
                                LZFSE_ENCODE_M_STATES, LZFSE_ENCODE_M_SYMBOLS, header1.m_freq,
                                m_extra_bits, m_base_value, bs.m_decoder);
                        fse_init_value_decoder_table(
                                LZFSE_ENCODE_D_STATES, LZFSE_ENCODE_D_SYMBOLS, header1.d_freq,
                                d_extra_bits, d_base_value, bs.d_decoder);

                        // Decode literals
                        {
                            fse_in_stream64 in = new fse_in_stream64();
                            final int/*uint8_t*/ buf_start = s.src_begin;
                            LittleEndianByteBuffer.skip(s.src, header1.n_literal_payload_bytes); // skip literal payload
                            final ByteBuffer /*uint8_t*/ buf = LittleEndianByteBuffer.duplicate(s.src); // read bits backwards from the end
                            if (fse_in_checked_init64(in, header1.literal_bits, buf, buf_start) != 0)
                                return LZFSE_STATUS_ERROR;

                            short/*fse_state*/[] state0 = {header1.literal_state[0]};
                            short/*fse_state*/[] state1 = {header1.literal_state[1]};
                            short/*fse_state*/[] state2 = {header1.literal_state[2]};
                            short/*fse_state*/[] state3 = {header1.literal_state[3]};

                            for (int/*uint32_t*/ i = 0; i < header1.n_literals; i += 4) // n_literals is multiple of 4
                            {
//#if FSE_IOSTREAM_64
                                if (fse_in_checked_flush64(in, buf, buf_start) != 0)
                                    return LZFSE_STATUS_ERROR;
                                bs.literals[i + 0] =
                                        fse_decode(state0, bs.literal_decoder, in); // 10b max
                                bs.literals[i + 1] =
                                        fse_decode(state1, bs.literal_decoder, in); // 10b max
                                bs.literals[i + 2] =
                                        fse_decode(state2, bs.literal_decoder, in); // 10b max
                                bs.literals[i + 3] =
                                        fse_decode(state3, bs.literal_decoder, in); // 10b max
//#else
//                                if (fse_in_flush(&in, &buf, buf_start) != 0)
//                                return LZFSE_STATUS_ERROR; // [25, 23] bits
//                                bs.literals[i + 0] =
//                                        fse_decode(&state0, bs.literal_decoder, &in); // 10b max
//                                bs.literals[i + 1] =
//                                        fse_decode(&state1, bs.literal_decoder, &in); // 10b max
//                                if (fse_in_flush(&in, &buf, buf_start) != 0)
//                                return LZFSE_STATUS_ERROR; // [25, 23] bits
//                                bs.literals[i + 2] =
//                                        fse_decode(&state2, bs.literal_decoder, &in); // 10b max
//                                bs.literals[i + 3] =
//                                        fse_decode(&state3, bs.literal_decoder, &in); // 10b max
//#endif
                            }

                            bs.current_literal = bs.literals;
                        } // literals

                        // SRC is not incremented to skip the LMD payload, since we need it
                        // during block decode.
                        // We will increment SRC at the end of the block only after this point.

                        // Initialize the L,M,D decode stream, do not start decoding matches
                        // yet, and store decoder state
                        {
                            fse_in_stream64 in = new fse_in_stream64();
                            // read bits backwards from the end
                            final ByteBuffer/*uint8_t*/ buf = LittleEndianByteBuffer.duplicate(s.src, header1.n_lmd_payload_bytes);
                            if (fse_in_checked_init64(in, header1.lmd_bits, buf, s.src.position()) != 0)
                                return LZFSE_STATUS_ERROR;

                            bs.l_state = header1.l_state;
                            bs.m_state = header1.m_state;
                            bs.d_state = header1.d_state;
                            bs.lmd_in_buf = /*(uint32_t)*/(buf.position() - s.src.position());
                            bs.l_value = bs.m_value = 0;
                            //  Initialize D to an illegal value so we can't erroneously use
                            //  an uninitialized "previous" value.
                            bs.d_value = -1;
                            bs.lmd_in_stream = in;
                        }

                        s.block_magic = magic;
                        break;
                    }

                    // Here we have an invalid magic number
                    return LZFSE_STATUS_ERROR;
                } // LZFSE_NO_BLOCK_MAGIC

                case LZFSE_UNCOMPRESSED_BLOCK_MAGIC: {
                    uncompressed_block_decoder_state bs = (s.uncompressed_block_state);

                    //  Compute the size (in bytes) of the data that we will actually copy.
                    //  This size is minimum(bs.n_raw_bytes, space in src, space in dst).

                    int/*uint32_t*/ copy_size = bs.n_raw_bytes; // bytes left to copy
                    if (copy_size == 0) {
                        s.block_magic = 0;
                        break;
                    } // end of block

                    if (s.src_end <= s.src.position())
                        return LZFSE_STATUS_SRC_EMPTY; // need more SRC data
                    final int/*size_t*/ src_space = s.src_end - s.src.position();
                    if (copy_size > src_space)
                        copy_size = /*(uint32_t)*/src_space; // limit to SRC data (> 0)

                    if (s.dst_end <= s.dst.position())
                        return LZFSE_STATUS_DST_FULL; // need more DST capacity
                    final int/*size_t*/ dst_space = s.dst_end - s.dst.position();

                    if (copy_size > dst_space)
                        copy_size = /*(uint32_t)*/dst_space; // limit to DST capacity (> 0)

                    // Now that we know that the copy size is bounded to the source and
                    // dest buffers, go ahead and copy the data.
                    // We always have copy_size > 0 here
                    LittleEndianByteBuffer.copy(s.dst, s.src, copy_size);//memcpy(s.dst, s.src, copy_size);
//                    s.src += copy_size;
//                    s.dst += copy_size;
                    bs.n_raw_bytes -= copy_size;

                    break;
                } // LZFSE_UNCOMPRESSED_BLOCK_MAGIC

                case LZFSE_COMPRESSEDV1_BLOCK_MAGIC:
                case LZFSE_COMPRESSEDV2_BLOCK_MAGIC: {
                    lzfse_compressed_block_decoder_state bs = (s.compressed_lzfse_block_state);
                    // Require the entire LMD payload to be in SRC
                    if (s.src_end <= s.src.position() ||
                            bs.n_lmd_payload_bytes > /*(size_t)*/(s.src_end - s.src.position()))
                        return LZFSE_STATUS_SRC_EMPTY;

                    int status = lzfse_decode_lmd(s);
                    if (status != LZFSE_STATUS_OK)
                        return status;

                    s.block_magic = LZFSE_NO_BLOCK_MAGIC;
                    LittleEndianByteBuffer.skip(s.src, bs.n_lmd_payload_bytes); // to next block
                    break;
                } // LZFSE_COMPRESSEDV1_BLOCK_MAGIC || LZFSE_COMPRESSEDV2_BLOCK_MAGIC

                case LZFSE_COMPRESSEDLZVN_BLOCK_MAGIC: {
                    lzvn_compressed_block_decoder_state bs = (s.compressed_lzvn_block_state);
                    if (bs.n_payload_bytes > 0 && s.src_end <= s.src.position())
                        return LZFSE_STATUS_SRC_EMPTY; // need more SRC data

                    // Init LZVN decoder state
                    lzvn_decoder_state dstate = new lzvn_decoder_state();//memset( & dstate, 0x00, sizeof(dstate));
                    dstate.src = s.src;
                    dstate.src_end = s.src_end;
                    if (dstate.src_end - s.src.position() > bs.n_payload_bytes)
                        dstate.src_end = s.src.position() + bs.n_payload_bytes; // limit to payload bytes
                    dstate.dst_begin = s.dst_begin;
                    dstate.dst = s.dst;
                    dstate.dst_end = s.dst_end;
                    if (dstate.dst_end - s.dst.position() > bs.n_raw_bytes)
                        dstate.dst_end = s.dst.position() + bs.n_raw_bytes; // limit to raw bytes
                    dstate.d_prev = bs.d_prev;
                    dstate.end_of_stream = 0;

                    // Run LZVN decoder
//                    lzvn_decode(&dstate);//TODO implement lzvn

                    // Update our state
                    int/*size_t*/ src_used = dstate.src.position() - s.src.position();
                    int/*size_t*/ dst_used = dstate.dst.position() - s.dst.position();
                    if (src_used > bs.n_payload_bytes || dst_used > bs.n_raw_bytes)
                        return LZFSE_STATUS_ERROR;
                    s.src = dstate.src;
                    s.dst = dstate.dst;
                    bs.n_payload_bytes -= /*(uint32_t)*/src_used;
                    bs.n_raw_bytes -= /*(uint32_t)*/dst_used;
                    bs.d_prev = (int/*uint32_t*/) dstate.d_prev;

                    // Test end of block
                    if (bs.n_payload_bytes == 0 && bs.n_raw_bytes == 0 &&
                            dstate.end_of_stream != 0) {
                        s.block_magic = 0;
                        break;
                    } // block done

                    // Check for invalid state
                    if (bs.n_payload_bytes == 0 || bs.n_raw_bytes == 0 ||
                            dstate.end_of_stream != 0)
                        return LZFSE_STATUS_ERROR;

                    // Here, block is not done and state is valid, so we need more space in dst.
                    return LZFSE_STATUS_DST_FULL;
                }

                default:
                    return LZFSE_STATUS_ERROR;

            } // switch magic

        } // block loop

//        return LZFSE_STATUS_OK;
    }

    /*! @abstract Base decoder state. */
    private static class lzvn_decoder_state {

        // Decoder I/O

        // Next byte to read in source buffer
        ByteBuffer /*unsigned char*/ src;
        // Next byte after source buffer
        int/*unsigned char*/         src_end;

        // Next byte to write in destination buffer (by decoder)
        ByteBuffer /*unsigned char*/ dst;
        // Valid range for destination buffer is [dst_begin, dst_end - 1]
        int/*unsigned char*/ dst_begin;
        int/*unsigned char*/ dst_end;
        // Next byte to read in destination buffer (modified by caller)
        int/*unsigned char*/ dst_current;

        // Decoder state

        // Partially expanded match, or 0,0,0.
        // In that case, src points to the next literal to copy, or the next op-code
        // if L==0.
        int/*size_t*/ L, M, D;

        // Distance for last emitted match, or 0
        long/*lzvn_offset*/ d_prev;

        // Did we decode end-of-stream?
        int end_of_stream;
    }

}
