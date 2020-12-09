package lzfse;

import java.nio.ByteBuffer;

import static lzfse.EncodeBase.lzfse_encode_v1_freq_table;
import static lzfse.EncodeBase.lzfse_encode_v1_state;
import static lzfse.EncodeTables.d_base_from_value;
import static lzfse.EncodeTables.l_base_from_value;
import static lzfse.EncodeTables.m_base_from_value;
import static lzfse.FSE.fse_encode;
import static lzfse.FSE.fse_init_encoder_table;
import static lzfse.FSE.fse_normalize_freq;
import static lzfse.FSE.fse_out_finish64;
import static lzfse.FSE.fse_out_flush64;
import static lzfse.FSE.fse_out_push64;
import static lzfse.Internal.LZFSE_ENCODE_D_STATES;
import static lzfse.Internal.LZFSE_ENCODE_D_SYMBOLS;
import static lzfse.Internal.LZFSE_ENCODE_LITERAL_STATES;
import static lzfse.Internal.LZFSE_ENCODE_LITERAL_SYMBOLS;
import static lzfse.Internal.LZFSE_ENCODE_L_STATES;
import static lzfse.Internal.LZFSE_ENCODE_L_SYMBOLS;
import static lzfse.Internal.LZFSE_ENCODE_MAX_L_VALUE;
import static lzfse.Internal.LZFSE_ENCODE_MAX_M_VALUE;
import static lzfse.Internal.LZFSE_ENCODE_M_STATES;
import static lzfse.Internal.LZFSE_ENCODE_M_SYMBOLS;
import static lzfse.Internal.LZFSE_LITERALS_PER_BLOCK;
import static lzfse.Internal.LZFSE_MATCHES_PER_BLOCK;
import static lzfse.Internal.LZFSE_STATUS_DST_FULL;
import static lzfse.Internal.LZFSE_STATUS_OK;
import static lzfse.Internal.copy16;
import static lzfse.Internal.d_base_value;
import static lzfse.Internal.d_extra_bits;
import static lzfse.Internal.l_base_value;
import static lzfse.Internal.l_extra_bits;
import static lzfse.Internal.m_base_value;
import static lzfse.Internal.m_extra_bits;
import static lzfse.Internal.store4;
import static lzfse.Internal.store8;
import static lzfse.InternalBlockHeaderObjects.LZFSE_COMPRESSEDV1_BLOCK_MAGIC;
import static lzfse.InternalBlockHeaderObjects.LZFSE_ENDOFSTREAM_BLOCK_MAGIC;

public class EncodeBackEnd {

