package io.github.dsheirer.jmbe.creator.github;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VersionTest
{
    @Test
    public void comparesEqualVersionsAsEqual()
    {
        Version version = Version.fromString("v1.0.12");
        assertEquals(version, Version.fromString("1.0.12"));
        assertEquals(0, version.compareTo(Version.fromString("v1.0.12")));
        assertTrue(Version.fromString("v1.0.12a").compareTo(version) > 0);
    }

    @Test
    public void rejectsMalformedSeparatorsAndPatches()
    {
        assertNull(Version.fromString("v1x0x12"));
        assertNull(Version.fromString("v1.0.12alpha"));
    }
}
