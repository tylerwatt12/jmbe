package jmbe;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class JMBEAudioLibraryTest
{
    @Test
    public void reportsVoiceQualityReleaseVersion()
    {
        JMBEAudioLibrary library = new JMBEAudioLibrary();

        assertEquals(1, library.getMajorVersion());
        assertEquals(0, library.getMinorVersion());
        assertEquals(14, library.getBuildVersion());
        assertEquals("JMBE Audio Conversion Library v1.0.14", library.getVersion());
    }
}
