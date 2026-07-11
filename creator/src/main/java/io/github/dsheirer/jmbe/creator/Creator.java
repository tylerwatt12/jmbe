/*
 * ******************************************************************************
 * Copyright (C) 2015-2020 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 * *****************************************************************************
 */

package io.github.dsheirer.jmbe.creator;

import io.github.dsheirer.jmbe.creator.github.GitHub;
import io.github.dsheirer.jmbe.creator.github.Release;
import io.github.dsheirer.jmbe.creator.github.Version;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Creator utility that downloads the JMBE source code, compiles it, and generates the JMBE library jar.
 */
public class Creator
{
    private static final Logger mLog = LoggerFactory.getLogger(Creator.class);

    private static final String GITHUB_JMBE_RELEASES_URL = "https://api.github.com/repos/tylerwatt12/jmbe/releases";
    private static final String JAVA_RELEASE = "25";

    /**
     * Exit code to indicate that the process completed successfully
     */
    public static final int EXIT_CODE_SUCCESS = 0;

    /**
     * Exit code to indicate an unknown error
     */
    public static final int EXIT_CODE_UNKNOWN_ERROR = 1;
    /**
     * Exit code to indicate an error when reading or writing files to the local storage system
     */
    public static final int EXIT_CODE_IO_ERROR = 2;

    /**
     * Exit code to indicate an error when attempting to download network resources
     */
    public static final int EXIT_CODE_NETWORK_ERROR = 3;

    /**
     * Exit code to indicate that the library path argument is required
     */
    public static final int EXIT_CODE_LIBRARY_PATH_REQUIRED = 4;

    /**
     * Creates an instance
     */
    public Creator()
    {
    }

    /**
     * Process a downloaded source code file
     *
     * @param downloadFile containing a GitHub JMBE release artifact
     * @throws IOException if there is an error
     */
    public static void process(Path downloadFile) throws IOException
    {
        Path downloadDirectory = downloadFile.toAbsolutePath().normalize().getParent();
        List<Path> toCompile = new ArrayList<>();

        System.out.println("Unzipping: Source Code");
        try(ZipFile zf = new ZipFile(downloadFile.toFile()))
        {
            var zipEntries = zf.entries();

            while(zipEntries.hasMoreElements())
            {
                ZipEntry entry = zipEntries.nextElement();
                Path target = downloadDirectory.resolve(entry.getName()).normalize();

                if(!target.startsWith(downloadDirectory))
                {
                    throw new IOException("Source archive contains an unsafe path: " + entry.getName());
                }

                if(entry.isDirectory())
                {
                    Files.createDirectories(target);
                }
                else
                {
                    Files.createDirectories(target.getParent());

                    try(InputStream inputStream = zf.getInputStream(entry))
                    {
                        Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
                    }

                    if(isCompilable(entry))
                    {
                        toCompile.add(target);
                    }
                }
            }
        }

        if(toCompile.isEmpty())
        {
            throw new IOException("Source archive did not contain JMBE codec sources");
        }

        compile(toCompile, getOptions(downloadDirectory));
        System.out.println("Deleting: Compiled Interfaces");
        deleteInterfaceClasses(getOutputDirectory(downloadDirectory));
    }

