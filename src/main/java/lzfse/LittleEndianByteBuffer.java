package lzfse;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;


final class LittleEndianByteBuffer {
    private LittleEndianByteBuffer() {
    }

    static ByteBuffer allocate(final int capacity) {
        return ByteBuffer.allocate(capacity)
                .order(ByteOrder.LITTLE_ENDIAN);
    }

    static ByteBuffer duplicate(final ByteBuffer src) {
        return src.duplicate()
                .position(src.position())
                .order(ByteOrder.LITTLE_ENDIAN);
    }

    static ByteBuffer duplicate(final ByteBuffer src, final int offset) {
        return duplicate(src)
                .position(src.position() + offset);
    }

    public static ByteBuffer wrap(final byte[] bytes) {
        return ByteBuffer.wrap(bytes)
                .order(ByteOrder.LITTLE_ENDIAN);
    }

    public static ByteBuffer wrap(final byte[] bytes, final int position) {
        return wrap(bytes)
                .position(position);
    }

    public static void copy(ByteBuffer dst, final ByteBuffer src, final int size) {
        final int position = src.position();
        dst.put(src.array(), position, size);
    }

    @Deprecated
    static void skip(ByteBuffer buffer, final int count) {
        buffer.position(buffer.position() + count);
    }

    public static long getLongBytes(ByteBuffer buf, final int number_of_bytes) {
        byte[] bytes = new byte[Long.BYTES];
        buf.get(bytes, 0, number_of_bytes);

        return LittleEndianByteBuffer.wrap(bytes)
                .getLong(0);
    }
}
