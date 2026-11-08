package hack.echo.client.features.impl.render.hud;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class CompanionGif {

    public static final class Frame {
        public final byte[] rgba;
        public final int delayMs;

        private Frame(byte[] rgba, int delayMs) {
            this.rgba = rgba;
            this.delayMs = delayMs;
        }
    }

    public final int width;
    public final int height;
    private final List<Frame> frames = new ArrayList<>();

    private CompanionGif(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public List<Frame> getFrames() {
        return frames;
    }

    public boolean isAnimated() {
        return frames.size() > 1;
    }

    public static CompanionGif decode(byte[] bytes) {
        if (bytes == null || bytes.length < 14) return null;
        String magic = new String(bytes, 0, 6, StandardCharsets.US_ASCII);
        if (!magic.equals("GIF87a") && !magic.equals("GIF89a")) return null;

        int width = unsignedShort(bytes, 6);
        int height = unsignedShort(bytes, 8);
        if (width <= 0 || height <= 0 || width * height * 4 > 64 * 1024 * 1024) return null;

        CompanionGif gif = new CompanionGif(width, height);

        int pos = 13;
        byte screenPacked = bytes[10];
        boolean hasGlobalTable = (screenPacked & 0x80) != 0;
        int globalTableSize = hasGlobalTable ? 3 * (2 << (screenPacked & 0x07)) : 0;
        byte[] globalTable = copyRange(bytes, pos, globalTableSize);
        pos += globalTableSize;

        byte[] canvas = new byte[width * height * 4];
        int disposal = 0;
        int transparentIndex = -1;
        int delayMs = 0;

        while (pos + 1 < bytes.length) {
            int block = bytes[pos++] & 0xFF;

            if (block == 0x3B) break;

            if (block == 0x21) {
                if (pos >= bytes.length) break;
                int label = bytes[pos++] & 0xFF;
                if (label == 0xF9) {
                    if (pos >= bytes.length) break;
                    int size = bytes[pos++] & 0xFF;
                    if (pos + size > bytes.length) break;
                    byte packed = bytes[pos];
                    disposal = (packed >> 2) & 0x07;
                    delayMs = unsignedShort(bytes, pos + 1) * 10;
                    transparentIndex = (packed & 0x01) != 0 ? bytes[pos + 3] & 0xFF : -1;
                    pos += size;
                    if (pos < bytes.length && bytes[pos] == 0) pos++;
                } else {
                    pos = skipSubBlocks(bytes, pos);
                }
            } else if (block == 0x2C) {
                if (pos + 9 > bytes.length) break;
                int left = unsignedShort(bytes, pos);
                int top = unsignedShort(bytes, pos + 2);
                int imgWidth = unsignedShort(bytes, pos + 4);
                int imgHeight = unsignedShort(bytes, pos + 6);
                byte imgPacked = bytes[pos + 8];
                pos += 9;

                boolean hasLocalTable = (imgPacked & 0x80) != 0;
                boolean interlaced = (imgPacked & 0x40) != 0;
                int localTableSize = hasLocalTable ? 3 * (2 << (imgPacked & 0x07)) : 0;
                byte[] table = hasLocalTable ? copyRange(bytes, pos, localTableSize) : globalTable;
                pos += localTableSize;

                if (pos >= bytes.length) break;
                int minCodeSize = bytes[pos++] & 0xFF;
                int[] posRef = {pos};
                byte[] data = readSubBlocks(bytes, posRef);
                if (data == null) break;
                pos = posRef[0];

                int[] indices;
                try {
                    indices = decodeLzw(data, minCodeSize, imgWidth * imgHeight);
                } catch (RuntimeException e) {
                    break;
                }

                byte[] beforeImage = disposal == 3 ? canvas.clone() : null;
                drawImage(canvas, indices, width, height, left, top, imgWidth, imgHeight, interlaced, table,
                        transparentIndex);
                gif.frames.add(new Frame(canvas.clone(), Math.max(1, delayMs)));

                if (disposal == 2) {
                    clearRect(canvas, width, height, left, top, imgWidth, imgHeight);
                } else if (disposal == 3 && beforeImage != null) {
                    canvas = beforeImage;
                }
            }
        }

        if (gif.frames.isEmpty()) return null;
        return gif;
    }

    private static int[] decodeLzw(byte[] data, int minCodeSize, int expectedPixels) {
        int clear = 1 << minCodeSize;
        int end = clear + 1;
        int codeSize = minCodeSize + 1;
        int nextCode = end + 1;
        List<int[]> dictionary = createDictionary(minCodeSize);

        int[] pixels = new int[Math.max(expectedPixels, 1)];
        int pixelCount = 0;

        int bitPos = 0;
        boolean prevValid = false;
        int[] prevEntry = null;

        while (bitPos + codeSize <= data.length * 8) {
            int code = readBits(data, bitPos, codeSize);
            bitPos += codeSize;

            if (code == clear) {
                dictionary = createDictionary(minCodeSize);
                nextCode = end + 1;
                codeSize = minCodeSize + 1;
                prevValid = false;
                prevEntry = null;
                continue;
            }
            if (code == end) break;

            int[] entry;
            if (code < dictionary.size() && dictionary.get(code) != null) {
                entry = dictionary.get(code);
            } else if (code == nextCode && prevValid) {
                entry = new int[prevEntry.length + 1];
                System.arraycopy(prevEntry, 0, entry, 0, prevEntry.length);
                entry[prevEntry.length] = prevEntry[0];
            } else {
                break;
            }

            for (int value : entry) {
                if (pixelCount < pixels.length) pixels[pixelCount++] = value;
            }

            if (prevValid) {
                int[] newEntry = new int[prevEntry.length + 1];
                System.arraycopy(prevEntry, 0, newEntry, 0, prevEntry.length);
                newEntry[prevEntry.length] = entry[0];
                dictionary.add(newEntry);
                nextCode++;
                if (codeSize < 12 && nextCode == (1 << codeSize)) codeSize++;
            }

            prevEntry = entry;
            prevValid = true;
        }

        int[] result = new int[pixelCount];
        System.arraycopy(pixels, 0, result, 0, pixelCount);
        return result;
    }

    private static List<int[]> createDictionary(int minCodeSize) {
        int size = 1 << minCodeSize;
        List<int[]> dictionary = new ArrayList<>(size + 2);
        for (int i = 0; i < size; i++) dictionary.add(new int[]{i});
        dictionary.add(null);
        dictionary.add(null);
        return dictionary;
    }

    private static int readBits(byte[] data, int bitPos, int count) {
        int value = 0;
        for (int i = 0; i < count; i++) {
            int bit = (data[(bitPos + i) >> 3] >> ((bitPos + i) & 7)) & 1;
            value |= bit << i;
        }
        return value;
    }

    private static void drawImage(byte[] canvas, int[] indices, int canvasWidth, int canvasHeight, int left, int top,
            int imgWidth, int imgHeight, boolean interlaced, byte[] table, int transparentIndex) {
        int[] rowOrder = interlaced ? interlaceOrder(imgHeight) : null;
        int pixelIndex = 0;
        for (int row = 0; row < imgHeight; row++) {
            int sourceRow = rowOrder != null ? rowOrder[row] : row;
            int y = top + sourceRow;
            if (y < 0 || y >= canvasHeight) {
                pixelIndex += imgWidth;
                continue;
            }
            for (int x = 0; x < imgWidth; x++) {
                int px = left + x;
                if (px >= 0 && px < canvasWidth) {
                    int colorIndex = indices[pixelIndex];
                    if (colorIndex != transparentIndex && table != null && colorIndex * 3 + 2 < table.length) {
                        int offset = (y * canvasWidth + px) * 4;
                        canvas[offset] = table[colorIndex * 3];
                        canvas[offset + 1] = table[colorIndex * 3 + 1];
                        canvas[offset + 2] = table[colorIndex * 3 + 2];
                        canvas[offset + 3] = (byte) 255;
                    }
                }
                pixelIndex++;
            }
        }
    }

    private static int[] interlaceOrder(int height) {
        int[] order = new int[height];
        int index = 0;
        for (int y = 0; y < height; y += 8) order[index++] = y;
        for (int y = 4; y < height; y += 8) order[index++] = y;
        for (int y = 2; y < height; y += 4) order[index++] = y;
        for (int y = 1; y < height; y += 2) order[index++] = y;
        return order;
    }

    private static void clearRect(byte[] canvas, int canvasWidth, int canvasHeight, int left, int top, int width,
            int height) {
        for (int y = top; y < top + height; y++) {
            if (y < 0 || y >= canvasHeight) continue;
            for (int x = left; x < left + width; x++) {
                if (x < 0 || x >= canvasWidth) continue;
                int offset = (y * canvasWidth + x) * 4;
                canvas[offset] = 0;
                canvas[offset + 1] = 0;
                canvas[offset + 2] = 0;
                canvas[offset + 3] = 0;
            }
        }
    }

    private static int unsignedShort(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
    }

    private static byte[] copyRange(byte[] bytes, int offset, int length) {
        if (length <= 0 || offset + length > bytes.length) return null;
        byte[] copy = new byte[length];
        System.arraycopy(bytes, offset, copy, 0, length);
        return copy;
    }

    private static int skipSubBlocks(byte[] bytes, int pos) {
        while (pos < bytes.length) {
            int size = bytes[pos++] & 0xFF;
            if (size == 0) break;
            pos += size;
        }
        return pos;
    }

    private static byte[] readSubBlocks(byte[] bytes, int[] posRef) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int pos = posRef[0];
        while (pos < bytes.length) {
            int size = bytes[pos++] & 0xFF;
            if (size == 0) break;
            if (pos + size > bytes.length) return null;
            out.write(bytes, pos, size);
            pos += size;
        }
        posRef[0] = pos;
        return out.toByteArray();
    }
}
