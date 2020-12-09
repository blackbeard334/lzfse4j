package lzfse;

import java.nio.ByteBuffer;
import java.util.Arrays;

/** MARK: - Encoder and Decoder state objects */
public class InternalStateObjects {
    /*! @abstract Encoder state object. */
    static class lzfse_encoder_state {
        static final long BYTES = 684384;

        //  Pointer to first byte of the source buffer.
        /*uint8_t*/ ByteBuffer src;
        //  Length of the source buffer in bytes. Note that this is not a size_t,
        //  but rather lzfse_offset, which is a signed type. The largest
        //  representable buffer is 2GB, but arbitrarily large buffers may be
        //  handled by repeatedly calling the encoder function and "translating"
        //  the state between calls. When doing this, it is beneficial to use
        //  blocks smaller than 2GB in order to maintain residency in the last-level
        //  cache. Consult the implementation of lzfse_encode_buffer for details.
        long src_end;
        //  Offset of the first byte of the next literal to encode in the source
        //  buffer.
        long src_literal;
        //  Offset of the byte currently being checked for a match.
        long src_encode_i;
        //  The last byte offset to consider for a match.  In some uses it makes
        //  sense to use a smaller offset than src_end.
        long src_encode_end;
        //  Pointer to the next byte to be written in the destination buffer.
        /*uint8_t*/ ByteBuffer dst;
        //  Pointer to the first byte of the destination buffer.
        /*uint8_t*/ int        dst_begin;
        //  Pointer to one byte past the end of the destination buffer.
        /*uint8_t*/ int        dst_end;
        //  Pending match; will be emitted unless a better match is found.
        Internal.lzfse_match pending;
        //  The number of matches written so far. Note that there is no problem in
        //  using a 32-bit field for this quantity, because the state already limits
        //  us to at most 2GB of data; there cannot possibly be more matches than
        //  there are bytes in the input.
        /*uint32_t*/ int    n_matches;
        //  The number of literals written so far.
        /*uint32_t*/ int    n_literals;
        //  Lengths of found literals.
        /*uint32_t*/ int[]  l_values = new int[Internal.LZFSE_MATCHES_PER_BLOCK];
        //  Lengths of found matches.
        /*uint32_t*/ int[]  m_values = new int[Internal.LZFSE_MATCHES_PER_BLOCK];//TODO     check unsigned arrays?
        //  Distances of found matches.
        /*uint32_t*/ int[]  d_values = new int[Internal.LZFSE_MATCHES_PER_BLOCK];
        //  Concatenated literal bytes.
        /*uint8_t*/  byte[] literals = new byte[Internal.LZFSE_LITERALS_PER_BLOCK];
        //  History table used to search for matches. Each entry of the table
        //  corresponds to a group of four byte sequences in the input stream
        //  that hash to the same value.
        Internal.lzfse_history_set[] history_table = new Internal.lzfse_history_set[Internal.LZFSE_ENCODE_HASH_VALUES];

        void clear() {
            if (this.src != null)
                this.src.clear();
            this.src_end = 0;
            this.src_literal = 0;
            this.src_encode_i = 0;
            this.src_encode_end = 0;
            if (this.dst != null)
                this.dst.clear();
            this.dst_begin = 0;
            this.dst_end = 0;
            this.pending = new Internal.lzfse_match();
            this.n_matches = 0;
            this.n_literals = 0;
            Arrays.fill(this.l_values, 0);
            Arrays.fill(this.m_values, 0);
            Arrays.fill(this.d_values, 0);
            Arrays.fill(this.literals, (byte) 0);
            Arrays.fill(this.history_table, null);
        }
    }

    /*! @abstract Decoder state object for lzfse compressed blocks. */
    static class lzfse_compressed_block_decoder_state {
        static final int BYTES = 47296;

