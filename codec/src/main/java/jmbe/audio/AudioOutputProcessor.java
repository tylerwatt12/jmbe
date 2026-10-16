/* Copyright (C) 2026. Licensed under GPL-3.0-or-later. */
package jmbe.audio;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import jmbe.audio.enhance.AudioEnhancementModelBundle;
import jmbe.audio.enhance.Codec;

/**
 * Completed-call audio output for the optional 16 kHz build. The supplied sample rate is part of the result, so hosts
 * need no build, model or processing-mode knowledge. Legacy frame converters remain unchanged at 8 kHz.
 * Use on a bounded completed-call worker, never on a decoder callback. Model weights are immutable and inference
 * workspaces are local to each call, allowing one provider to serve multiple workers safely.
 */
public final class AudioOutputProcessor
{
    private static final String MODEL_RESOURCE = "/jmbe/audio/enhance/voice-model.zip";
    private static final int MAXIMUM_INPUT_SAMPLES = 16_777_216;
    private volatile AudioEnhancementModelBundle mModel;

    public AudioOutputProcessor() {}

    /**
     * Returns {@code samples} (float[]) and {@code sample_rate} (Integer). The input is never modified or returned by
     * reference. Supported complete IMBE/AMBE_PLUS_2 calls of at most 120 seconds become exactly twice as many samples
     * at 16 kHz. Empty audio, other codecs/rates and longer calls pass through at their original rate. This provider
     * has no automatic gain control, configuration switches or external model loading. Malformed input/model failures throw;
     * the host may retain its original audio and format. Calls longer than the 64 MiB PCM allocation bound are rejected.
     */
    public Map<String,Object> process(float[] samples, int sampleRate, String codec)
    {
        Objects.requireNonNull(samples, "Audio samples are required");
        if(sampleRate < 1) throw new IllegalArgumentException("A positive sample rate is required");
        if(samples.length > MAXIMUM_INPUT_SAMPLES)
            throw new IllegalArgumentException("Audio exceeds the completed-call processing limit");
        for(float value: samples)
        {
            if(!Float.isFinite(value) || Math.abs(value) > 16)
                throw new IllegalArgumentException("Audio contains invalid decoder-level samples");
        }
        Codec family = parseCodec(codec);
        if(sampleRate != 8000 || family == null || samples.length == 0 ||
            samples.length > AudioEnhancementModelBundle.MAX_INPUT_SAMPLES)
        {
            return output(samples.clone(), sampleRate);
        }
        AudioEnhancementModelBundle model = model();
        if(samples.length > model.maximumInputSamples() || !model.supports(family))
            return output(samples.clone(), sampleRate);
        float[] result = model.enhance(samples, family);
        if(result.length != samples.length * 2)
            throw new IllegalStateException("Output duration differs from input");
        // One fixed attenuation for the completed render; no boost or time-varying gain.
        float peak = 0;
        for(float value: result) peak = Math.max(peak, Math.abs(value));
        if(peak > 0.95f)
        {
            float scale = Math.nextDown(0.95f / peak);
            for(int i = 0; i < result.length; i++) result[i] *= scale;
        }
        return output(result, 16000);
    }

    private static Map<String,Object> output(float[] samples, int sampleRate)
    {
        return Map.of("samples", samples, "sample_rate", sampleRate);
    }

    private static Codec parseCodec(String name)
    {
        if("IMBE".equals(name)) return Codec.IMBE;
        if("AMBE_PLUS_2".equals(name)) return Codec.AMBE_PLUS_2;
        return null;
    }

    private AudioEnhancementModelBundle model()
    {
        AudioEnhancementModelBundle result = mModel;
        if(result == null)
        {
            synchronized(this)
            {
                result = mModel;
                if(result == null)
                {
                    try(InputStream input = AudioOutputProcessor.class.getResourceAsStream(MODEL_RESOURCE))
                    {
                        if(input == null) throw new IOException("Bundled audio model is missing");
                        result = AudioEnhancementModelBundle.load(input.readNBytes(
                            AudioEnhancementModelBundle.MAX_BUNDLE_BYTES + 1));
                        mModel = result;
                    }
                    catch(IOException e)
                    {
                        throw new IllegalStateException("Bundled audio model could not be loaded", e);
                    }
                }
            }
        }
        return result;
    }
}
