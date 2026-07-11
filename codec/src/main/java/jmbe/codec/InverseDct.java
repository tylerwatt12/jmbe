/*
 * ******************************************************************************
 * Copyright (C) 2026
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * ******************************************************************************
 */

package jmbe.codec;

/**
 * Precomputed inverse-DCT cosine coefficients shared by the AMBE and IMBE decoders.
 */
public final class InverseDct
{
    private static final int MAXIMUM_BLOCK_LENGTH = 17;
    private static final float[][][] COEFFICIENTS = createCoefficients(false);
    private static final float[][][] FLOAT_COEFFICIENTS = createCoefficients(true);

    private InverseDct()
    {
    }

    /**
     * Returns cos(PI * (coefficient - 1) * (sample - 0.5) / blockLength).
     * Indexes are one-based to match the codec specifications.
     */
    public static float coefficient(int blockLength, int coefficient, int sample)
    {
        validate(blockLength, coefficient, sample);
        return COEFFICIENTS[blockLength][coefficient][sample];
    }

    /**
     * Returns the coefficient using the original AMBE single-precision angle calculation.
     */
    public static float floatCoefficient(int blockLength, int coefficient, int sample)
    {
        validate(blockLength, coefficient, sample);
        return FLOAT_COEFFICIENTS[blockLength][coefficient][sample];
    }

    private static float[][][] createCoefficients(boolean singlePrecision)
    {
        float[][][] coefficients = new float[MAXIMUM_BLOCK_LENGTH + 1][][];

        for(int blockLength = 1; blockLength <= MAXIMUM_BLOCK_LENGTH; blockLength++)
        {
            coefficients[blockLength] = new float[blockLength + 1][blockLength + 1];

            for(int coefficient = 1; coefficient <= blockLength; coefficient++)
            {
                for(int sample = 1; sample <= blockLength; sample++)
                {
                    double angle = singlePrecision ?
                        ((float)Math.PI * (coefficient - 1) * (sample - 0.5f)) / blockLength :
                        Math.PI * (coefficient - 1) * (sample - 0.5d) / blockLength;
                    coefficients[blockLength][coefficient][sample] = (float)Math.cos(angle);
                }
            }
        }

        return coefficients;
    }

    private static void validate(int blockLength, int coefficient, int sample)
    {
        if(blockLength < 1 || blockLength > MAXIMUM_BLOCK_LENGTH || coefficient < 1 ||
            coefficient > blockLength || sample < 1 || sample > blockLength)
        {
            throw new IllegalArgumentException("Invalid inverse DCT indexes");
        }
    }
}
