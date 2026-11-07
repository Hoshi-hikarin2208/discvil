package com.hoshihikarin.discforge;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

/** Reads the length of an Ogg Vorbis file without any external library. */
final class OggDuration {

    private static final byte[] VORBIS_ID = {0x01, 'v', 'o', 'r', 'b', 'i', 's'};
    private static final byte[] OGG_PAGE = {'O', 'g', 'g', 'S'};

    private OggDuration() {
    }

    /**
     * @return the duration in seconds, or -1 if the file is not a readable Ogg Vorbis stream
     */
    static double seconds(Path file) {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            long length = raf.length();
            if (length < 64) {
                return -1;
            }

            byte[] head = new byte[(int) Math.min(4096, length)];
            raf.readFully(head);
            int id = indexOf(head, VORBIS_ID);
            if (id < 0 || id + 16 > head.length) {
                return -1;
            }
            long sampleRate = readLittleEndian(head, id + 12, 4);
            if (sampleRate <= 0) {
                return -1;
            }

            byte[] tail = new byte[(int) Math.min(65536, length)];
            raf.seek(length - tail.length);
            raf.readFully(tail);
            int page = lastIndexOf(tail, OGG_PAGE);
            if (page < 0 || page + 14 > tail.length) {
                return -1;
            }
            long granule = readLittleEndian(tail, page + 6, 8);
            if (granule <= 0) {
                return -1;
            }
            return (double) granule / sampleRate;
        } catch (IOException ex) {
            return -1;
        }
    }

    private static long readLittleEndian(byte[] data, int offset, int bytes) {
        long value = 0;
        for (int i = 0; i < bytes; i++) {
            value |= (long) (data[offset + i] & 0xFF) << (8 * i);
        }
        return value;
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            if (matches(haystack, needle, i)) {
                return i;
            }
        }
        return -1;
    }

    private static int lastIndexOf(byte[] haystack, byte[] needle) {
        for (int i = haystack.length - needle.length; i >= 0; i--) {
            if (matches(haystack, needle, i)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean matches(byte[] haystack, byte[] needle, int offset) {
        for (int j = 0; j < needle.length; j++) {
            if (haystack[offset + j] != needle[j]) {
                return false;
            }
        }
        return true;
    }
}
