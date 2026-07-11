package io.github.dsheirer.jmbe.creator;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import io.github.dsheirer.jmbe.creator.github.Version;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CreatorTest
{
    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Test
    public void compilationFailureStopsCreation() throws Exception
    {
        Path source = mTemporaryFolder.newFile("Broken.java").toPath();
        Path output = mTemporaryFolder.newFolder("classes").toPath();
        Files.writeString(source, "public class Broken {");

        assertThrows(IOException.class, () -> Creator.compile(List.of(source),
            List.of("--release", "25", "-d", output.toString())));
    }

    @Test
    public void archiveCannotWriteOutsideExtractionDirectory() throws Exception
    {
        Path directory = mTemporaryFolder.newFolder("archive").toPath();
        Path archive = directory.resolve("source.zip");
        Path escaped = directory.getParent().resolve("escaped.java");

        try(ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive)))
        {
            output.putNextEntry(new ZipEntry("../escaped.java"));
            output.write("class Escaped {}".getBytes());
            output.closeEntry();
        }

        assertThrows(IOException.class, () -> Creator.process(archive));
        assertFalse(Files.exists(escaped));
    }

    @Test
    public void validatesCreatedLibraryContents() throws Exception
    {
        Path content = mTemporaryFolder.newFolder("content").toPath();
        Path entryPoint = content.resolve("jmbe/JMBEAudioLibrary.class");
        Files.createDirectories(entryPoint.getParent());
        Files.write(entryPoint, new byte[]{0});
        Creator.createJarMetadata(content, "1.0.12");
        Path library = mTemporaryFolder.getRoot().toPath().resolve("jmbe-1.0.12.jar");
        Creator.createJar(content, library);

        Creator.verifyLibrary(library, Version.fromString("1.0.12"));
        assertTrue(Files.size(library) > 0);
    }
}
