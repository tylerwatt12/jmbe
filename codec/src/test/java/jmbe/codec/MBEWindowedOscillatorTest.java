package jmbe.codec;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.Test;

public class MBEWindowedOscillatorTest
{
    @Test
    public void previousWindowCutoffMatchesOriginalRecurrenceBitForBit() throws Exception
    {
        float[] amplitudes = {0.0f, -0.0f, 0.75f, -17.5f, Float.MAX_VALUE / 2, Float.MAX_VALUE};
        for(float amplitude: amplitudes)
        {
            for(float phase: new float[]{0.0f, -1.4f, (float)Math.PI, Float.MAX_VALUE})
            {
                for(float step: new float[]{0.0f, 0.091f, -0.34f})
                {
                    verify(amplitude, phase, step, 0, ordinaryDestination());
                    verify(amplitude, phase, step, -160, ordinaryDestination());
                }
            }
        }
    }

    @Test
    public void exceptionalInputsAndDestinationsRetainOriginalZeroAndNanBehavior() throws Exception
    {
        for(float special: new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
        {
            verify(special, 0.13f, 0.081f, 0, ordinaryDestination());
            verify(0.75f, special, 0.081f, 0, ordinaryDestination());
            verify(0.75f, 0.13f, special, 0, ordinaryDestination());
            float[] destination = ordinaryDestination();
            destination[123] = special;
            verify(0.75f, 0.13f, 0.081f, 0, destination);
        }
        float[] negativeZero = ordinaryDestination();
        Arrays.fill(negativeZero, 105, 160, -0.0f);
        verify(0.75f, 0.13f, 0.081f, 0, negativeZero);
        float[] nanPayload = ordinaryDestination();
        nanPayload[139] = Float.intBitsToFloat(0x7FC12345);
        verify(0.75f, 0.13f, 0.081f, 0, nanPayload);
    }

    private static float[] ordinaryDestination()
    {
        float[] destination = new float[160];
        for(int sample = 0; sample < 160; sample++) destination[sample] = (sample % 7 - 3) * 0.125f;
        return destination;
    }

    private static void verify(float amplitude, float phase, float step, int offset, float[] destination) throws Exception
    {
        float[] expected = destination.clone();
        Method window = MBESynthesizer.class.getDeclaredMethod("synthesisWindow", int.class);
        window.setAccessible(true);
        //Frozen reference: the original helper always advances all 160 local oscillator samples.
        double cosPhase = Math.cos(phase);
        double sinPhase = Math.sin(phase);
        double cosStep = Math.cos(step);
        double sinStep = Math.sin(step);
        float scaledAmplitude = 2.0f * amplitude;
        for(int sample = 0; sample < 160; sample++)
        {
            expected[sample] += scaledAmplitude * (float)window.invoke(null, sample + offset) * (float)cosPhase;
            double nextCosPhase = (cosPhase * cosStep) - (sinPhase * sinStep);
            sinPhase = (sinPhase * cosStep) + (cosPhase * sinStep);
            cosPhase = nextCosPhase;
        }
        Method oscillator = MBESynthesizer.class.getDeclaredMethod("addWindowedOscillator",
            float[].class, float.class, float.class, float.class, int.class);
        oscillator.setAccessible(true);
        oscillator.invoke(null, destination, amplitude, phase, step, offset);
        for(int sample = 0; sample < 160; sample++)
        {
            assertEquals("Sample " + sample + " offset " + offset,
                Float.floatToRawIntBits(expected[sample]), Float.floatToRawIntBits(destination[sample]));
        }
    }
}
