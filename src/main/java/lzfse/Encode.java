package lzfse;

import lzfse.InternalBlockHeaderObjects.lzvn_compressed_block_header;
import lzfse.InternalBlockHeaderObjects.uncompressed_block_header;
import lzfse.InternalStateObjects.lzfse_encoder_state;

import java.nio.ByteBuffer;

import static lzfse.EncodeFrontEnd.lzfse_encode_base;
import static lzfse.EncodeFrontEnd.lzfse_encode_finish;
import static lzfse.EncodeStateManagement.lzfse_encode_init;
import static lzfse.EncodeStateManagement.lzfse_encode_translate;
import static lzfse.Internal.LZFSE_STATUS_OK;
import static lzfse.Internal.LZVN_ENCODE_MIN_SRC_SIZE;
import static lzfse.Internal.store4;
import static lzfse.InternalBlockHeaderObjects.LZFSE_COMPRESSEDLZVN_BLOCK_MAGIC;
import static lzfse.InternalBlockHeaderObjects.LZFSE_ENDOFSTREAM_BLOCK_MAGIC;
import static lzfse.InternalBlockHeaderObjects.LZFSE_UNCOMPRESSED_BLOCK_MAGIC;
import static lzfse.Tunables.LZFSE_ENCODE_LZVN_THRESHOLD;

public class Encode {
    private Encode() {
    }

    public static long lzfse_encode_scratch_size() {
        long s1 = InternalStateObjects.lzfse_encoder_state.BYTES;
        long s2 = -1;//TODO lzvn_encode_scratch_size();
        return Math.max(s1, s2); // max(lzfse,lzvn)
    }

    static long lzfse_encode_buffer_with_scratch(ByteBuffer dst_buffer,
                                                 long dst_size, final ByteBuffer src_buffer,
                                                 long src_size, lzfse_encoder_state scratch_buffer) {
        final long original_size = src_size;

        // If input is really really small, go directly to uncompressed buffer
        // (because LZVN will refuse to encode it, and we will report a failure)
        if (src_size < LZVN_ENCODE_MIN_SRC_SIZE)
            return try_uncompressed(original_size, dst_size, src_size, dst_buffer, src_buffer);

        // If input is too small, try encoding with LZVN
        if (src_size < LZFSE_ENCODE_LZVN_THRESHOLD) {
            // need header + end-of-stream marker
            long extra_size = 4 + lzvn_compressed_block_header.BYTES;
            if (dst_size <= extra_size)
                return try_uncompressed(original_size, dst_size, src_size, dst_buffer, src_buffer); // DST is really too small, give up

            long sz = -1;//TODO lzvn_encode_buffer(
            //TODO dst_buffer + lzvn_compressed_block_header.BYTES,
            //TODO dst_size - extra_size, src_buffer, src_size, scratch_buffer);
            if (sz == 0 || sz >= src_size)
                return try_uncompressed(original_size, dst_size, src_size, dst_buffer, src_buffer); // failed, or no compression, fall back to
            // uncompressed block

            // If we could encode, setup header and end-of-stream marker (we left room
            // for them, no need to test)
            lzvn_compressed_block_header header = new lzvn_compressed_block_header();
            header.magic = LZFSE_COMPRESSEDLZVN_BLOCK_MAGIC;
            header.n_raw_bytes = (int/*uint32_t*/) src_size;
            header.n_payload_bytes = (int/*uint32_t*/) sz;
            dst_buffer.put(header.toByteBuffer());//memcpy(dst_buffer, & header, header.BYTES);
            store4(LittleEndianByteBuffer.duplicate(dst_buffer, Math.toIntExact(lzvn_compressed_block_header.BYTES + sz)),
                    LZFSE_ENDOFSTREAM_BLOCK_MAGIC);

            return sz + extra_size;
        }

        // Try encoding with LZFSE
        {
            lzfse_encoder_state state = scratch_buffer;
            state.clear();//memset(state, 0x00, state.BYTES);
            if (lzfse_encode_init(state) != LZFSE_STATUS_OK)
                return try_uncompressed(original_size, dst_size, src_size, dst_buffer, src_buffer);
            state.dst = LittleEndianByteBuffer.duplicate(dst_buffer);
            state.dst_begin = dst_buffer.position();
            state.dst_end = Math.toIntExact(dst_size);
            state.src = LittleEndianByteBuffer.duplicate(src_buffer);
            state.src_encode_i = 0;

            if (src_size >= 0xffffffffL) {
                //  lzfse only uses 32 bits for offsets internally, so if the input
                //  buffer is really huge, we need to process it in smaller chunks.
                //  Note that we switch over to this path for sizes much smaller
                //  2GB because it's actually faster to change algorithms well before
                //  it's necessary for correctness.
                //  The first chunk, we just process normally.
                final long encoder_block_size = 262144;
                state.src_end = encoder_block_size;
                if (lzfse_encode_base(state) != LZFSE_STATUS_OK)
                    return try_uncompressed(original_size, dst_size, src_size, dst_buffer, src_buffer);
                src_size -= encoder_block_size;
                while (src_size >= encoder_block_size) {
                    //  All subsequent chunks require a translation to keep the offsets
                    //  from getting too big.  Note that we are always going from
                    //  encoder_block_size up to 2*encoder_block_size so that the
                    //  offsets remain positive (as opposed to resetting to zero and
                    //  having negative offsets).
                    state.src_end = 2 * encoder_block_size;
                    if (lzfse_encode_base(state) != LZFSE_STATUS_OK)
                        return try_uncompressed(original_size, dst_size, src_size, dst_buffer, src_buffer);
                    lzfse_encode_translate(state, encoder_block_size);
                    src_size -= encoder_block_size;
                }
                //  Set the end for the final chunk.
                state.src_end = encoder_block_size + (long/*lzfse_offset*/) src_size;
            }
            //  If the source buffer is small enough to use 32-bit offsets, we simply
            //  encode the whole thing in a single chunk.
            else
                state.src_end = (long/*lzfse_offset*/) src_size;
            //  This is either the trailing chunk (if the source file is huge), or
            //  the whole source file.
            if (lzfse_encode_base(state) != LZFSE_STATUS_OK)
                return try_uncompressed(original_size, dst_size, src_size, dst_buffer, src_buffer);
            if (lzfse_encode_finish(state) != LZFSE_STATUS_OK)
                return try_uncompressed(original_size, dst_size, src_size, dst_buffer, src_buffer);
            //  No error occured, return compressed size.
            return state.dst.position() - dst_buffer.position();
        }
    }

