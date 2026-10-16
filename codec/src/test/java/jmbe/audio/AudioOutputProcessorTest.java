/* Copyright (C) 2026. Licensed under GPL-3.0-or-later. */
package jmbe.audio;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import jmbe.JMBEAudioLibrary;
import jmbe.audio.enhance.AudioEnhancementModelBundle;
import jmbe.audio.enhance.Codec;
import org.junit.Test;
import static org.junit.Assert.*;

public class AudioOutputProcessorTest
{
    private static final String MODEL_SHA = "3779c29761d97233873a991c5912e341dc2614c82f7b80dcd3b354eda994ed18";

    @Test public void bundlesTheExistingV4WeightsExactly() throws Exception
    {
        try(var input = getClass().getResourceAsStream("/jmbe/audio/enhance/voice-model.zip"))
        {
            assertNotNull(input);
            assertEquals(MODEL_SHA, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())));
        }
    }

    @Test public void automaticallyProcessesBothCodecsAndReportsNativeClockWithoutChangingInput() throws Exception
    {
        var processor = new JMBEAudioLibrary().getAudioOutputProcessor();
        byte[] bytes;
        try(var input = getClass().getResourceAsStream("/jmbe/audio/enhance/voice-model.zip")) { bytes = input.readAllBytes(); }
        var model = AudioEnhancementModelBundle.load(bytes);
        float[] audio = new float[1601];
        for(int i = 0; i < audio.length; i++) audio[i] = (float)(.05 * Math.sin(i * .3));
        float[] original = audio.clone();
        for(String codec: new String[]{"IMBE", "AMBE_PLUS_2"})
        {
            Map<String,Object> output = processor.process(audio, 8000, codec);
            assertEquals(2, output.size());
            assertEquals(16000, output.get("sample_rate"));
            float[] result = (float[])output.get("samples");
            assertEquals(audio.length * 2, result.length);
            assertNotSame(audio, result);
            assertArrayEquals(original, audio, 0);
            // The original V4 inference is retained, with only fixed peak attenuation when needed.
            float[] expected = model.enhance(original, Codec.valueOf(codec));
            float peak = 0;
            for(float sample: expected) peak = Math.max(peak, Math.abs(sample));
            if(peak > .95f)
            {
                float scale = Math.nextDown(.95f / peak);
                for(int i = 0; i < expected.length; i++) expected[i] *= scale;
            }
            assertArrayEquals(expected, result, 0);
            for(float sample: result) assertTrue(Float.isFinite(sample) && Math.abs(sample) <= .95f);
            assertArrayEquals(result, (float[])processor.process(audio, 8000, codec).get("samples"), 0);
        }
    }

    @Test public void peakProtectionOnlyAttenuatesTheCompleteRender() throws Exception
    {
        byte[] bytes;
        try(var input = getClass().getResourceAsStream("/jmbe/audio/enhance/voice-model.zip")) { bytes = input.readAllBytes(); }
        var model = AudioEnhancementModelBundle.load(bytes);
        float[] loud = new float[513];
        for(int i = 0; i < loud.length; i++) loud[i] = (float)(4 * Math.sin(i * .2));
        float[] unprotected = model.enhance(loud, Codec.IMBE);
        float peak = 0;
        for(float value: unprotected) peak = Math.max(peak, Math.abs(value));
        assertTrue(peak > .95f);
        float scale = Math.nextDown(.95f / peak);
        float[] actual = (float[])new AudioOutputProcessor().process(loud, 8000, "IMBE").get("samples");
        for(int i = 0; i < actual.length; i++)
        {
            assertEquals(unprotected[i] * scale, actual[i], 0);
            assertTrue(Math.abs(actual[i]) <= .95f);
        }
    }

    @Test public void unsupportedFormatsAndLongCallsPreserveAudioAndClockWithoutLoadingModel() throws Exception
    {
        var processor = new AudioOutputProcessor();
        float[] audio = {.1f, -.3f};
        for(String codec: new String[]{"NONE", "OTHER", null})
        {
            Map<String,Object> output = processor.process(audio, 8000, codec);
            assertEquals(8000, output.get("sample_rate"));
            assertNotSame(audio, output.get("samples"));
            assertArrayEquals(audio, (float[])output.get("samples"), 0);
        }
        assertEquals(16000, processor.process(audio, 16000, "IMBE").get("sample_rate"));
        float[] longCall = new float[120 * 8000 + 1];
        Arrays.fill(longCall, .125f);
        var output = processor.process(longCall, 8000, "IMBE");
        assertEquals(8000, output.get("sample_rate"));
        assertArrayEquals(longCall, (float[])output.get("samples"), 0);
        assertEquals(0, ((float[])processor.process(new float[0], 8000, "IMBE").get("samples")).length);
        var model = AudioOutputProcessor.class.getDeclaredField("mModel");
        model.setAccessible(true);
        assertNull(model.get(processor));
    }

    @Test public void rejectsMalformedAudioBeforeModelLoading() throws Exception
    {
        var processor = new AudioOutputProcessor();
        assertThrows(NullPointerException.class, () -> processor.process(null, 8000, "IMBE"));
        assertThrows(IllegalArgumentException.class, () -> processor.process(new float[]{Float.NaN}, 8000, "IMBE"));
        assertThrows(IllegalArgumentException.class, () -> processor.process(new float[]{Float.POSITIVE_INFINITY}, 8000, "IMBE"));
        assertThrows(IllegalArgumentException.class, () -> processor.process(new float[]{17}, 8000, "IMBE"));
        assertThrows(IllegalArgumentException.class, () -> processor.process(new float[]{0}, 0, "IMBE"));
    }

    @Test public void legacyFrameApisStillReturn160SamplesAt8kAndExposeNoProcessingOptions() throws Exception
    {
        var library = new JMBEAudioLibrary();
        assertEquals(17, library.getBuildVersion());
        byte[][] frames = {HexFormat.of().parseHex("6CC1D2A2F7A16810A463B0C759FA82E38E5E"), new byte[9]};
        String[] codecs = {"IMBE", "AMBE 3600 x 2450"};
        for(int i = 0; i < frames.length; i++)
        {
            var codec = library.getAudioConverter(codecs[i]);
            float[] raw = codec.getAudio(frames[i]);
            codec.reset();
            assertEquals(160, raw.length);
            assertArrayEquals(raw, codec.getAudioWithMetadata(frames[i]).getAudio(), 0);
        }
        assertThrows(NoSuchMethodException.class, () -> JMBEAudioLibrary.class.getMethod("getVocoderAudioProcessor"));
        assertThrows(NoSuchMethodException.class, () -> AudioOutputProcessor.class.getMethod("getModelInfo"));
        assertThrows(ClassNotFoundException.class, () -> Class.forName("jmbe.audio.ContinuousAudioGain"));
    }
}
