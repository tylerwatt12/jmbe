package jmbe;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class JMBEAudioLibraryTest
{
    @Test
    public void reportsToneAudioPreferenceReleaseVersion()
    {
        JMBEAudioLibrary library = new JMBEAudioLibrary();

        assertEquals(1, library.getMajorVersion());
        assertEquals(0, library.getMinorVersion());
        assertEquals(15, library.getBuildVersion());
        assertEquals("JMBE Audio Conversion Library v1.0.15", library.getVersion());
    }
}