    /*! @abstract Encode matches stored in STATE into a compressed/uncompressed block.
     * @return LZFSE_STATUS_OK on success.
     * @return LZFSE_STATUS_DST_FULL and restore initial state if output buffer is
     * full. */
    static int lzfse_encode_matches(InternalStateObjects.lzfse_encoder_state s) {
        if (s.n_literals == 0 && s.n_matches == 0)
            return LZFSE_STATUS_OK; // nothing to store, OK

        int/*uint32_t*/[] l_occ = new int[LZFSE_ENCODE_L_SYMBOLS];
        int/*uint32_t*/[] m_occ = new int[LZFSE_ENCODE_M_SYMBOLS];
        int/*uint32_t*/[] d_occ = new int[LZFSE_ENCODE_D_SYMBOLS];
        int/*uint32_t*/[] literal_occ = new int[LZFSE_ENCODE_LITERAL_SYMBOLS];
        FSE.fse_encoder_entry[] l_encoder = new FSE.fse_encoder_entry[LZFSE_ENCODE_L_SYMBOLS];
        FSE.fse_encoder_entry[] m_encoder = new FSE.fse_encoder_entry[LZFSE_ENCODE_M_SYMBOLS];
        FSE.fse_encoder_entry[] d_encoder = new FSE.fse_encoder_entry[LZFSE_ENCODE_D_SYMBOLS];
        FSE.fse_encoder_entry[] literal_encoder = new FSE.fse_encoder_entry[LZFSE_ENCODE_LITERAL_SYMBOLS];
        int ok = 1;
        InternalBlockHeaderObjects.lzfse_compressed_block_header_v1 header1 = new InternalBlockHeaderObjects.lzfse_compressed_block_header_v1();
        InternalBlockHeaderObjects.lzfse_compressed_block_header_v2 header2;

        // Keep initial state to be able to restore it if DST full
        byte[]/*uint8_t*/ dst0 = s.dst.array().clone();
        int/*uint32_t*/ n_literals0 = s.n_literals;

        // Add 0x00 literals until n_literals multiple of 4, since we encode 4
        // interleaved literal streams.
        while ((s.n_literals & 3) != 0) {
            int/*uint32_t*/ n = s.n_literals++;
            s.literals[n] = 0;
        }

        // Encode previous distance
        int/*uint32_t*/ d_prev = 0;
        for (int/*uint32_t*/ i = 0; i < s.n_matches; i++) {
            int/*uint32_t*/ d = s.d_values[i];
            if (d == d_prev)
                s.d_values[i] = 0;
            else
                d_prev = d;
        }

//        // Clear occurrence tables
//        memset(l_occ, 0, sizeof(l_occ));
//        memset(m_occ, 0, sizeof(m_occ));
//        memset(d_occ, 0, sizeof(d_occ));
//        memset(literal_occ, 0, sizeof(literal_occ));

        // Update occurrence tables in all 4 streams (L,M,D,literals)
        int/*uint32_t*/ l_sum = 0;
        int/*uint32_t*/ m_sum = 0;
        for (int/*uint32_t*/ i = 0; i < s.n_matches; i++) {
            int/*uint32_t*/ l = s.l_values[i];
            l_sum += l;
            l_occ[l_base_from_value(l)]++;
        }
        for (int/*uint32_t*/ i = 0; i < s.n_matches; i++) {
            int/*uint32_t*/ m = s.m_values[i];
            m_sum += m;
            m_occ[m_base_from_value(m)]++;
        }
        for (int/*uint32_t*/ i = 0; i < s.n_matches; i++)
            d_occ[d_base_from_value(s.d_values[i])]++;
        for (int/*uint32_t*/ i = 0; i < s.n_literals; i++)
            literal_occ[Byte.toUnsignedInt(s.literals[i])]++;

        // Make sure we have enough room for a _full_ V2 header
        if (s.dst.position() + InternalBlockHeaderObjects.lzfse_compressed_block_header_v2.BYTES > s.dst_end) {
            ok = 0;
            return lzfse_encode_matches_goto_END(s, ok, n_literals0, dst0);//goto END;
        }
        header2 = new InternalBlockHeaderObjects.lzfse_compressed_block_header_v2();//(lzfse_compressed_block_header_v2 *)(s->dst);

        // Setup header V1
        header1.magic = LZFSE_COMPRESSEDV1_BLOCK_MAGIC;
        header1.n_raw_bytes = m_sum + l_sum;
        header1.n_matches = s.n_matches;
        header1.n_literals = s.n_literals;

        // Normalize occurrence tables to freq tables
        fse_normalize_freq(LZFSE_ENCODE_L_STATES, LZFSE_ENCODE_L_SYMBOLS, l_occ,
                header1.l_freq);
        fse_normalize_freq(LZFSE_ENCODE_M_STATES, LZFSE_ENCODE_M_SYMBOLS, m_occ,
                header1.m_freq);
        fse_normalize_freq(LZFSE_ENCODE_D_STATES, LZFSE_ENCODE_D_SYMBOLS, d_occ,
                header1.d_freq);
        fse_normalize_freq(LZFSE_ENCODE_LITERAL_STATES, LZFSE_ENCODE_LITERAL_SYMBOLS,
                literal_occ, header1.literal_freq);

        // Compress freq tables to V2 header, and get actual size of V2 header
        final int header_size = lzfse_encode_v1_freq_table(header2, header1);
        LittleEndianByteBuffer.skip(s.dst, header_size);

        // Initialize encoder tables from freq tables
        fse_init_encoder_table(LZFSE_ENCODE_L_STATES, LZFSE_ENCODE_L_SYMBOLS,
                header1.l_freq, l_encoder);
        fse_init_encoder_table(LZFSE_ENCODE_M_STATES, LZFSE_ENCODE_M_SYMBOLS,
                header1.m_freq, m_encoder);
        fse_init_encoder_table(LZFSE_ENCODE_D_STATES, LZFSE_ENCODE_D_SYMBOLS,
                header1.d_freq, d_encoder);
        fse_init_encoder_table(LZFSE_ENCODE_LITERAL_STATES,
                LZFSE_ENCODE_LITERAL_SYMBOLS, header1.literal_freq,
                literal_encoder);

        // Encode literals
        {
            FSE.fse_out_stream64 out = new FSE.fse_out_stream64();
//            fse_out_init(&out);
            short/*fse_state*/[] state0, state1, state2, state3;
            state0 = new short[]{0};
            state1 = new short[]{0};
            state2 = new short[]{0};
            state3 = new short[]{0};

            ByteBuffer/*uint8_t*/ buf = LittleEndianByteBuffer.duplicate(s.dst);
            int/*uint32_t*/ i = s.n_literals; // I multiple of 4
            // We encode starting from the last literal so we can decode starting from
            // the first
            while (i > 0) {//FIXME i has the wrong value...should be 22376, but is 22756
                if (buf.position() + 16 > s.dst_end) {
                    ok = 0;
                    return lzfse_encode_matches_goto_END(s, ok, n_literals0, dst0);//goto END;
                } // out full
                i -= 4;
                fse_encode(state3, literal_encoder, out, s.literals[i + 3]); // 10b
                fse_encode(state2, literal_encoder, out, s.literals[i + 2]); // 10b
//#if !FSE_IOSTREAM_64
//                fse_out_flush(&out, &buf);
//#endif
                fse_encode(state1, literal_encoder, out, s.literals[i + 1]); // 10b
                fse_encode(state0, literal_encoder, out, s.literals[i + 0]); // 10b
                fse_out_flush64(out, buf);
            }
            fse_out_finish64(out, buf);

            // Update header with final encoder state
            header1.literal_bits = out.accum_nbits; // [-7, 0]
            header1.n_literal_payload_bytes = (int/*uint32_t*/) (buf.position() - s.dst.position());
            header1.literal_state[0] = state0[0];
            header1.literal_state[1] = state1[0];
            header1.literal_state[2] = state2[0];
            header1.literal_state[3] = state3[0];

            // Update state
            s.dst.position(buf.position());

        } // literals

        // Encode L,M,D
        {
            FSE.fse_out_stream64 out = new FSE.fse_out_stream64();
//            fse_out_init(&out);
            short/*fse_state*/[] l_state, m_state, d_state;
            l_state = new short[]{0};
            m_state = new short[]{0};
            d_state = new short[]{0};

            ByteBuffer/*uint8_t*/ buf = LittleEndianByteBuffer.duplicate(s.dst);
            int/*uint32_t*/ i = s.n_matches;

            // Add 8 padding bytes to the L,M,D payload
            if (buf.position() + 8 > s.dst_end) {
                ok = 0;
                return lzfse_encode_matches_goto_END(s, ok, n_literals0, dst0);//goto END;
            } // out full
            store8(buf, 0);
//            skip(buf, 8);//TODO remove this?

            // We encode starting from the last match so we can decode starting from the
            // first
            while (i > 0) {
                if (buf.position() + 16 > s.dst_end) {
                    ok = 0;
                    return lzfse_encode_matches_goto_END(s, ok, n_literals0, dst0);//goto END;
                } // out full
                i -= 1;

                // D requires 23b max
                int d_value = s.d_values[i];
                byte/*uint8_t*/ d_symbol = d_base_from_value(d_value);
                int d_nbits = d_extra_bits[d_symbol];
                int d_bits = d_value - d_base_value[d_symbol];
                fse_out_push64(out, d_nbits, d_bits);
                fse_encode(d_state, d_encoder, out, d_symbol);
//#if !FSE_IOSTREAM_64
//                fse_out_flush(&out, &buf);
//#endif

                // M requires 17b max
                int m_value = s.m_values[i];
                byte/*uint8_t*/ m_symbol = m_base_from_value(m_value);
                int m_nbits = m_extra_bits[m_symbol];
                int m_bits = m_value - m_base_value[m_symbol];
                fse_out_push64(out, m_nbits, m_bits);
                fse_encode(m_state, m_encoder, out, m_symbol);
//#if !FSE_IOSTREAM_64
//                fse_out_flush(&out, &buf);
//#endif

                // L requires 14b max
                int l_value = s.l_values[i];
                byte/*uint8_t*/ l_symbol = l_base_from_value(l_value);
                int l_nbits = l_extra_bits[l_symbol];
                int l_bits = l_value - l_base_value[l_symbol];
                fse_out_push64(out, l_nbits, l_bits);
                fse_encode(l_state, l_encoder, out, l_symbol);
                fse_out_flush64(out, buf);
            }
            fse_out_finish64(out, buf);

            // Update header with final encoder state
            header1.n_lmd_payload_bytes = (int/*uint32_t*/) (buf.position() - s.dst.position());
            header1.lmd_bits = out.accum_nbits; // [-7, 0]
            header1.l_state = l_state[0];
            header1.m_state = m_state[0];
            header1.d_state = d_state[0];

            // Update state
            s.dst.position(buf.position());

        } // L,M,D

        // Final state update, here we had enough space in DST, and are not going to
        // revert state
        s.n_literals = 0;
        s.n_matches = 0;

        // Final payload size
        header1.n_payload_bytes =
                header1.n_literal_payload_bytes + header1.n_lmd_payload_bytes;

        // Encode state info in V2 header (we previously encoded the tables, now we
        // set the other fields)
        lzfse_encode_v1_state(header2, header1);
        update_dst_header(s.dst, header2, header_size);

        return lzfse_encode_matches_goto_END(s, ok, n_literals0, dst0);
    }

