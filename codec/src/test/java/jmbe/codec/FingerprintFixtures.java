package jmbe.codec;

/** Synthetic valid codewords for correction/evidence tests; contains no captured speech or receiver data. */
public final class FingerprintFixtures
{
    public static final int[] AMBE_C0 = {0,4,8,12,16,20,24,28,32,36,40,44,48,52,56,60,64,68,1,5,9,13,17,21};
    public static final int[] AMBE_C1 = {25,29,33,37,41,45,49,53,57,61,65,69,2,6,10,14,18,22,26,30,34,38,42};
    public static final int[] AMBE_C2 = {46,50,54,58,62,66,70,3,7,11,15};
    public static final int[] AMBE_C3 = {19,23,27,31,35,39,43,47,51,55,59,63,67,71};
    private static final int[] GOLAY_CHECKSUMS = {0x63A,0x31D,0x7B4,0x3DA,0x1ED,0x6CC,0x366,0x1B3,0x6E3,0x54B,0x49F,0x475};
    private static final int[] HAMMING_CHECKSUMS = {15,14,13,12,11,10,9,7,6,5,3};
    private static final int[] IMBE_DEINTERLEAVE = {0,24,48,72,96,120,25,1,73,49,121,97,2,26,50,74,98,122,27,3,75,51,123,99,
        4,28,52,76,100,124,29,5,77,53,125,101,6,30,54,78,102,126,31,7,79,55,127,103,8,32,56,80,104,128,33,9,81,57,129,105,
        10,34,58,82,106,130,35,11,83,59,131,107,12,36,60,84,108,132,37,13,85,61,133,109,14,38,62,86,110,134,39,15,87,63,
        135,111,16,40,64,88,112,136,41,17,89,65,137,113,18,42,66,90,114,138,43,19,91,67,139,115,20,44,68,92,116,140,45,21,
        93,69,141,117,22,46,70,94,118,142,47,23,95,71,143,119};

    private FingerprintFixtures() {}

    public static byte[] ambe(int u0, int u1, int c2, int c3)
    {
        byte[] raw = new byte[9];
        int c0 = golay(u0);
        put(raw, AMBE_C0, (c0 << 1) | (Integer.bitCount(c0) & 1));
        int c1 = golay(u1);
        int randomizer = 16 * u0;
        for(int bit = 0; bit < 23; bit++)
        {
            randomizer = (173 * randomizer + 13849) & 0xFFFF;
            if(randomizer >= 32768) c1 ^= 1 << (22 - bit);
        }
        put(raw, AMBE_C1, c1);
        put(raw, AMBE_C2, c2);
        put(raw, AMBE_C3, c3);
        return raw;
    }

    public static byte[] imbe(int fundamental, int variation)
    {
        byte[] decoded = new byte[18];
        int u0 = ((fundamental >>> 2) << 6) | (variation & 63);
        put(decoded, 0, 23, golay(u0));
        for(int block = 1; block < 4; block++) put(decoded, block * 23, 23, golay((variation * (17 + block)) & 4095));
        for(int block = 0; block < 3; block++)
        {
            int data = (variation * (11 + block)) & 2047;
            put(decoded, 92 + block * 15, 15, (data << 4) | checksum(data, HAMMING_CHECKSUMS));
        }
        put(decoded, 137, 7, ((variation & 0x71) | ((fundamental & 3) << 1)));
        int randomizer = 16 * u0;
        for(int bit = 23; bit < 137; bit++)
        {
            randomizer = (173 * randomizer + 13849) & 0xFFFF;
            if(randomizer >= 32768) flip(decoded, bit);
        }
        byte[] raw = new byte[18];
        for(int bit = 0; bit < 144; bit++) if(get(decoded, IMBE_DEINTERLEAVE[bit])) flip(raw, bit);
        return raw;
    }

    public static void flipImbe(byte[] raw, int decodedBit)
    {
        for(int bit = 0; bit < 144; bit++) if(IMBE_DEINTERLEAVE[bit] == decodedBit) { flip(raw, bit); return; }
        throw new IllegalArgumentException("Invalid decoded IMBE bit");
    }

    public static void flip(byte[] raw, int bit) { raw[bit / 8] ^= (byte)(0x80 >>> (bit % 8)); }
    private static boolean get(byte[] raw, int bit) { return (raw[bit / 8] & (0x80 >>> (bit % 8))) != 0; }
    private static int golay(int data) { return (data << 11) | checksum(data, GOLAY_CHECKSUMS); }
    private static int checksum(int data, int[] checksums)
    {
        int value = 0;
        for(int bit = 0; bit < checksums.length; bit++) if((data & (1 << (checksums.length - bit - 1))) != 0) value ^= checksums[bit];
        return value;
    }
    private static void put(byte[] raw, int[] indexes, int value)
    {
        for(int bit = 0; bit < indexes.length; bit++) if((value & (1 << (indexes.length - bit - 1))) != 0) flip(raw, indexes[bit]);
    }
    private static void put(byte[] raw, int offset, int length, int value)
    {
        for(int bit = 0; bit < length; bit++) if((value & (1 << (length - bit - 1))) != 0) flip(raw, offset + bit);
    }
}
