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

package jmbe.codec;

/**
 * PCM audio and the mutually exclusive action used to synthesize one 20 millisecond vocoder frame.
 */
public record VoiceFrameSynthesis(float[] audio, Outcome outcome)
{
    public VoiceFrameSynthesis
    {
        if(outcome == null)
        {
            throw new IllegalArgumentException("Voice frame synthesis outcome cannot be null");
        }
    }

    public enum Outcome
    {
        DECODED,
        REPEATED,
        CONCEALED
    }
}