    private static void update_dst_header(ByteBuffer dst, final InternalBlockHeaderObjects.lzfse_compressed_block_header_v2 header, final int header_size) {
        final byte[] header_array = InternalBlockHeaderObjects.lzfse_compressed_block_header_v2.toByteBuffer(header).array();
        final int current_position = dst.position();
        dst.position(0);
        dst.put(header_array, 0, header_size);
        dst.position(current_position);
    }

    private static int lzfse_encode_matches_goto_END(final InternalStateObjects.lzfse_encoder_state s, final int ok, final int/*uint32_t*/ n_literals0, final byte/*uint8_t*/[] dst0) {
        if (0 == ok) {
            // Revert state, DST was full

            // Revert the d_prev encoding
            int/*uint32_t*/ d_prev = 0;
            for (int/*uint32_t*/ i = 0; i < s.n_matches; i++) {
                int/*uint32_t*/ d = s.d_values[i];
                if (d == 0)
                    s.d_values[i] = d_prev;
                else
                    d_prev = d;
            }

            // Revert literal count
            s.n_literals = n_literals0;

            // Revert DST
            s.dst.clear().put(dst0);

            return LZFSE_STATUS_DST_FULL; // DST full
        }

        return LZFSE_STATUS_OK;
    }

    /*! @abstract Push a L,M,D match into the STATE.
     * @return LZFSE_STATUS_OK if OK.
     * @return LZFSE_STATUS_DST_FULL if the match can't be pushed, meaning one of
     * the buffers is full. In that case the state is not modified. */
    static int lzfse_push_lmd(InternalStateObjects.lzfse_encoder_state s, int/*uint32_t*/ L,
                              int/*uint32_t*/ M, int/*uint32_t*/ D) {
        // Check if we have enough space to push the match (we add some margin to copy
        // literals faster here, and round final count later)
        if (s.n_matches + 1 + 8 > LZFSE_MATCHES_PER_BLOCK)
            return LZFSE_STATUS_DST_FULL; // state full
        if (s.n_literals + L + 16 > LZFSE_LITERALS_PER_BLOCK)
            return LZFSE_STATUS_DST_FULL; // state full

        // Store match
        int/*uint32_t*/ n = s.n_matches++;
        s.l_values[n] = L;
        s.m_values[n] = M;
        s.d_values[n] = D;

        // Store literals
        ByteBuffer/*uint8_t*/ dst = LittleEndianByteBuffer.wrap(s.literals, s.n_literals);
        final ByteBuffer/*uint8_t*/ src = LittleEndianByteBuffer.duplicate(s.src, Math.toIntExact(s.src_literal));
        final int dst_end = dst.position() + L;//uint8_t * dst_end = dst + L;
        if (s.src_literal + L + 16 > s.src_end) {
            // Careful at the end of SRC, we can't read 16 bytes
            if (L > 0)
                dst.put(src.array(), src.position(), L);//memcpy(dst, src, L);
        } else {
            copy16(dst, src);
//            skip(dst, 16);
//            skip(src, 16);
            while (dst.position() < dst_end) {
                copy16(dst, src);
//                skip(dst, 16);
//                skip(src, 16);
            }
        }
        DBG_lzfse_push_lmd++;
        s.n_literals += L;

        // Update state
        s.src_literal += L + M;

        return LZFSE_STATUS_OK;
    }

