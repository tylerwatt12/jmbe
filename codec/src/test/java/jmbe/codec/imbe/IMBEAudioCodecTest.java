package jmbe.codec.imbe;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.HexFormat;
import jmbe.audio.FrameQualityMetadata;
import jmbe.iface.IAudioWithMetadata;
import org.junit.Test;

public class IMBEAudioCodecTest
{
    private static final byte[] FRAME_1 = HexFormat.of().parseHex("6CC1D2A2F7A16810A463B0C759FA82E38E5E");
    private static final byte[] FRAME_2 = HexFormat.of().parseHex("7AC2B5A637E734A5000DE2E6211BE38DDE58");

    @Test
    public void resetRestoresCallBoundaryState()
    {
        IMBEAudioCodec codec = new IMBEAudioCodec();
        float[] first = codec.getAudio(FRAME_1);
        float[] second = codec.getAudio(FRAME_2);
        codec.reset();

        assertArrayEquals(first, codec.getAudio(FRAME_1), 0.0f);
        assertArrayEquals(second, codec.getAudio(FRAME_2), 0.0f);
        assertEquals(160, second.length);
    }

    @Test
    public void rejectsMalformedFrames()
    {
        IMBEAudioCodec codec = new IMBEAudioCodec();
        assertThrows(IllegalArgumentException.class, () -> codec.getAudio(null));
        assertThrows(IllegalArgumentException.class, () -> codec.getAudio(new byte[17]));
        assertThrows(IllegalArgumentException.class, () -> codec.getAudio(new byte[19]));
    }

    @Test
    public void exposesBoundedFrameQualityMetadata()
    {
        IAudioWithMetadata result = new IMBEAudioCodec().getAudioWithMetadata(FRAME_1);

        assertNotNull(result.getMetadata().get(FrameQualityMetadata.OUTCOME));
        assertTrue(Integer.parseInt(result.getMetadata().get(FrameQualityMetadata.FEC_ERRORS)) >= 0);
        assertEquals("137", result.getMetadata().get(FrameQualityMetadata.FEC_PROTECTED_BITS));
    }
}