        //  Number of matches remaining in the block.
        /*uint32_t*/ int    n_matches;
        //  Number of bytes used to encode L, M, D triplets for the block.
        /*uint32_t*/ int    n_lmd_payload_bytes;
        //  Pointer to the next literal to emit.
        /*uint8_t*/  byte[] current_literal;
        //  L, M, D triplet for the match currently being emitted. This is used only
        //  if we need to restart after reaching the end of the destination buffer in
        //  the middle of a literal or match.
        int l_value, m_value, d_value;
        //  FSE stream object.
        FSE.fse_in_stream64 lmd_in_stream;
        //  Offset of L,M,D encoding in the input buffer. Because we read through an
        //  FSE stream *backwards* while decoding, this is decremented as we move
        //  through a block.
        /*uint32_t*/ int   lmd_in_buf;
        //  The current state of the L, M, and D FSE decoders.
        /*uint16_t*/ short l_state, m_state, d_state;
        //  Internal FSE decoder tables for the current block. These have
        //  alignment forced to 8 bytes to guarantee that a single state's
        //  entry cannot span two cachelines.
        FSE.fse_value_decoder_entry[] l_decoder       = new FSE.fse_value_decoder_entry[Internal.LZFSE_ENCODE_L_STATES];// __attribute__((__aligned__(8)));
        FSE.fse_value_decoder_entry[] m_decoder       = new FSE.fse_value_decoder_entry[Internal.LZFSE_ENCODE_M_STATES];// __attribute__((__aligned__(8)));
        FSE.fse_value_decoder_entry[] d_decoder       = new FSE.fse_value_decoder_entry[Internal.LZFSE_ENCODE_D_STATES];// __attribute__((__aligned__(8)));
        int[]                         literal_decoder = new int[Internal.LZFSE_ENCODE_LITERAL_STATES];
        //  The literal stream for the block, plus padding to allow for faster copy
        //  operations.
        /*uint8_t*/ byte[] literals = new byte[Internal.LZFSE_LITERALS_PER_BLOCK + 64];
    }

    //  Decoder state object for uncompressed blocks.
    static class uncompressed_block_decoder_state {
        /*uint32_t*/ int n_raw_bytes;
    }

    /*! @abstract Decoder state object for lzvn-compressed blocks. */
    static class lzvn_compressed_block_decoder_state {
        /*uint32_t*/ int n_raw_bytes;
        /*uint32_t*/ int n_payload_bytes;
        /*uint32_t*/ int d_prev;
    }

    /*! @abstract Decoder state object. */
    public static class lzfse_decoder_state {
        public static final int BYTES = 47368;

        //  Pointer to next byte to read from source buffer (this is advanced as we
        //  decode; src_begin describe the buffer and do not change).
        /*uint8_t*/ ByteBuffer src;
        //  Pointer to first byte of source buffer.
        /*uint8_t*/ int        src_begin;
        //  Pointer to one byte past the end of the source buffer.
        /*uint8_t*/ int        src_end;
        //  Pointer to the next byte to write to destination buffer (this is advanced
        //  as we decode; dst_begin and dst_end describe the buffer and do not change).
        /*uint8_t*/ ByteBuffer dst;
        //  Pointer to first byte of destination buffer.
        /*uint8_t*/ int        dst_begin;
        //  Pointer to one byte past the end of the destination buffer.
        /*uint8_t*/ int        dst_end;
        //  1 if we have reached the end of the stream, 0 otherwise.
        int end_of_stream;
        //  magic number of the current block if we are within a block,
        //  LZFSE_NO_BLOCK_MAGIC otherwise.
        /*uint32_t*/ int block_magic;
        lzfse_compressed_block_decoder_state compressed_lzfse_block_state = new lzfse_compressed_block_decoder_state();
        lzvn_compressed_block_decoder_state  compressed_lzvn_block_state  = new lzvn_compressed_block_decoder_state();
        uncompressed_block_decoder_state     uncompressed_block_state     = new uncompressed_block_decoder_state();
    }
}
