package jmbe.codec.ambe;

import static org.junit.Assert.*;
import java.util.HexFormat;
import jmbe.audio.FrameQualityMetadata;
import jmbe.codec.FingerprintFixtures;
import jmbe.iface.IAudioWithMetadata;
import org.junit.Test;

public class AMBECorrectedVoiceFrameFingerprintTest
{
    private static final byte[] SPEECH = FingerprintFixtures.ambe(0, 0, 0, 0);
    private static final byte[] TONE = HexFormat.of().parseHex("89DABB538CD3E76600");

    @Test
    public void completeProtectedWordCorrectionsRetainTheSameSpeechEvidenceAndAudio()
    {
        AMBEAudioCodec clean = enabled();
        IAudioWithMetadata cleanAudio = clean.getAudioWithMetadata(SPEECH);
        long expected = clean.getCorrectedVoiceFrameFingerprintV1();
        assertEquals("V1 canonical stream is stable", 0xF9BE44DA3AC10D7BL, expected);
        assertEquals("DECODED", cleanAudio.getMetadata().get(FrameQualityMetadata.OUTCOME));
        for(int[] vector: new int[][]{FingerprintFixtures.AMBE_C0, FingerprintFixtures.AMBE_C1})
        {
            for(int bit: vector)
            {
                byte[] damaged = SPEECH.clone();
                FingerprintFixtures.flip(damaged, bit);
                AMBEAudioCodec receiver = enabled();
                IAudioWithMetadata audio = receiver.getAudioWithMetadata(damaged);
                assertEquals("Bit " + bit, expected, receiver.getCorrectedVoiceFrameFingerprintV1());
                assertEquals("1", audio.getMetadata().get(FrameQualityMetadata.FEC_ERRORS));
                assertEquals("DECODED", audio.getMetadata().get(FrameQualityMetadata.OUTCOME));
                assertArrayEquals(cleanAudio.getAudio(), audio.getAudio(), 0.0f);
            }
            byte[] damaged = SPEECH.clone();
            for(int index = 0; index < 3; index++) FingerprintFixtures.flip(damaged, vector[index]);
            AMBEAudioCodec receiver = enabled();
            IAudioWithMetadata audio = receiver.getAudioWithMetadata(damaged);
            assertEquals(expected, receiver.getCorrectedVoiceFrameFingerprintV1());
            assertEquals("3", audio.getMetadata().get(FrameQualityMetadata.FEC_ERRORS));
            assertArrayEquals(cleanAudio.getAudio(), audio.getAudio(), 0.0f);
        }
    }

    @Test
    public void everyUnprotectedBitRemainsPartOfTheEvidence()
    {
        AMBEAudioCodec clean = enabled(); clean.getAudio(SPEECH);
        long expected = clean.getCorrectedVoiceFrameFingerprintV1();
        for(int[] vector: new int[][]{FingerprintFixtures.AMBE_C2, FingerprintFixtures.AMBE_C3})
        {
            for(int bit: vector)
            {
                byte[] changed = SPEECH.clone(); FingerprintFixtures.flip(changed, bit);
                AMBEAudioCodec receiver = enabled(); receiver.getAudio(changed);
                assertNotEquals("Bit " + bit, 0L, receiver.getCorrectedVoiceFrameFingerprintV1());
                assertNotEquals("Bit " + bit, expected, receiver.getCorrectedVoiceFrameFingerprintV1());
            }
        }
    }

    @Test
    public void optInDoesNotChangeAudioOrMetadataAndBothAudioApisPublishEvidence()
    {
        AMBEAudioCodec legacy = new AMBEAudioCodec(), receiver = new AMBEAudioCodec();
        receiver.setCorrectedVoiceFrameFingerprintEnabled(true);
        for(byte[] input: new byte[][]{SPEECH, FingerprintFixtures.ambe(0, 25, 141, 67), TONE})
        {
            IAudioWithMetadata oldAudio = legacy.getAudioWithMetadata(input), newAudio = receiver.getAudioWithMetadata(input);
            assertArrayEquals(oldAudio.getAudio(), newAudio.getAudio(), 0.0f);
            assertEquals(oldAudio.getMetadata(), newAudio.getMetadata());
            assertEquals(0L, legacy.getCorrectedVoiceFrameFingerprintV1());
        }
        receiver.reset(); receiver.getAudio(SPEECH);
        assertEquals(0xF9BE44DA3AC10D7BL, receiver.getCorrectedVoiceFrameFingerprintV1());
    }