    /**
     * Creates JAR metadata directory and manifest file
     *
     * @param outputDirectory for writing files
     * @param version string for the library
     * @throws IOException if there is an error
     */
    public static void createJarMetadata(Path outputDirectory, String version) throws IOException
    {
        Path metaDirectory = outputDirectory.resolve("META-INF");
        if(!Files.exists(metaDirectory))
        {
            Files.createDirectories(metaDirectory);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Manifest-Version: 1.0\r\n");
        sb.append("Implementation-Title: jmbe\r\n");
        sb.append("Version: ").append(version).append("\r\n");
        sb.append("Site: https://github.com/tylerwatt12/jmbe\r\n");

        Path manifest = metaDirectory.resolve("MANIFEST.MF");
        Files.writeString(manifest, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * Discovers the LICENSE file from the source code tree and copies it to the output directory
     *
     * @param downloadDirectory where source code exists
     * @throws IOException if there is an error
     */
    public static void copyLicenseFile(Path downloadDirectory) throws IOException
    {
        String fileName = "LICENSE";
        Path license = getFile(downloadDirectory, fileName);

        if(license != null)
        {
            Path toLicense = getOutputDirectory(downloadDirectory).resolve(fileName);
            Files.copy(license, toLicense, StandardCopyOption.REPLACE_EXISTING);
            return;
        }

        throw new IOException("JMBE source archive is missing its LICENSE file");
    }

    /**
     * Recursively finds the specified filename in the specified directory
     *
     * @param downloadDirectory to search
     * @param fileName to discover
     * @return discovered file path or null
     */
    public static Path getFile(Path downloadDirectory, String fileName)
    {
        try(Stream<Path> paths = Files.walk(downloadDirectory))
        {
            return paths.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().equals(fileName))
                .findFirst().orElse(null);
        }
        catch(IOException ioe)
        {
            System.out.println("Error while searching for [" + fileName + "]");
            return null;
        }
    }

    /**
     * Output directory for storing compiled classes and JAR artifacts
     *
     * @param downloadDirectory where source code was downloaded
     * @return output directory
     */
    public static Path getOutputDirectory(Path downloadDirectory)
    {
        return downloadDirectory.resolve("output");
    }

    /**
     * Creates compiler options for classpath and output directory
     *
     * @param downloadDirectory where source code is located
     * @return options
     */
    public static List<String> getOptions(Path downloadDirectory)
    {
        Path output = getOutputDirectory(downloadDirectory);
        String classpath = getLibraryClassPath();
        List<String> options = new ArrayList<>();
        options.add("--release");
        options.add(JAVA_RELEASE);
        options.add("-cp");
        options.add(classpath);
        options.add("-d");
        options.add(output.toString());
        return options;
    }

    /**
     * Deletes the compiled interface classes from the output directory
     *
     * @param output
     * @throws IOException
     */
    public static void deleteInterfaceClasses(Path output) throws IOException
    {
        Path iface = output.resolve("jmbe").resolve("iface");

        if(Files.exists(iface) && Files.isDirectory(iface))
        {
            FileUtils.deleteDirectory(iface.toFile());
        }
    }

    /**
     * Indicates if the zip entry is a compilable file for the codec or interface java classes
     *
     * @param zipEntry to inspect
     * @return true if the file is part of the interfaces or codec package and is a java file.
     */
    public static boolean isCompilable(ZipEntry zipEntry)
    {
        String name = zipEntry.getName().replace('\\', '/');
        return name.endsWith(".java") &&
            (name.contains("/api/src/main/java/") || name.contains("/codec/src/main/java/"));
    }

    /**
     * Creates a command line classpath of the dependent jar libraries
     *
     * @return concatenated string suitable for -d command line option
     */
    public static String getLibraryClassPath()
    {
        URL currentURL = Creator.class.getProtectionDomain().getCodeSource().getLocation();
        System.out.println("Current URL:" + currentURL.toString());
        Path currentPath = null;

        try
        {
            currentPath = new File(currentURL.toURI()).toPath();
        }
        catch(Exception e)
        {
            mLog.error("Error discovering current execution path to lookup compile dependencies", e);
            currentPath = null;
        }

        if(currentPath != null && Files.exists(currentPath))
        {
            System.out.println("Discovering: Current Location [" + currentPath.toString() + "]");
            Path parent = currentPath.getParent();
            System.out.println("Discovering: Compile Dependencies [" + parent.toString() + "]");
            StringJoiner joiner = new StringJoiner(String.valueOf(File.pathSeparatorChar));

            try(DirectoryStream<Path> stream = Files.newDirectoryStream(parent))
            {
                stream.forEach(path -> {
                    if(!Files.isDirectory(path) && path.toString().endsWith("jar"))
                    {
                        joiner.add(path.toString());
                    }
                });
            }
            catch(IOException ioe)
            {
                throw new IllegalStateException("Unable to create the compiler classpath", ioe);
            }

            return joiner.toString();
        }

        return "";
    }

    /**
     * Compiles the list of java source code files using the specified compile time options
     *
     * @param paths of source code files
     * @param options for compilation (classpath & output directory)
     */
    public static void compile(List<Path> paths, List<String> options) throws IOException
    {
        System.out.println("Compiling: Source Code");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        if(compiler == null)
        {
            throw new IOException("The creator runtime does not contain the Java compiler");
        }

        int outputOption = options.indexOf("-d");

        if(outputOption < 0 || outputOption + 1 >= options.size())
        {
            throw new IOException("Compiler output directory is not configured");
        }

        Files.createDirectories(Path.of(options.get(outputOption + 1)));

        try(StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null))
        {
            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromPaths(paths);
            Boolean success = compiler.getTask(null, fileManager, null, options, null, compilationUnits).call();

            if(!Boolean.TRUE.equals(success))
            {
                throw new IOException("JMBE source compilation failed");
            }
        }
    }

    /**
     * Creates a jar from the specified source directory, storing the jar at the specified output file name
     *
     * @param source directory containing compiled classes and other jar artifacts
     * @param output library file path and name
     * @throws IOException if there is an error
     */
    public static void createJar(Path source, Path output) throws IOException
    {
        Path normalizedOutput = output.toAbsolutePath().normalize();

        if(normalizedOutput.getParent() != null)
        {
            Files.createDirectories(normalizedOutput.getParent());
        }

        try(JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(normalizedOutput));
            Stream<Path> paths = Files.walk(source))
        {
            for(Path path: paths.filter(Files::isRegularFile).sorted(Comparator.naturalOrder()).toList())
            {
                String entryName = source.relativize(path).toString().replace(File.separatorChar, '/');
                jarOutputStream.putNextEntry(new JarEntry(entryName));
                Files.copy(path, jarOutputStream);
                jarOutputStream.closeEntry();
            }
        }
    }

