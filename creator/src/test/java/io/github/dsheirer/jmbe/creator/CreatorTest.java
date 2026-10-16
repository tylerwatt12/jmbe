package io.github.dsheirer.jmbe.creator;

import static org.junit.Assert.assertEquals;
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
    public void optionalLibraryKeepsLegacyVersionAndRequiresItsModel() throws Exception
    {
        Path content = mTemporaryFolder.newFolder("optional").toPath();
        Path entryPoint = content.resolve("jmbe/JMBEAudioLibrary.class");
        Path processor = content.resolve("jmbe/audio/AudioOutputProcessor.class");
        Path model = content.resolve("jmbe/audio/enhance/voice-model.zip");
        Files.createDirectories(model.getParent());
        Files.write(entryPoint, new byte[]{0});
        Files.write(processor, new byte[]{0});
        Creator.createJarMetadata(content, "1.0.17-16khz.1");
        Path library = mTemporaryFolder.getRoot().toPath().resolve("jmbe-1.0.17-16khz.1.jar");
        Creator.createJar(content, library);
        assertThrows(IOException.class, () -> Creator.verifyLibrary(library, Version.fromString("1.0.17-16khz.1")));
        Files.write(model, new byte[]{1});
        Creator.createJar(content, library);
        Creator.verifyLibrary(library, Version.fromString("1.0.17-16khz.1"));
        try(var jar = new java.util.jar.JarFile(library.toFile()))
        {
            assertEquals("1.0.17", jar.getManifest().getMainAttributes().getValue("Version"));
            assertEquals("1.0.17-16khz.1", jar.getManifest().getMainAttributes().getValue("Implementation-Version"));
        }
    }

    @Test
    public void processPackagesCodecResourcesAndStillRemovesSharedInterfaces() throws Exception
    {
        Path directory = mTemporaryFolder.newFolder("resources").toPath();
        Path archive = directory.resolve("source.zip");
        byte[] model = {2, 4, 6};
        try(ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive)))
        {
            zip.putNextEntry(new ZipEntry("jmbe/codec/src/main/java/jmbe/Minimal.java"));
            zip.write("package jmbe; public class Minimal {}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("jmbe/api/src/main/java/jmbe/iface/Shared.java"));
            zip.write("package jmbe.iface; public interface Shared {}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("jmbe/codec/src/main/resources/jmbe/audio/enhance/voice-model.zip"));
            zip.write(model);
            zip.closeEntry();
        }
        Creator.process(archive);
        Path output = Creator.getOutputDirectory(directory);
        org.junit.Assert.assertArrayEquals(model, Files.readAllBytes(output.resolve("jmbe/audio/enhance/voice-model.zip")));
        assertTrue(Files.exists(output.resolve("jmbe/Minimal.class")));
        assertFalse(Files.exists(output.resolve("jmbe/iface/Shared.class")));
        byte[] bytecode = Files.readAllBytes(output.resolve("jmbe/Minimal.class"));
        assertEquals(61, ((bytecode[6] & 255) << 8) | (bytecode[7] & 255));
    }

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
