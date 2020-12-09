package lzfse;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.TimeUnit;

import static lzfse.Decode.lzfse_decode_buffer;
import static lzfse.Decode.lzfse_decode_scratch_size;
import static lzfse.Encode.lzfse_encode_buffer;
import static lzfse.Encode.lzfse_encode_scratch_size;

public class Main {

    private static final int INVALID_OP      = -1;
    private static final int VERBOSITY_QUIET = 0;

    //TODO enum?
    private static final int LZFSE_ENCODE = 0;
    private static final int LZFSE_DECODE = 1;

    private static final long START_TIME = System.nanoTime();

    public static void main(String[] args) throws IOException {
        final int argc = args.length;
        String[] in_file = {null};
        String[] out_file = {null};
        int op = INVALID_OP;
        int verbosity = VERBOSITY_QUIET;

        for (int i = 0; i < argc; ) {
            final String a = args[i++];
            if (a.equals("-h")) {
                throw new TODOException("USAGE(argc, argv);");
            }
            if (a.equals("-v")) {
                verbosity++;
                continue;
            }
            if (a.equals("-encode")) {
                op = LZFSE_ENCODE;
                continue;
            }
            if (a.equals("-decode")) {
                op = LZFSE_DECODE;
                continue;
            }

            // one arg
            String[] arg_var = null;
            if (a.equals("-i") && in_file[0] == null) {
                arg_var = in_file;
            } else if (a.equals("-o") && out_file[0] == null) {
                arg_var = out_file;
            }
            if (arg_var != null) {    // Flag is recognized. Check if there is an argument.
                if (i == argc) {
                    throw new TODOException("USAGE_MSG(argc, argv, \"Error: Missing arg after %s\\n\", a);");
                }
                arg_var[0] = args[i++];
                continue;
            }
            throw new TODOException("USAGE_MSG(argc, argv, \"Error: invalid flag %s\\n\", a);");
        }

        if (op < 0) {
            throw new TODOException("USAGE_MSG(argc, argv, \"Error: -encode|-decode required\\n\");");
        }

        // Info
        if (verbosity > 0) {
            if (op == LZFSE_ENCODE)
                System.err.println("LZFSE encode \n");
            if (op == LZFSE_DECODE)
                System.err.println("LZFSE decode \n");
            System.err.printf("Input: %s\n", in_file[0] != null ? in_file[0] : "stdin");
            System.err.printf("Output: %s\n", out_file[0] != null ? out_file[0] : "stdout");
        }

        // Load input
        long in_allocated = 0; // allocated in IN
        long in_size = 0;      // used in IN
        ByteBuffer/*uint8_t **/ in = null;         // input buffer
        int in_fd = -1;        // input file desc

        if (in_file[0] != null) {
            in = LittleEndianByteBuffer.wrap(Files.readAllBytes(Paths.get(in_file[0]))); //TODO buffer?
        }
        if (in == null) {
            throw new TODOException("perror(\"malloc\");\n" +
                    "        exit(1);");
        }

        in_size += in.capacity();//(size_t)r;
        // Size info
        if (verbosity > 0) {
            System.err.printf("Input size: %zu B\n", in_size);
        }

        //  Encode/decode
        //  Compute size for result buffer; we assume here that encode shrinks size,
        //  and that decode grows by no more than 4x.  These are reasonable common-
        //  case guidelines, but are not formally guaranteed to be satisfied.
        long out_allocated = in_size;//(op == LZFSE_ENCODE) ? in_size : (4 * in_size);
        long out_size = 0;
        long aux_allocated = (op == LZFSE_ENCODE) ? lzfse_encode_scratch_size()
                : lzfse_decode_scratch_size();
        ByteBuffer/*uint8_t*/ out = LittleEndianByteBuffer.allocate(/*uint8_t *)malloc(*/Math.toIntExact(out_allocated));
        double c0 = System.nanoTime();//get_time();
        while (true) {
            if (op == LZFSE_ENCODE)
                out_size = lzfse_encode_buffer(out, out_allocated, in, in_size);
            else
                out_size = lzfse_decode_buffer(out, out_allocated, in, in_size);

            // If output buffer was too small, grow and retry.
            if (out_size == 0 || (op == LZFSE_DECODE && out_size == out_allocated)) {
                if (verbosity > 0)
                    System.err.printf("Output buffer was too small, increasing size...\n");
                out_allocated <<= 1;
                out = LittleEndianByteBuffer.allocate(Math.toIntExact(out_allocated));//(uint8_t *) lzfse_reallocf(out, out_allocated);
                if (out == null) {
//                    perror("malloc");
                    System.exit(1);
                }
                continue;
            }

            break;
        }
        double c1 = System.nanoTime();//get_time();

        if (verbosity > 0) {
            System.err.printf("Output size: %zu B\n", out_size);
            long raw_size = (op == LZFSE_ENCODE) ? in_size : out_size;
            long compressed_size = (op == LZFSE_ENCODE) ? out_size : in_size;
            System.err.printf("Compression ratio: %.3f\n", (double) raw_size / (double) compressed_size);
            double ns_per_byte = 1.0e9 * (c1 - c0) / (double) raw_size;
            double mb_per_s = (double) raw_size / 1024.0 / 1024.0 / (c1 - c0);
            System.err.printf("Speed: %.2f ns/B, %.2f MB/s\n", ns_per_byte, mb_per_s);
        }

        // Write output
        OutputStream out_fd = null;
        if (out_file[0] != null) {
            out_fd = Files.newOutputStream(Paths.get(out_file[0]), StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW, StandardOpenOption.TRUNCATE_EXISTING);
        } else {
            out_fd = System.out; // stdout
        }
        for (long out_pos = 0; out_pos < out_size; ) {
//            ptrdiff_t w = write(out_fd, out + out_pos, out_size - out_pos);
            byte[] array = new byte[Math.toIntExact(out_size)];
            out.position(Math.toIntExact(out_pos)).get(array, 0, array.length);
            out_fd.write(array);//TODO long long?
            out_pos += array.length;
        }
        if (out_file[0] != null) {
            out_fd.close();
        }

        final long elapsed_nanos = System.nanoTime() - START_TIME;
        System.out.printf("Time elapsed %d seconds.%n", TimeUnit.NANOSECONDS.toSeconds(elapsed_nanos));

        return; //0\\ // OK
    }

}
