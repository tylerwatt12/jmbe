/*
 * ******************************************************************************
 * Copyright (C) 2015-2019 Dennis Sheirer
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

package jmbe.codec.ambe;

import java.util.Collections;
import java.util.Map;
import jmbe.audio.AudioWithMetadata;
import jmbe.audio.FrameQualityMetadata;
import jmbe.codec.VoiceFrameSynthesis;
import jmbe.iface.IAudioCodec;
import jmbe.iface.IAudioWithMetadata;

/**
 * Audio converter for AMBE frames encoded at 3600 bps with 2450 bps data and 1250 bps FEC
 */
public class AMBEAudioCodec implements IAudioCodec
{
    public static final String CODEC_NAME = "AMBE 3600 x 2450";
    private static final int FRAME_BYTE_LENGTH = 9;
    private AMBESynthesizer mSynthesizer = new AMBESynthesizer();
    private boolean mToneAudioEnabled = true;
    private boolean mVoiceQualityMetadataEnabled;
    private boolean mCorrectedVoiceFrameFingerprintEnabled;
    private long mCorrectedVoiceFrameFingerprint;

    /**
     * Converts the AMBE frame data into PCM audio samples at 8kHz 16-bit rate.
     *
     * @param frameData byte array of AMBE frame data
     */
    public float[] getAudio(byte[] frameData)
    {
        mCorrectedVoiceFrameFingerprint = 0;
        validate(frameData);
        AMBEFrame frame = new AMBEFrame(frameData, mCorrectedVoiceFrameFingerprintEnabled);
        VoiceFrameSynthesis synthesis = mSynthesizer.synthesize(frame);
        captureCorrectedVoiceFrameFingerprint(frame, synthesis);
        return applyToneAudioPreference(frame, synthesis.audio());
    }

    /**
     * Converts the AMBE frame data into PCM audio samples at 8kHz 16-bit rate and includes metadata about any
     * tone(s) contained in the frame.
     *
     * @param frameData byte array for an audio frame
     * @return decoded audio and any associated metadata such as tones or dtmf/knox codes
     */
    @Override
    public IAudioWithMetadata getAudioWithMetadata(byte[] frameData)
    {
        mCorrectedVoiceFrameFingerprint = 0;
        validate(frameData);
        AMBEFrame frame = new AMBEFrame(frameData, mCorrectedVoiceFrameFingerprintEnabled);
        VoiceFrameSynthesis synthesis = mSynthesizer.synthesize(frame);
        captureCorrectedVoiceFrameFingerprint(frame, synthesis);
        float[] audio = applyToneAudioPreference(frame, synthesis.audio());
        AudioWithMetadata toneMetadata = frame.getAudioWithMetadata(audio);

        if(!mVoiceQualityMetadataEnabled)
        {
            return toneMetadata;
        }

        Map<String,String> metadata = FrameQualityMetadata.create(
            toneMetadata.hasMetadata() ? toneMetadata.getMetadata() : Collections.emptyMap(), synthesis,
            frame.getFecErrorCount(), AMBEFrame.FEC_PROTECTED_BITS);
        return AudioWithMetadata.create(audio, metadata);
    }

    /**
     * Controls audio synthesis for valid AMBE tone frames. Tone detection and metadata remain available when tone
     * audio is disabled; only the decoded PCM samples are replaced with silence. This is enabled by default to
     * preserve legacy behavior.
     */
    public void setToneAudioEnabled(boolean enabled)
    {
        mToneAudioEnabled = enabled;
    }

    /**
     * Enables the optional per-frame quality metadata consumed by newer SDRTrunk versions. This is disabled by
     * default so that older callers continue to receive tone-only metadata.
     */
    public void setVoiceQualityMetadataEnabled(boolean enabled)
    {
        mVoiceQualityMetadataEnabled = enabled;
    }

    /**
     * Resets the audio converter at the end or beginning of each call so that the starting frame is a default frame.
     */
    @Override
    public void reset()
    {
        mCorrectedVoiceFrameFingerprint = 0;
        mSynthesizer.reset();
    }

    /** Enables optional V1 corrected speech evidence. Disabled by default; changing this setting clears evidence. */
    public void setCorrectedVoiceFrameFingerprintEnabled(boolean enabled)
    {
        mCorrectedVoiceFrameFingerprintEnabled = enabled;
        mCorrectedVoiceFrameFingerprint = 0;
    }

    /**
     * Current successfully decoded speech frame's corrected payload evidence, or zero when unavailable.
     * Tone, silence, failed correction, repeated/concealed audio, invalid input, and reset never retain evidence.
     * Codec instances remain confined to their caller's decoding sequence, just like the audio synthesis state.
     */
    public long getCorrectedVoiceFrameFingerprintV1()
    {
        return mCorrectedVoiceFrameFingerprint;
    }

    private void captureCorrectedVoiceFrameFingerprint(AMBEFrame frame, VoiceFrameSynthesis synthesis)
    {
        if(mCorrectedVoiceFrameFingerprintEnabled && synthesis.outcome() == VoiceFrameSynthesis.Outcome.DECODED)
        {
            mCorrectedVoiceFrameFingerprint = frame.getCorrectedVoiceFrameFingerprintV1();
        }
    }

    /**
     * CODEC Name constant
     */
    @Override
    public String getCodecName()
    {
        return CODEC_NAME;
    }

    private float[] applyToneAudioPreference(AMBEFrame frame, float[] audio)
    {
        if(!mToneAudioEnabled && frame.isToneFrame() && frame.getToneParameters().isValidTone())
        {
            return new float[audio.length];
        }

        return audio;
    }

    private static void validate(byte[] frameData)
    {
        if(frameData == null || frameData.length != FRAME_BYTE_LENGTH)
        {
            throw new IllegalArgumentException("AMBE frame must contain exactly " + FRAME_BYTE_LENGTH + " bytes");
        }
    }
}