    @Test
    public void failedCorrectionRepeatedAndConcealedAudioNeverCarryCurrentSpeechEvidence()
    {
        byte[] failed = SPEECH.clone();
        for(int bit = 0; bit < 4; bit++) FingerprintFixtures.flip(failed, FingerprintFixtures.AMBE_C0[bit]);
        //The decoder can report three corrections after four errors while leaving C0 odd parity.
        //Such a visible failed correction must not become evidence, regardless of its synthesis outcome.
        assertEquals(0L, new AMBEFrame(failed, true).getCorrectedVoiceFrameFingerprintV1());
        AMBEAudioCodec receiver = enabled(); receiver.getAudio(SPEECH);
        receiver.getAudioWithMetadata(failed);
        assertEquals(0L, receiver.getCorrectedVoiceFrameFingerprintV1());

        byte[] repeated = SPEECH.clone();
        for(int[] vector: new int[][]{FingerprintFixtures.AMBE_C0, FingerprintFixtures.AMBE_C1})
            for(int bit = 0; bit < 3; bit++) FingerprintFixtures.flip(repeated, vector[bit]);
        receiver.reset(); receiver.getAudio(SPEECH);
        for(int count = 1; count <= 4; count++)
        {
            IAudioWithMetadata audio = receiver.getAudioWithMetadata(repeated);
            assertEquals(count < 4 ? "REPEATED" : "CONCEALED", audio.getMetadata().get(FrameQualityMetadata.OUTCOME));
            assertEquals(0L, receiver.getCorrectedVoiceFrameFingerprintV1());
        }
    }

    @Test
    public void silenceToneErasureAndMutedToneCannotReuseEarlierSpeechEvidence()
    {
        for(byte[] nonSpeech: new byte[][]{FingerprintFixtures.ambe(0xF00, 0, 0, 4 << 9),
            FingerprintFixtures.ambe(0xF00, 0, 0, 0), TONE})
        {
            AMBEAudioCodec receiver = enabled(); receiver.getAudio(SPEECH);
            assertNotEquals(0L, receiver.getCorrectedVoiceFrameFingerprintV1());
            receiver.getAudioWithMetadata(nonSpeech);
            assertEquals(0L, receiver.getCorrectedVoiceFrameFingerprintV1());
        }
        AMBEAudioCodec receiver = enabled(); receiver.getAudio(SPEECH); receiver.setToneAudioEnabled(false);
        assertArrayEquals(new float[160], receiver.getAudio(TONE), 0.0f);
        assertEquals(0L, receiver.getCorrectedVoiceFrameFingerprintV1());
    }

    @Test
    public void invalidInputResetAndDisablingClearPreviouslyPublishedEvidence()
    {
        AMBEAudioCodec receiver = enabled(); receiver.getAudio(SPEECH);
        assertThrows(IllegalArgumentException.class, () -> receiver.getAudioWithMetadata(new byte[8]));
        assertEquals(0L, receiver.getCorrectedVoiceFrameFingerprintV1());
        receiver.getAudio(SPEECH);
        assertThrows(IllegalArgumentException.class, () -> receiver.getAudio(null));
        assertEquals(0L, receiver.getCorrectedVoiceFrameFingerprintV1());
        receiver.getAudio(SPEECH); receiver.reset(); assertEquals(0L, receiver.getCorrectedVoiceFrameFingerprintV1());
        receiver.getAudio(SPEECH); receiver.setCorrectedVoiceFrameFingerprintEnabled(false);
        assertEquals(0L, receiver.getCorrectedVoiceFrameFingerprintV1());
        receiver.getAudio(SPEECH); assertEquals(0L, receiver.getCorrectedVoiceFrameFingerprintV1());
        receiver.setCorrectedVoiceFrameFingerprintEnabled(true);
        assertEquals(0L, receiver.getCorrectedVoiceFrameFingerprintV1());
    }

    private static AMBEAudioCodec enabled()
    {
        AMBEAudioCodec codec = new AMBEAudioCodec();
        codec.setVoiceQualityMetadataEnabled(true);
        codec.setCorrectedVoiceFrameFingerprintEnabled(true);
        return codec;
    }
}
