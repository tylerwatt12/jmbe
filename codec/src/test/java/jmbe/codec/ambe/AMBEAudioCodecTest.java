package jmbe.codec.ambe;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.HexFormat;
import jmbe.audio.FrameQualityMetadata;
import jmbe.iface.IAudioWithMetadata;
import org.junit.Test;

public class AMBEAudioCodecTest
{
    private static final byte[] FRAME = HexFormat.of().parseHex("000000000000000000");
    private static final byte[] TONE_FRAME = HexFormat.of().parseHex("89DABB538CD3E76600");

    @Test
    public void resetRestoresCallBoundaryState()
    {
        AMBEAudioCodec codec = new AMBEAudioCodec();
        float[] first = codec.getAudio(FRAME);
        codec.reset();

        assertArrayEquals(first, codec.getAudio(FRAME), 0.0f);
        assertEquals(160, first.length);
    }

    @Test
    public void rejectsMalformedFrames()
    {
        AMBEAudioCodec codec = new AMBEAudioCodec();
        assertThrows(IllegalArgumentException.class, () -> codec.getAudio(null));
        assertThrows(IllegalArgumentException.class, () -> codec.getAudio(new byte[8]));
        assertThrows(IllegalArgumentException.class, () -> codec.getAudio(new byte[10]));
    }

    @Test
    public void preservesLegacyMetadataUntilFrameQualityIsEnabled()
    {
        AMBEAudioCodec codec = new AMBEAudioCodec();
        IAudioWithMetadata legacyResult = codec.getAudioWithMetadata(FRAME);
        assertNull(legacyResult.getMetadata().get(FrameQualityMetadata.OUTCOME));

        codec.setVoiceQualityMetadataEnabled(true);
        IAudioWithMetadata result = codec.getAudioWithMetadata(FRAME);

        assertNotNull(result.getMetadata().get(FrameQualityMetadata.OUTCOME));
        assertTrue(Integer.parseInt(result.getMetadata().get(FrameQualityMetadata.FEC_ERRORS)) >= 0);
        assertEquals("47", result.getMetadata().get(FrameQualityMetadata.FEC_PROTECTED_BITS));
    }

    @Test
    public void toneAudioRemainsEnabledByDefault()
    {
        AMBEAudioCodec defaultCodec = new AMBEAudioCodec();
        AMBEAudioCodec explicitlyEnabledCodec = new AMBEAudioCodec();
        explicitlyEnabledCodec.setToneAudioEnabled(true);
        assertArrayEquals(defaultCodec.getAudio(FRAME), explicitlyEnabledCodec.getAudio(FRAME), 0.0f);

        float[] audio = defaultCodec.getAudio(TONE_FRAME);
        float[] explicitlyEnabledAudio = explicitlyEnabledCodec.getAudio(TONE_FRAME);

        assertEquals(160, audio.length);
        assertTrue(containsNonZeroSample(audio));
        assertArrayEquals(audio, explicitlyEnabledAudio, 0.0f);
    }

    @Test
    public void toneAudioCanBeMutedWithoutRemovingMetadata()
    {
        AMBEAudioCodec codec = new AMBEAudioCodec();
        codec.setToneAudioEnabled(false);
        codec.setVoiceQualityMetadataEnabled(true);
        IAudioWithMetadata result = codec.getAudioWithMetadata(TONE_FRAME);

        assertArrayEquals(new float[160], result.getAudio(), 0.0f);
        assertFalse(result.getMetadata().isEmpty());
        assertNotNull(result.getMetadata().get("TONE"));
        assertNotNull(result.getMetadata().get(FrameQualityMetadata.OUTCOME));
    }

    private static boolean containsNonZeroSample(float[] audio)
    {
        for(float sample: audio)
        {
            if(sample != 0.0f)
            {
                return true;
            }
        }

        return false;
    }
}
