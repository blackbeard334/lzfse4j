package lzfse;

final class Util {
    private Util() {
    }

    static short[] mergeArrays(final short[]... arrays) {
        int length = 0;
        for (short[] array : arrays) {
            length += array.length;
        }
        short[] mergeArray = new short[length];
        int destPos = 0;
        for (short[] array : arrays) {
            System.arraycopy(array, 0, mergeArray, destPos, array.length);
            destPos += array.length;
        }
        return mergeArray;
    }

    static void flushArrays(final short[] src, final int length, short[]... dst) {
        for (int i = 0, j = 0; i < length; j++) {
            for (int k = 0; k < dst[j].length && i < length; i++, k++) {
                dst[j][k] = src[i];
            }
        }
    }

    static byte[] ltob(final long l) {
        return LittleEndianByteBuffer.allocate(Long.BYTES)
                .putLong(l)
                .array();
    }

    static byte[] itob(final int i) {
        return LittleEndianByteBuffer.allocate(Integer.BYTES)
                .putInt(i)
                .array();
    }
}
