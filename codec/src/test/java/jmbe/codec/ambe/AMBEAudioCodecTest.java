package jmbe.codec.ambe;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.HexFormat;
import jmbe.audio.FrameQualityMetadata;
import jmbe.iface.IAudioWithMetadata;
import org.junit.Test;

public class AMBEAudioCodecTest
{
    private static final byte[] FRAME = HexFormat.of().parseHex("000000000000000000");

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
    public void exposesBoundedFrameQualityMetadata()
    {
        IAudioWithMetadata result = new AMBEAudioCodec().getAudioWithMetadata(FRAME);

        assertNotNull(result.getMetadata().get(FrameQualityMetadata.OUTCOME));
        assertTrue(Integer.parseInt(result.getMetadata().get(FrameQualityMetadata.FEC_ERRORS)) >= 0);
        assertEquals("47", result.getMetadata().get(FrameQualityMetadata.FEC_PROTECTED_BITS));
    }
}
