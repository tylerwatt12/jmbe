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

import jmbe.codec.MBEModelParameters;
import jmbe.codec.MBESynthesizer;
import jmbe.codec.VoiceFrameSynthesis;

class AMBESynthesizer extends MBESynthesizer
{
    private ToneGenerator mToneGenerator = new ToneGenerator();
    private AMBEModelParameters mPreviousFrame = new AMBEModelParameters();

    /**
     * Previous AMBE frame parameters
     *
     * @return parameters
     */
    @Override
    protected MBEModelParameters getPreviousFrame()
    {
        return mPreviousFrame;
    }

    @Override
    protected void reset()
    {
        super.reset();
        mToneGenerator.reset();
        mPreviousFrame = new AMBEModelParameters();
    }

    /**
     * Generates 160 samples (20 ms) of tone audio
     *
     * @param toneParameters to use in generating the tone frame
     * @return samples
     */
    private float[] getTone(ToneParameters toneParameters)
    {
        return mToneGenerator.generate(toneParameters);
    }

    /**
     * Generates 160 samples (20 ms) of audio from the ambe frame.  Can decode both audio and tone frames and handles
     * frame repeats and white noise generation when error rate exceeds thresholds.
     *
     * @param frame of audio
     * @return decoded audio samples
     */
    float[] getAudio(AMBEFrame frame)
    {
        return synthesize(frame).audio();
    }

    /**
     * Generates audio and reports the action used to synthesize the frame.
     */
    VoiceFrameSynthesis synthesize(AMBEFrame frame)
    {
        VoiceFrameSynthesis synthesis = frame.isToneFrame() ? getToneFrameAudio(frame) : getVoiceFrameAudio(frame);
        float[] audio = synthesis.audio();

        if(audio == null)
        {
            audio = new float[SAMPLES_PER_FRAME];
        }

        return new VoiceFrameSynthesis(audio, synthesis.outcome());
    }

    private VoiceFrameSynthesis getToneFrameAudio(AMBEFrame frame)
    {
        if(frame.getToneParameters().isValidTone())
        {
            return new VoiceFrameSynthesis(getTone(frame.getToneParameters()), VoiceFrameSynthesis.Outcome.DECODED);
        }

        mPreviousFrame.setRepeatCount(mPreviousFrame.getRepeatCount() + 1);

        if(!mPreviousFrame.isMaxFrameRepeat())
        {
            return new VoiceFrameSynthesis(getVoice(mPreviousFrame), VoiceFrameSynthesis.Outcome.REPEATED);
        }

        return concealFrame();
    }

    private VoiceFrameSynthesis getVoiceFrameAudio(AMBEFrame frame)
    {
        AMBEModelParameters parameters = frame.getVoiceParameters(mPreviousFrame);

        if(parameters.isMaxFrameRepeat())
        {
            return concealFrame();
        }

        if(parameters.isErasureFrame())
        {
            mPreviousFrame = parameters;
            return new VoiceFrameSynthesis(getWhiteNoise(), VoiceFrameSynthesis.Outcome.CONCEALED);
        }

        float[] audio = getVoice(parameters);
        mPreviousFrame = parameters;

        return new VoiceFrameSynthesis(audio, parameters.getRepeatCount() > 0 ?
            VoiceFrameSynthesis.Outcome.REPEATED : VoiceFrameSynthesis.Outcome.DECODED);
    }

    private VoiceFrameSynthesis concealFrame()
    {
        mPreviousFrame = new AMBEModelParameters();
        return new VoiceFrameSynthesis(getWhiteNoise(), VoiceFrameSynthesis.Outcome.CONCEALED);
    }
}
