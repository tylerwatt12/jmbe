package jmbe.audio;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;
import jmbe.codec.VoiceFrameSynthesis;
import org.junit.Test;

public class FrameQualityMetadataTest
{
    private static final VoiceFrameSynthesis SYNTHESIS =
        new VoiceFrameSynthesis(new float[160], VoiceFrameSynthesis.Outcome.REPEATED);

    @Test
    public void voiceOnlyMapPreservesValuesAndImmutability()
    {
        for(int protectedBits: new int[]{47, 137, 99, -1})
        {
            Map<String,String> expected = Map.of(FrameQualityMetadata.OUTCOME, "REPEATED",
                FrameQualityMetadata.FEC_ERRORS, "0", FrameQualityMetadata.FEC_PROTECTED_BITS,
                Integer.toString(Math.max(0, protectedBits)));
            assertEquals(expected, FrameQualityMetadata.create(null, SYNTHESIS, -3, protectedBits));
            Map<String,String> result = FrameQualityMetadata.create(Map.of(), SYNTHESIS, -3, protectedBits);
            assertEquals(expected, result);
            assertThrows(UnsupportedOperationException.class, () -> result.put("new", "value"));
        }
    }

    @Test
    public void toneMetadataIsCopiedAndQualityKeysOverrideEarlierValues()
    {
        Map<String,String> input = new HashMap<>();
        input.put("TONE", "440");
        input.put(FrameQualityMetadata.OUTCOME, "stale");
        input.put(FrameQualityMetadata.FEC_ERRORS, "stale");
        input.put(FrameQualityMetadata.FEC_PROTECTED_BITS, "stale");
        Map<String,String> result = FrameQualityMetadata.create(input, SYNTHESIS, 2, 47);
        assertEquals(Map.of("TONE", "440", FrameQualityMetadata.OUTCOME, "REPEATED",
            FrameQualityMetadata.FEC_ERRORS, "2", FrameQualityMetadata.FEC_PROTECTED_BITS, "47"), result);
        input.put("TONE", "880");
        assertEquals("440", result.get("TONE"));
        assertThrows(UnsupportedOperationException.class, () -> result.put("new", "value"));
    }
}