    static int DBG_lzfse_push_lmd = 0;

    /*! @abstract Split MATCH into one or more L,M,D parts, and push to STATE.
     * @return LZFSE_STATUS_OK if OK.
     * @return LZFSE_STATUS_DST_FULL if the match can't be pushed, meaning one of the
     * buffers is full. In that case the state is not modified. */
    static int lzfse_push_match(InternalStateObjects.lzfse_encoder_state s, final Internal.lzfse_match match) {
        // Save the initial n_matches, n_literals, src_literal
        int/*uint32_t*/ n_matches0 = s.n_matches;
        int/*uint32_t*/ n_literals0 = s.n_literals;
        long/*lzfse_offset*/ src_literals0 = s.src_literal;

        // L,M,D
        int/*uint32_t*/ L = (int/*uint32_t*/) (match.pos - s.src_literal); // literal count
        int/*uint32_t*/ M = match.length;                                  // match length
        int/*uint32_t*/ D = (int/*uint32_t*/) (match.pos - match.ref);     // match distance
        int ok = 1;

        // Split L if too large
        while (L > LZFSE_ENCODE_MAX_L_VALUE) {
            if (lzfse_push_lmd(s, LZFSE_ENCODE_MAX_L_VALUE, 0, 1) != 0) {
                ok = 0;
                return lzfse_push_match_goto_END(s, ok, n_matches0, n_literals0, n_literals0);//goto END;
            } // take D=1 because most frequent, but not actually used
            L -= LZFSE_ENCODE_MAX_L_VALUE;
        }

        // Split if M too large
        while (M > LZFSE_ENCODE_MAX_M_VALUE) {
            if (lzfse_push_lmd(s, L, LZFSE_ENCODE_MAX_M_VALUE, D) != 0) {
                ok = 0;
                return lzfse_push_match_goto_END(s, ok, n_matches0, n_literals0, n_literals0);//goto END;
            }
            L = 0;
            M -= LZFSE_ENCODE_MAX_M_VALUE;
        }

        // L,M in range
        if (L > 0 || M > 0) {
            if (lzfse_push_lmd(s, L, M, D) != 0) {
                ok = 0;
                return lzfse_push_match_goto_END(s, ok, n_matches0, n_literals0, n_literals0);//goto END;
            }
//            L = M = 0;
//            (void) L;
//            (void) M; // dead stores//TODO do something with these dead stores?
        }

        return lzfse_push_match_goto_END(s, ok, n_matches0, n_literals0, n_literals0);
    }

