/*
 * Copyright (C) 2026 Dennis Sheirer
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package jmbe.codec;

import jmbe.binary.BinaryFrame;

/**
 * Fixed-cost V1 evidence from the current frame's complete corrected information payload, never synthesized PCM.
 * This is matching evidence, not a cryptographic digest or a guarantee against undetected channel errors.
 *
 * <p>The canonical byte stream starts with ASCII JMBE, version 1, and codec domain 1 (AMBE) or 2 (IMBE). Each
 * information field follows most-significant bit first, padded with zero low bits to its own byte boundary.
 * AMBE fields are U0 (12), U1 (12), C2 (11), C3 (14). IMBE fields are the information portions of C0 through
 * C3 (12 each), C4 through C6 (11 each), and C7 (7). Parity bits are excluded; every unprotected bit is retained.
 * FNV-1a hashes the stream without allocating a serialized payload. Zero is reserved for unavailable evidence.</p>
 */
public final class CorrectedVoiceFrameFingerprintV1
{
    public static final int AMBE = 1;
    public static final int IMBE = 2;
    private static final long OFFSET_BASIS = 0xCBF29CE484222325L;
    private static final long PRIME = 0x100000001B3L;

    private CorrectedVoiceFrameFingerprintV1() {}

    public static long begin(int codec)
    {
        long hash = appendByte(OFFSET_BASIS, 'J');
        hash = appendByte(hash, 'M');
        hash = appendByte(hash, 'B');
        hash = appendByte(hash, 'E');
        hash = appendByte(hash, 1);
        return appendByte(hash, codec);
    }

    public static long appendBits(long hash, BinaryFrame frame, int offset, int length)
    {
        int value = 0;
        int count = 0;
        for(int bit = offset; bit < offset + length; bit++)
        {
            value = (value << 1) | (frame.get(bit) ? 1 : 0);
            if(++count == 8)
            {
                hash = appendByte(hash, value);
                value = 0;
                count = 0;
            }
        }
        return count == 0 ? hash : appendByte(hash, value << (8 - count));
    }

    public static long finish(long hash)
    {
        return hash != 0 ? hash : 1;
    }

    private static long appendByte(long hash, int value)
    {
        return (hash ^ (value & 0xFFL)) * PRIME;
    }
}
