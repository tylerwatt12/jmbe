package jmbe.codec;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HexFormat;
import jmbe.codec.ambe.AMBEAudioCodec;
import jmbe.codec.imbe.IMBEAudioCodec;
import jmbe.iface.IAudioCodec;
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

    @Test
    public void unvoicedScratchNeverEscapesAndResetClearsItForBothCodecs() throws Exception
    {
        verifyScratch(new AMBEAudioCodec(), new AMBEAudioCodec(), new byte[][]{
            FingerprintFixtures.ambe(0, 0, 0, 0), FingerprintFixtures.ambe(0, 73, 105, 90)});
        verifyScratch(new IMBEAudioCodec(), new IMBEAudioCodec(), new byte[][]{
            FingerprintFixtures.imbe(0, 0), FingerprintFixtures.imbe(134, 7)});
    }

    private static void verifyScratch(IAudioCodec codec, IAudioCodec control, byte[][] frames) throws Exception
    {
        Field synthesizerField = codec.getClass().getDeclaredField("mSynthesizer");
        synthesizerField.setAccessible(true);
        Object synthesizer = synthesizerField.get(codec);
        Field scratchField = MBESynthesizer.class.getDeclaredField("mUnvoicedSamples");
        scratchField.setAccessible(true);
        float[] scratch = (float[])scratchField.get(synthesizer);
        assertNotSame(scratch, scratchField.get(synthesizerField.get(control)));
        float[] first = codec.getAudio(frames[0]);
        float[] expectedFirst = control.getAudio(frames[0]);
        assertArrayEquals(expectedFirst, first, 0.0f);
        assertNotSame(first, scratch);

        //Callers own returned PCM. Mutating it must not affect the codec's next frame or its overlap history.
        Arrays.fill(first, Float.NaN);
        Arrays.fill(scratch, Float.NaN);
        float[] second = codec.getAudio(frames[1]);
        assertArrayEquals(control.getAudio(frames[1]), second, 0.0f);
        assertNotSame(second, scratch);
        assertSame(scratch, scratchField.get(synthesizer));
        float[] savedSecond = second.clone();
        for(int count = 0; count < 12; count++) codec.getAudio(frames[count % frames.length]);
        assertArrayEquals(savedSecond, second, 0.0f);

        for(String fieldName: new String[]{"mNoiseSamples", "mPreviousUw"})
        {
            Field history = MBESynthesizer.class.getDeclaredField(fieldName);
            history.setAccessible(true);
            assertNotSame(scratch, history.get(synthesizer));
        }
        codec.reset();
        assertArrayEquals(new float[160], scratch, 0.0f);
        assertArrayEquals(expectedFirst, codec.getAudio(frames[0]), 0.0f);
    }
}