    private static int lzfse_push_match_goto_END(final InternalStateObjects.lzfse_encoder_state s, final int ok,
                                                 final int/*uint32_t*/ n_matches0,
                                                 final int/*uint32_t*/ n_literals0,
                                                 final long/*lzfse_offset*/ src_literals0) {
        if (0 == ok) {
            // Revert state
            s.n_matches = n_matches0;
            s.n_literals = n_literals0;
            s.src_literal = src_literals0;

            return LZFSE_STATUS_DST_FULL; // state tables full
        }

        return LZFSE_STATUS_OK; // OK
    }

    /*! @abstract Backend: add MATCH to state S. Encode block if necessary, when
     * state is full.
     * @return LZFSE_STATUS_OK if OK.
     * @return LZFSE_STATUS_DST_FULL if the match can't be added, meaning one of the
     * buffers is full. In that case the state is not modified. */
    static int lzfse_backend_match(InternalStateObjects.lzfse_encoder_state s,
                                   final Internal.lzfse_match match) {
        // Try to push the match in state
        if (lzfse_push_match(s, match) == LZFSE_STATUS_OK)
            return LZFSE_STATUS_OK; // OK, match added to state

        // Here state tables are full, try to emit block
        if (lzfse_encode_matches(s) != LZFSE_STATUS_OK)
            return LZFSE_STATUS_DST_FULL; // DST full, match not added

        // Here block has been emitted, re-try to push the match in state
        return lzfse_push_match(s, match);
    }

