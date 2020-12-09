package lzfse;

import static lzfse.Internal.LZFSE_ENCODE_HASH_VALUES;
import static lzfse.Internal.LZFSE_ENCODE_MAX_D_VALUE;
import static lzfse.Internal.LZFSE_STATUS_OK;
import static lzfse.Tunables.LZFSE_ENCODE_HASH_WIDTH;

public class EncodeStateManagement {

    /*! @abstract Initialize state:
     * @code
     * - hash table with all invalid pos, and value 0.
     * - pending match to NO_MATCH.
     * - src_literal to 0.
     * - d_prev to 0.
     @endcode
     * @return LZFSE_STATUS_OK */
    static int lzfse_encode_init(InternalStateObjects.lzfse_encoder_state s) {
        final Internal.lzfse_match NO_MATCH = new Internal.lzfse_match();//{0};
        Internal.lzfse_history_set line = new Internal.lzfse_history_set();
        for (int i = 0; i < LZFSE_ENCODE_HASH_WIDTH; i++) {
            line.pos[i] = -4 * LZFSE_ENCODE_MAX_D_VALUE; // invalid pos
            line.value[i] = 0;
        }
        // Fill table
        for (int i = 0; i < LZFSE_ENCODE_HASH_VALUES; i++)
            s.history_table[i] = new Internal.lzfse_history_set(line);
        s.pending = NO_MATCH;
        s.src_literal = 0;

        return LZFSE_STATUS_OK; // OK
    }

    /*! @abstract Translate state \p src forward by \p delta > 0.
     * Offsets in \p src are updated backwards to point to the same positions.
     * @return  LZFSE_STATUS_OK */
    static int lzfse_encode_translate(InternalStateObjects.lzfse_encoder_state s, long /*lzfse_offset*/ delta) {
        assert (delta >= 0);
        if (delta == 0)
            return LZFSE_STATUS_OK; // OK

        // SRC
        LittleEndianByteBuffer.skip(s.src, Math.toIntExact(delta));

        // Offsets in SRC
        s.src_end -= delta;
        s.src_encode_i -= delta;
        s.src_encode_end -= delta;
        s.src_literal -= delta;

        // Pending match
        s.pending.pos -= delta;
        s.pending.ref -= delta;

        // history_table positions, translated, and clamped to invalid pos
        int invalidPos = -4 * LZFSE_ENCODE_MAX_D_VALUE;
        for (int i = 0; i < LZFSE_ENCODE_HASH_VALUES; i++) {
            int[] p = s.history_table[i].pos;
            for (int j = 0; j < LZFSE_ENCODE_HASH_WIDTH; j++) {
                long/*lzfse_offset*/ newPos = p[j] - delta; // translate
                p[j] = (newPos < invalidPos) ? invalidPos : (int) newPos; // clamp
            }
        }

        return LZFSE_STATUS_OK; // OK
    }
}
