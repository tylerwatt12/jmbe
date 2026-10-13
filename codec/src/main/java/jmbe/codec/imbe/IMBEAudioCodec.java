package jmbe.codec.imbe;

/*******************************************************************************
 * jmbe - Java MBE Library
 * Copyright (C) 2015 Dennis Sheirer
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
 ******************************************************************************/

import jmbe.audio.AudioWithMetadata;
import jmbe.audio.FrameQualityMetadata;
import jmbe.codec.VoiceFrameSynthesis;
import jmbe.iface.IAudioCodec;
import jmbe.iface.IAudioWithMetadata;

public class IMBEAudioCodec implements IAudioCodec
{
    public static final String CODEC_NAME = "IMBE";
    private static final int FRAME_BYTE_LENGTH = 18;

    private IMBESynthesizer mSynthesizer;
    private boolean mVoiceQualityMetadataEnabled;
    private boolean mCorrectedVoiceFrameFingerprintEnabled;
    private long mCorrectedVoiceFrameFingerprint;

    public IMBEAudioCodec()
    {
        mSynthesizer = new IMBESynthesizer();
    }

    @Override
    public void reset()
    {
        mCorrectedVoiceFrameFingerprint = 0;
        mSynthesizer.reset();
    }

    /**
     * Converts imbe frame data into PCM audio samples at 8kHz 16-bit rate
     */
    public float[] getAudio(byte[] frameData)
    {
        mCorrectedVoiceFrameFingerprint = 0;
        validate(frameData);
        IMBEFrame frame = new IMBEFrame(frameData, mCorrectedVoiceFrameFingerprintEnabled);
        VoiceFrameSynthesis synthesis = mSynthesizer.synthesize(frame);
        captureCorrectedVoiceFrameFingerprint(frame, synthesis);
        return synthesis.audio();
    }

    /**
     * Converts imbe frame data into PCM audio samples at 8kHz 16-bit rate
     *
     * Note: this method is for compatibility with the AMBE synthesizer and does not return any metadata.
     *
     * @param frameData byte array for an audio frame
     * @return audio with empty metadata.
     */
    @Override
    public IAudioWithMetadata getAudioWithMetadata(byte[] frameData)
    {
        mCorrectedVoiceFrameFingerprint = 0;
        validate(frameData);
        IMBEFrame frame = new IMBEFrame(frameData, mCorrectedVoiceFrameFingerprintEnabled);
        VoiceFrameSynthesis synthesis = mSynthesizer.synthesize(frame);
        captureCorrectedVoiceFrameFingerprint(frame, synthesis);

        if(!mVoiceQualityMetadataEnabled)
        {
            return AudioWithMetadata.create(synthesis.audio());
        }

        return AudioWithMetadata.create(synthesis.audio(), FrameQualityMetadata.create(null, synthesis,
            frame.getFecErrorCount(), IMBEFrame.FEC_PROTECTED_BITS));
    }

    /**
     * Enables the optional per-frame quality metadata consumed by newer SDRTrunk versions. This is disabled by
     * default so that older callers continue to receive empty metadata.
     */
    public void setVoiceQualityMetadataEnabled(boolean enabled)
    {
        mVoiceQualityMetadataEnabled = enabled;
    }

    /** Enables optional V1 corrected speech evidence. Disabled by default; changing this setting clears evidence. */
    public void setCorrectedVoiceFrameFingerprintEnabled(boolean enabled)
    {
        mCorrectedVoiceFrameFingerprintEnabled = enabled;
        mCorrectedVoiceFrameFingerprint = 0;
    }

    /**
     * Current successfully decoded speech frame's corrected payload evidence, or zero when unavailable.
     * Failed correction, invalid fundamental frequency, repeated/concealed audio, invalid input, and reset clear it.
     * Codec instances remain confined to their caller's decoding sequence, just like the audio synthesis state.
     */
    public long getCorrectedVoiceFrameFingerprintV1()
    {
        return mCorrectedVoiceFrameFingerprint;
    }

    private void captureCorrectedVoiceFrameFingerprint(IMBEFrame frame, VoiceFrameSynthesis synthesis)
    {
        if(mCorrectedVoiceFrameFingerprintEnabled && synthesis.outcome() == VoiceFrameSynthesis.Outcome.DECODED)
        {
            mCorrectedVoiceFrameFingerprint = frame.getCorrectedVoiceFrameFingerprintV1();
        }
    }

    /**
     * CODEC Name
     */
    @Override
    public String getCodecName()
    {
        return CODEC_NAME;
    }

    private static void validate(byte[] frameData)
    {
        if(frameData == null || frameData.length != FRAME_BYTE_LENGTH)
        {
            throw new IllegalArgumentException("IMBE frame must contain exactly " + FRAME_BYTE_LENGTH + " bytes");
        }
    }
}