    /*! @abstract Backend: add L literals to state S. Encode block if necessary,
     * when state is full.
     * @return LZFSE_STATUS_OK if OK.
     * @return LZFSE_STATUS_DST_FULL if the literals can't be added, meaning one of
     * the buffers is full. In that case the state is not modified. */
    static int lzfse_backend_literals(InternalStateObjects.lzfse_encoder_state s, long/*lzfse_offset*/ L) {
        // Create a fake match with M=0, D=1
        Internal.lzfse_match match = new Internal.lzfse_match();
        long/*lzfse_offset*/ pos = s.src_literal + L;
        match.pos = pos;
        match.ref = match.pos - 1;
        match.length = 0;
        return lzfse_backend_match(s, match);
    }

    /*! @abstract Backend: flush final block, and emit end of stream
     * @return LZFSE_STATUS_OK if OK.
     * @return LZFSE_STATUS_DST_FULL if either the final block, or the end-of-stream
     * can't be added, meaning one of the buffers is full. If the block was emitted,
     * the state is updated to reflect this. Otherwise, it is left unchanged. */
    static int lzfse_backend_end_of_stream(InternalStateObjects.lzfse_encoder_state s) {
        // Final match triggers write, otherwise emit blocks when we have enough
        // matches stored
        if (lzfse_encode_matches(s) != LZFSE_STATUS_OK)
            return LZFSE_STATUS_DST_FULL; // DST full

        // Emit end-of-stream block
        if (s.dst.position() + 4 > s.dst_end)
            return LZFSE_STATUS_DST_FULL; // DST full
        store4(s.dst, LZFSE_ENDOFSTREAM_BLOCK_MAGIC);
//        s.dst += 4;

        return LZFSE_STATUS_OK; // OK
    }
}
