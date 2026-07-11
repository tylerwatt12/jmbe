package jmbe.codec;

import static org.junit.Assert.assertNotSame;

import java.lang.reflect.Field;
import java.util.HexFormat;
import jmbe.codec.imbe.IMBEAudioCodec;
import org.junit.Test;

public class MBESynthesizerBufferTest
{
    @Test
    public void previousOverlapBufferIsNotNextFrameInput() throws Exception
    {
        IMBEAudioCodec codec = new IMBEAudioCodec();
        codec.getAudio(HexFormat.of().parseHex("6CC1D2A2F7A16810A463B0C759FA82E38E5E"));

        Field synthesizerField = IMBEAudioCodec.class.getDeclaredField("mSynthesizer");
        synthesizerField.setAccessible(true);
        Object synthesizer = synthesizerField.get(codec);
        Field noiseSamplesField = MBESynthesizer.class.getDeclaredField("mNoiseSamples");
        Field previousUwField = MBESynthesizer.class.getDeclaredField("mPreviousUw");
        noiseSamplesField.setAccessible(true);
        previousUwField.setAccessible(true);

        assertNotSame(noiseSamplesField.get(synthesizer), previousUwField.get(synthesizer));
    }
}
