package lzfse;

import java.nio.ByteBuffer;

import static lzfse.EncodeBackEnd.lzfse_backend_end_of_stream;
import static lzfse.EncodeBackEnd.lzfse_backend_literals;
import static lzfse.EncodeBackEnd.lzfse_backend_match;
import static lzfse.EncodeBase.LZFSE_ENCODE_MAX_MATCH_LENGTH;
import static lzfse.EncodeBase.hashX;
import static lzfse.Internal.LZFSE_ENCODE_MAX_D_VALUE;
import static lzfse.Internal.LZFSE_ENCODE_MAX_L_VALUE;
import static lzfse.Internal.LZFSE_STATUS_DST_FULL;
import static lzfse.Internal.LZFSE_STATUS_OK;
import static lzfse.Internal.__builtin_ctzll;
import static lzfse.Internal.load4;
import static lzfse.Internal.load8;
import static lzfse.Tunables.LZFSE_ENCODE_GOOD_MATCH;
import static lzfse.Tunables.LZFSE_ENCODE_HASH_WIDTH;

public class EncodeFrontEnd {

    static int lzfse_encode_base(InternalStateObjects.lzfse_encoder_state s) {
        Internal.lzfse_history_set[] history_table = s.history_table;
        Internal.lzfse_history_set hashLine;
        Internal.lzfse_history_set newH;
        final Internal.lzfse_match NO_MATCH = new Internal.lzfse_match();//{0};
        int ok = 1;

        newH = new Internal.lzfse_history_set();//memset(&newH, 0x00, sizeof(newH));

        // 8 byte padding at end of buffer
        s.src_encode_end = s.src_end - 8;
        for (; s.src_encode_i < s.src_encode_end; s.src_encode_i++) {
            long/*lzfse_offset*/ pos = s.src_encode_i; // pos >= 0

            if (s.src_encode_i == 3865) {
                int DBG = 0;
            }
            // Load 4 byte value and get hash line
            int/*uint32_t*/ x = load4(LittleEndianByteBuffer.duplicate(s.src, Math.toIntExact(pos)));
            hashLine = history_table[hashX(x)];
            Internal.lzfse_history_set h = hashLine;

            // Prepare next hash line (component 0 is the most recent) to prepare new
            // entries (stored later)
            {
                newH.pos[0] = (int) pos;
                for (int k = 0; k < LZFSE_ENCODE_HASH_WIDTH - 1; k++)
                    newH.pos[k + 1] = h.pos[k];
                newH.value[0] = x;
                for (int k = 0; k < LZFSE_ENCODE_HASH_WIDTH - 1; k++)
                    newH.value[k + 1] = h.value[k];
            }

            // Do not look for a match if we are still covered by a previous match
            if (pos < s.src_literal) {
                hashLine.set(newH);
                continue;//goto END_POS;
            }

            // Search best incoming match
            Internal.lzfse_match incoming = new Internal.lzfse_match(pos, 0, 0);

            // Check for matches.  We consider matches of length >= 4 only.
            for (int k = 0; k < LZFSE_ENCODE_HASH_WIDTH; k++) {
                int/*uint32_t*/ d = h.value[k] ^ x;
                if (d != 0)
                    continue; // no 4 byte match
                int ref = h.pos[k];
                if (ref + LZFSE_ENCODE_MAX_D_VALUE < pos)
                    continue; // too far

                final ByteBuffer /*uint8_t*/ src_ref = LittleEndianByteBuffer.duplicate(s.src, ref);
                final ByteBuffer /*uint8_t*/  src_pos = LittleEndianByteBuffer.duplicate(s.src, Math.toIntExact(pos));
                int/*uint32_t*/ length = 4;
                int/*uint32_t*/ maxLength =
                        (int/*uint32_t*/) (s.src_end - pos - 8); // ensure we don't hit the end of SRC
                while (length < maxLength) {
                    long/*uint64_t*/ dee = load8(LittleEndianByteBuffer.duplicate(src_ref, length)) ^ load8(LittleEndianByteBuffer.duplicate(src_pos, length));
                    if (dee == 0) {
                        length += 8;
                        continue;
                    }

                    length +=
                            (__builtin_ctzll(dee) >>> 3); // ctzll must be called only with D != 0
                    break;
                }
                if (length > incoming.length) {
                    incoming.length = length;
                    incoming.ref = ref;
                } // keep if longer
            }

            // No incoming match?
            if (incoming.length == 0) {
                // We may still want to emit some literals here, to not lag too far behind
                // the current search point, and avoid
                // ending up with a literal block not fitting in the state.
                long/*lzfse_offset*/ n_literals = pos - s.src_literal;
                // The threshold here should be larger than a couple of MAX_L_VALUE, and
                // much smaller than LITERALS_PER_BLOCK
                if (n_literals > 8 * LZFSE_ENCODE_MAX_L_VALUE) {
                    // Here, we need to consume some literals. Emit pending match if there
                    // is one
                    if (s.pending.length > 0) {
                        if (lzfse_backend_match(s, s.pending) != LZFSE_STATUS_OK) {
                            ok = 0;
                            return lzfse_encode_base_goto_END(ok);//goto END;
                        }
                        s.pending = NO_MATCH;
                    } else {
                        // No pending match, emit a full LZFSE_ENCODE_MAX_L_VALUE block of
                        // literals
                        if (lzfse_backend_literals(s, LZFSE_ENCODE_MAX_L_VALUE) !=
                                LZFSE_STATUS_OK) {
                            ok = 0;
                            return lzfse_encode_base_goto_END(ok);//goto END;
                        }
                    }
                }
                hashLine.set(newH);
                continue;//goto END_POS;
            }

            // Limit match length (it may still be expanded backwards, but this is
            // bounded by the limit on literals we tested before)
            if (incoming.length > LZFSE_ENCODE_MAX_MATCH_LENGTH) {
                incoming.length = LZFSE_ENCODE_MAX_MATCH_LENGTH;
            }

            // Expand backwards (since this is expensive, we do this for the best match
            // only)
            while (incoming.pos > s.src_literal && incoming.ref > 0 &&
                    s.src.get(Math.toIntExact(incoming.ref - 1)) == s.src.get(Math.toIntExact(incoming.pos - 1))) {
                incoming.pos--;
                incoming.ref--;
            }
            incoming.length += pos - incoming.pos; // update length after expansion

            // Match filtering heuristic (from LZVN). INCOMING is always defined here.

            // Incoming is 'good', emit incoming
            if (incoming.length >= LZFSE_ENCODE_GOOD_MATCH) {
                if (lzfse_backend_match(s, incoming) != LZFSE_STATUS_OK) {
                    ok = 0;
                    return lzfse_encode_base_goto_END(ok);//goto END;
                }
                s.pending = NO_MATCH;
                hashLine.set(newH);
                continue;//goto END_POS;
            }

            // No pending, keep incoming
            if (s.pending.length == 0) {
                s.pending = incoming;
                hashLine.set(newH);
                continue;//goto END_POS;
            }

            // No overlap, emit pending, keep incoming
            if (s.pending.pos + s.pending.length <= incoming.pos) {
                if (lzfse_backend_match(s, s.pending) != LZFSE_STATUS_OK) {
                    ok = 0;
                    return lzfse_encode_base_goto_END(ok);//goto END;
                }
                s.pending = incoming;
                hashLine.set(newH);
                continue;//goto END_POS;
            }

            // Overlap: emit longest
            if (incoming.length > s.pending.length) {
                if (lzfse_backend_match(s, incoming) != LZFSE_STATUS_OK) {
                    ok = 0;
                    return lzfse_encode_base_goto_END(ok);//goto END;
                }
            } else {
                if (lzfse_backend_match(s, s.pending) != LZFSE_STATUS_OK) {
                    ok = 0;
                    return lzfse_encode_base_goto_END(ok);//goto END;
                }
            }
            s.pending = NO_MATCH;

            END_POS:
            // We are done with this src_encode_i.
            // Update state now (s.pending has already been updated).
            hashLine.set(newH);
        }

        END:
        return lzfse_encode_base_goto_END(ok);
    }

    private static int lzfse_encode_base_goto_END(final int ok) {
        return ok != 0 ? LZFSE_STATUS_OK : LZFSE_STATUS_DST_FULL;
    }

    static int lzfse_encode_finish(InternalStateObjects.lzfse_encoder_state s) {
        final Internal.lzfse_match NO_MATCH = new Internal.lzfse_match();//{0};

        // Emit pending match
        if (s.pending.length > 0) {
            if (lzfse_backend_match(s, s.pending) != LZFSE_STATUS_OK)
                return LZFSE_STATUS_DST_FULL;
            s.pending = NO_MATCH;
        }

        // Emit final literals if any
        long/*lzfse_offset*/ L = s.src_end - s.src_literal;
        if (L > 0) {
            if (lzfse_backend_literals(s, L) != LZFSE_STATUS_OK)
                return LZFSE_STATUS_DST_FULL;
        }

        // Emit all matches, and end-of-stream block
        if (lzfse_backend_end_of_stream(s) != LZFSE_STATUS_OK)
            return LZFSE_STATUS_DST_FULL;

        return LZFSE_STATUS_OK;
    }
}