    public static long lzfse_encode_buffer(ByteBuffer dst_buffer, long dst_size,
                                           final ByteBuffer src_buffer,
                                           long src_size/*, lzfse_encoder_state scratch_buffer*/) {  //TODO remove scratch_buffer
        int has_malloc = 0;
        long ret = 0;

        // Deal with the possible NULL pointer
//        if (scratch_buffer == null) {
        // +1 in case scratch size could be zero
        lzfse_encoder_state scratch_buffer = new lzfse_encoder_state();// malloc(lzfse_encode_scratch_size() + 1);//TODO lzfse_encoder_state or lzvn_encoder_state
        has_malloc = 1;
//        }
        if (scratch_buffer == null)
            return 0;
        ret = lzfse_encode_buffer_with_scratch(dst_buffer,
                dst_size, src_buffer,
                src_size, scratch_buffer);
//        if (has_malloc)
//            free(scratch_buffer);
        return ret;
    }

    static long try_uncompressed(long original_size, long dst_size, long src_size, ByteBuffer dst_buffer, ByteBuffer src_buffer) {
        if (original_size + 12 <= dst_size && original_size < Integer.MAX_VALUE) {
            uncompressed_block_header header = new uncompressed_block_header(LZFSE_UNCOMPRESSED_BLOCK_MAGIC, (int/*uint32_t*/) src_size);
            int /*uint8_t*/ dst_end = dst_buffer.position();
            dst_buffer.put(header.toByteBuffer());//memcpy(dst_end, & header, sizeof header);
//            dst_end += sizeof header;
            dst_buffer.put(src_buffer.array(), src_buffer.position(), Math.toIntExact(original_size));// memcpy(dst_end, src_buffer, original_size);
//            dst_end += original_size;
            store4(dst_buffer, LZFSE_ENDOFSTREAM_BLOCK_MAGIC);
//            dst_end += 4;
            return dst_buffer.position() - dst_end;
        }

        //  Otherwise, there's nothing we can do, so return zero.
        return 0;
    }
}
