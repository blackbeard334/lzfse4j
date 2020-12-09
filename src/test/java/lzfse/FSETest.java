package lzfse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FSETest {

    @Test
    void fse_mask_lsb64() {
        long expected = 0;
        for (int i = 0; i < 65; i++) {
            assertEquals(expected, FSE.fse_mask_lsb64(-1L, i));
            expected <<= 1;
            expected |= 1;
        }
    }

    @Test
    void fse_mask_lsb32() {
        int expected = 0;
        for (int i = 0; i < 33; i++) {
            assertEquals(expected, FSE.fse_mask_lsb32(-1, i));
            expected <<= 1;
            expected |= 1;
        }
    }

    @Test
    void fse_extract_bits64() {
        long expected = 0;
        for (int i = 0; i < 65; i++) {
            assertEquals(expected, FSE.fse_extract_bits64(-1L, 1, i));
            expected <<= 1;
            expected |= 1;
        }
    }

    @Test
    void fse_extract_bits32() {
    }

    @Test
    void fse_out_init64() {
    }

    @Test
    void fse_out_init32() {
    }

    @Test
    void fse_out_flush64() {
    }

    @Test
    void fse_out_flush32() {
    }

    @Test
    void fse_out_finish64() {
    }

    @Test
    void fse_out_finish32() {
    }

    @Test
    void fse_out_push64() {
    }

    @Test
    void fse_out_push32() {
    }

    @Test
    void DEBUG_CHECK_INPUT_STREAM_PARAMETERS() {
    }

    @Test
    void testDEBUG_CHECK_INPUT_STREAM_PARAMETERS() {
    }

    @Test
    void fse_in_checked_init64() {
    }

    @Test
    void fse_in_checked_init32() {
    }

    @Test
    void fse_in_checked_flush64() {
    }

    @Test
    void fse_in_checked_flush32() {
    }

    @Test
    void fse_in_pull64() {
    }

    @Test
    void fse_in_pull32() {
    }

    @Test
    void fse_encode() {
    }

    @Test
    void fse_decode() {
    }

    @Test
    void fse_value_decode() {
    }

    @Test
    void fse_check_freq() {
    }

    @Test
    void fse_init_encoder_table() {
    }

    @Test
    void fse_init_decoder_table() {
    }

    @Test
    void fse_init_value_decoder_table() {
    }

    @Test
    void fse_adjust_freqs() {
    }

    @Test
    void fse_normalize_freq() {
    }
}