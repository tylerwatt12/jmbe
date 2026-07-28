/*
 * ******************************************************************************
 * Copyright (C) 2026 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 * *****************************************************************************
 */

package jmbe.audio;

import java.util.LinkedHashMap;
import java.util.Map;
import jmbe.codec.VoiceFrameSynthesis;

/**
 * Stable metadata keys for optional per-frame vocoder diagnostics.
 */
public final class FrameQualityMetadata
{
    public static final String OUTCOME = "jmbe.frame.outcome";
    public static final String FEC_ERRORS = "jmbe.frame.fec_errors";
    public static final String FEC_PROTECTED_BITS = "jmbe.frame.fec_protected_bits";

    private FrameQualityMetadata()
    {
    }

    /**
     * Combines optional tone metadata with one frame-quality observation.
     */
    public static Map<String,String> create(Map<String,String> metadata, VoiceFrameSynthesis synthesis,
                                            int fecErrors, int fecProtectedBits)
    {
        LinkedHashMap<String,String> values = new LinkedHashMap<>();

        if(metadata != null)
        {
            values.putAll(metadata);
        }

        values.put(OUTCOME, synthesis.outcome().name());
        values.put(FEC_ERRORS, Integer.toString(Math.max(0, fecErrors)));
        values.put(FEC_PROTECTED_BITS, Integer.toString(Math.max(0, fecProtectedBits)));
        return Map.copyOf(values);
    }
}
