package lzfse;

import lzfse.InternalStateObjects.lzfse_decoder_state;

import java.nio.ByteBuffer;

import static lzfse.DecodeBase.lzfse_decode;
import static lzfse.Internal.LZFSE_STATUS_DST_FULL;
import static lzfse.Internal.LZFSE_STATUS_OK;

public class Decode {
    private Decode() {
    }

    public static int/*size_t*/ lzfse_decode_scratch_size() {
        return InternalStateObjects.lzfse_decoder_state.BYTES;
    }

    private static int/*size_t*/ lzfse_decode_buffer_with_scratch(ByteBuffer/*uint8_t*/ dst_buffer,
                                                                  long/*size_t*/ dst_size, final ByteBuffer/*uint8_t*/ src_buffer,
                                                                  long/*size_t*/ src_size, lzfse_decoder_state scratch_buffer) {
        lzfse_decoder_state s = (lzfse_decoder_state) scratch_buffer;
//        memset(s, 0x00, sizeof( * s));

        // Initialize state
        s.src = LittleEndianByteBuffer.duplicate(src_buffer);
        s.src_begin = src_buffer.position();
        s.src_end = Math.toIntExact(src_size);
        s.dst = LittleEndianByteBuffer.duplicate(dst_buffer);
        s.dst_begin = dst_buffer.position();
        s.dst_end = Math.toIntExact(dst_size);//TODO check all these Match.toIntExact()

        // Decode
        int status = lzfse_decode(s);
        if (status == LZFSE_STATUS_DST_FULL)
            return Math.toIntExact(dst_size);
        if (status != LZFSE_STATUS_OK)
            return 0;                           // failed
        return /*(size_t)*/ (s.dst.position() - dst_buffer.position()); // bytes written
    }

    public static int/*size_t*/ lzfse_decode_buffer(ByteBuffer/*uint8_t*/ dst_buffer, long/*size_t*/ dst_size,
                                                    final ByteBuffer /*uint8_t*/  src_buffer,
                                                    long/*size_t*/ src_size/*, lzfse_decoder_state scratch_buffer*/) {
//        int has_malloc = 0;
        int/*size_t*/ ret = 0;

        // Deal with the possible NULL pointer
//        if (scratch_buffer == null) {
        // +1 in case scratch size could be zero
        lzfse_decoder_state scratch_buffer = new lzfse_decoder_state();//malloc(lzfse_decode_scratch_size() + 1);
//            has_malloc = 1;
//        }
        if (scratch_buffer == null)
            return 0;
        ret = lzfse_decode_buffer_with_scratch(dst_buffer,
                dst_size, src_buffer,
                src_size, scratch_buffer);
//        if (has_malloc)
//            free(scratch_buffer);
        return ret;
    }
}
