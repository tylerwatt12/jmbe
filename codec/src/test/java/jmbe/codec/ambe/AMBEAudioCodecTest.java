package jmbe.codec.ambe;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.HexFormat;
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
}
