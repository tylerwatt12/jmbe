package jmbe.codec.imbe;

import static org.junit.Assert.*;
import jmbe.audio.FrameQualityMetadata;
import jmbe.codec.FingerprintFixtures;
import jmbe.iface.IAudioWithMetadata;
import org.junit.Test;

public class IMBECorrectedVoiceFrameFingerprintTest
{
    private static final byte[] SPEECH = FingerprintFixtures.imbe(0, 0);

    @Test
    public void everyProtectedBitCorrectsToTheSameEvidenceAndAudio()
    {
        IMBEAudioCodec clean = enabled();
        IAudioWithMetadata cleanAudio = clean.getAudioWithMetadata(SPEECH);
        long expected = clean.getCorrectedVoiceFrameFingerprintV1();
        assertEquals("V1 canonical stream is stable", 0x3BA4E05ABAAB867AL, expected);
        assertEquals("DECODED", cleanAudio.getMetadata().get(FrameQualityMetadata.OUTCOME));
        for(int bit = 0; bit < 137; bit++)
        {
            byte[] damaged = SPEECH.clone(); FingerprintFixtures.flipImbe(damaged, bit);
            IMBEAudioCodec receiver = enabled();
            IAudioWithMetadata audio = receiver.getAudioWithMetadata(damaged);
            assertEquals("Bit " + bit, expected, receiver.getCorrectedVoiceFrameFingerprintV1());
            assertEquals("1", audio.getMetadata().get(FrameQualityMetadata.FEC_ERRORS));
            assertEquals("DECODED", audio.getMetadata().get(FrameQualityMetadata.OUTCOME));
            assertArrayEquals(cleanAudio.getAudio(), audio.getAudio(), 0.0f);
        }
        for(int block = 0; block < 4; block++)
        {
            byte[] damaged = SPEECH.clone();
            for(int bit = 0; bit < 3; bit++) FingerprintFixtures.flipImbe(damaged, block * 23 + bit);
            IMBEAudioCodec receiver = enabled(); IAudioWithMetadata audio = receiver.getAudioWithMetadata(damaged);
            assertEquals(expected, receiver.getCorrectedVoiceFrameFingerprintV1());
            assertEquals("3", audio.getMetadata().get(FrameQualityMetadata.FEC_ERRORS));
            assertArrayEquals(cleanAudio.getAudio(), audio.getAudio(), 0.0f);
        }
    }

    @Test
    public void allSevenUnprotectedBitsRemainPartOfTheEvidence()
    {
        IMBEAudioCodec clean = enabled(); clean.getAudio(SPEECH);
        long expected = clean.getCorrectedVoiceFrameFingerprintV1();
        for(int bit = 137; bit < 144; bit++)
        {
            byte[] changed = SPEECH.clone(); FingerprintFixtures.flipImbe(changed, bit);
            IMBEAudioCodec receiver = enabled(); receiver.getAudio(changed);
            assertNotEquals(0L, receiver.getCorrectedVoiceFrameFingerprintV1());
            assertNotEquals("Bit " + bit, expected, receiver.getCorrectedVoiceFrameFingerprintV1());
        }
    }

    @Test
    public void optInLeavesAudioAndMetadataUnchangedAndPlainAudioApiPublishesEvidence()
    {
        IMBEAudioCodec legacy = new IMBEAudioCodec(), receiver = new IMBEAudioCodec();
        receiver.setCorrectedVoiceFrameFingerprintEnabled(true);
        for(int variation = 0; variation < 16; variation++)
        {
            byte[] frame = FingerprintFixtures.imbe(134, variation);
            IAudioWithMetadata oldAudio = legacy.getAudioWithMetadata(frame), newAudio = receiver.getAudioWithMetadata(frame);
            assertArrayEquals(oldAudio.getAudio(), newAudio.getAudio(), 0.0f);
            assertEquals(oldAudio.getMetadata(), newAudio.getMetadata());
            assertEquals(0L, legacy.getCorrectedVoiceFrameFingerprintV1());
            assertNotEquals(0L, receiver.getCorrectedVoiceFrameFingerprintV1());
        }
        receiver.reset(); receiver.getAudio(SPEECH);
        assertEquals(0x3BA4E05ABAAB867AL, receiver.getCorrectedVoiceFrameFingerprintV1());
    }

    @Test
    public void invalidFundamentalRepeatedAndConcealedAudioCannotReuseEarlierEvidence()
    {
        byte[] invalid = FingerprintFixtures.imbe(255, 0);
        assertEquals(IMBEFundamentalFrequency.INVALID, new IMBEFrame(invalid).getFundamentalFrequency());
        IMBEAudioCodec receiver = enabled(); receiver.getAudio(SPEECH);
        for(int count = 1; count <= 4; count++)
        {
            IAudioWithMetadata audio = receiver.getAudioWithMetadata(invalid);
            assertEquals(count < 4 ? "REPEATED" : "CONCEALED", audio.getMetadata().get(FrameQualityMetadata.OUTCOME));
            assertEquals(0L, receiver.getCorrectedVoiceFrameFingerprintV1());
        }
    }

    @Test
    public void accumulatedCorrectedErrorsCanConcealANewFrameWithoutPublishingItsPayload()
    {
        byte[] noisy = SPEECH.clone();
        FingerprintFixtures.flipImbe(noisy, 0);
        for(int block = 1; block < 4; block++)
            for(int bit = 0; bit < 3; bit++) FingerprintFixtures.flipImbe(noisy, block * 23 + bit);
        for(int block = 0; block < 3; block++) FingerprintFixtures.flipImbe(noisy, 92 + block * 15);
        IMBEAudioCodec receiver = enabled(); boolean concealed = false;
        for(int count = 0; count < 80; count++)
        {
            IAudioWithMetadata audio = receiver.getAudioWithMetadata(noisy);
            if("CONCEALED".equals(audio.getMetadata().get(FrameQualityMetadata.OUTCOME)))
            {
                assertEquals(0L, receiver.getCorrectedVoiceFrameFingerprintV1());
                concealed = true;
                break;
            }
        }
        assertTrue("Current decoded bits must not become evidence when synthesis conceals them", concealed);
    }

    @Test
    public void invalidInputResetAndDisablingClearPreviouslyPublishedEvidence()
    {
        IMBEAudioCodec receiver = enabled(); receiver.getAudio(SPEECH);
        assertThrows(IllegalArgumentException.class, () -> receiver.getAudioWithMetadata(new byte[17]));
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

    private static IMBEAudioCodec enabled()
    {
        IMBEAudioCodec codec = new IMBEAudioCodec();
        codec.setVoiceQualityMetadataEnabled(true);
        codec.setCorrectedVoiceFrameFingerprintEnabled(true);
        return codec;
    }
}