    /**
     * Creates a jar name for the specified release version
     *
     * @param version of the GitHub JMBE release
     * @return JMBE library jar name
     */
    public static String getJarName(String version)
    {
        if(version.startsWith("v"))
        {
            version = version.substring(1);
        }

        return "jmbe-" + version + ".jar";
    }

    /**
     * Creates a JMBE library from one exact release, or the latest release when no tag is supplied.
     */
    public static int createLibrary(String libraryPath, String releaseTag)
    {
        Path temporaryDirectory = null;

        try
        {
            System.out.println("Starting: JMBE Library Creator");
            temporaryDirectory = Files.createTempDirectory("jmbe-creator");
            System.out.println("Created: Temporary Directory [" + temporaryDirectory.toString() + "]");
            Release release = getRelease(releaseTag);

            if(release == null)
            {
                System.out.println("Failed: Unable to determine the requested JMBE release from GitHub.");
                return EXIT_CODE_NETWORK_ERROR;
            }

            Path library = libraryPath == null ?
                Path.of(System.getProperty("user.dir")).toAbsolutePath().getParent()
                    .resolve(getJarName(release.getVersion().toString())) :
                Path.of(libraryPath).toAbsolutePath().normalize();
            System.out.println((libraryPath == null ? "Generated" : "Specified") + ": Library Path [" + library + "]");

            System.out.println("Downloading: Source Code [Version " + release.getVersion() + "]");
            Path download = GitHub.downloadReleaseSourceCode(release, temporaryDirectory);

            if(download == null)
            {
                System.out.println("Failed: Couldn't download source code from GitHub. Exiting.");
                return EXIT_CODE_NETWORK_ERROR;
            }

            process(download);
            System.out.println("Creating: JAR Metadata");
            createJarMetadata(getOutputDirectory(temporaryDirectory), release.getVersion().toString());
            System.out.println("Creating: JAR License File");
            copyLicenseFile(temporaryDirectory);
            System.out.println("Creating: JMBE Library [" + library + "]");
            createJar(getOutputDirectory(temporaryDirectory), library);
            verifyLibrary(library, release.getVersion());

            System.out.println("----------------------------------------------------------------------");
            System.out.println("Success: JMBE Library Created At: " + library);
            System.out.println("----------------------------------------------------------------------\n");
            return EXIT_CODE_SUCCESS;
        }
        catch(IOException ioe)
        {
            System.out.println("Failed: Unknown I/O Error " + ioe.getLocalizedMessage());
            ioe.printStackTrace();
            return EXIT_CODE_IO_ERROR;
        }
        catch(Exception e)
        {
            System.out.println("Failed: Unknown (General) Error " + e.getLocalizedMessage());
            e.printStackTrace();
            return EXIT_CODE_UNKNOWN_ERROR;
        }
        finally
        {
            if(temporaryDirectory != null)
            {
                try
                {
                    FileUtils.deleteDirectory(temporaryDirectory.toFile());
                }
                catch(IOException e)
                {
                    System.out.println("Delete: Temporary Directory Failed [" + temporaryDirectory + "]");
                }
            }
        }
    }

    public static int createLibrary(String libraryPath)
    {
        return createLibrary(libraryPath, null);
    }

    private static Release getRelease(String releaseTag)
    {
        if(releaseTag == null)
        {
            return GitHub.getLatestRelease(GITHUB_JMBE_RELEASES_URL);
        }

        Version version = Version.fromString(releaseTag);

        if(version == null)
        {
            throw new IllegalArgumentException("Invalid JMBE release tag: " + releaseTag);
        }

        return GitHub.getRelease(GITHUB_JMBE_RELEASES_URL, "v" + version);
    }

    static void verifyLibrary(Path library, Version expectedVersion) throws IOException
    {
        if(!Files.isRegularFile(library) || Files.size(library) == 0)
        {
            throw new IOException("JMBE library was not created");
        }

        try(JarFile jarFile = new JarFile(library.toFile()))
        {
            if(jarFile.getManifest() == null)
            {
                throw new IOException("Created JMBE library is missing its manifest");
            }

            String actualVersion = jarFile.getManifest().getMainAttributes().getValue("Version");

            if(!expectedVersion.toString().equals(actualVersion))
            {
                throw new IOException("Created JMBE version " + actualVersion + " does not match requested version " +
                    expectedVersion);
            }

            JarEntry libraryClass = jarFile.getJarEntry("jmbe/JMBEAudioLibrary.class");

            if(libraryClass == null)
            {
                throw new IOException("Created JMBE library is missing its entry point");
            }
        }
    }

    public static void main(String[] args)
    {
        int status;

        if(args.length == 0)
        {
            status = createLibrary(null);
        }
        else if(args.length == 1)
        {
            status = createLibrary(args[0]);
        }
        else if(args.length == 2)
        {
            status = createLibrary(args[0], args[1]);
        }
        else
        {
            status = EXIT_CODE_LIBRARY_PATH_REQUIRED;
            System.out.println("Usage: Creator [optional full library path] [optional exact release tag]");
        }

        System.exit(status);
    }
}
