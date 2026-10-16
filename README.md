Copyright (C) 2015-2026 Dennis Sheirer

# jmbe - Java Multi-Band Excitation library

## Optional 16 kHz branch

The standard public default remains **JMBE 1.0.17**, with 8 kHz audio. This branch builds the deliberately selected
**1.0.17-16khz.1** variant. Its bundled V4 model processes completed IMBE and AMBE+2 calls automatically; there are
no enhancement settings, model selector or continuous automatic gain control. A fixed attenuation is applied only
when an enhanced completed call would exceed a 0.95 peak. There is no gain boost or gain movement within a call.

Use this optional build with a host that supports the generic completed-call output provider below. Old hosts remain
safe: the existing `getAudio` and `getAudioWithMetadata` frame methods still return 160 samples at **8 kHz** and never
run the model. A new host must use the returned sample rate for playback, recordings and uploads. Returning 16 kHz
samples through an old 8 kHz frame method would change duration and pitch, so this branch does not do that.

This branch's release artifacts are named `jmbe-1.0.17-16khz.1.jar` and corresponding optional Creators. The legacy
numeric interface and manifest `Version` stay **1.0.17**; `Implementation-Version` identifies the optional flavor.
Optional releases are prereleases with `latest=false`, leaving the standard release as the default. Download the
optional release deliberately. Its Creator is pinned to that exact optional source tag; the standard Creator is
unchanged. Library and API bytecode target Java 17, while the Creator runs on bundled Java 25.

### Completed-call audio contract

On a completed-call worker, optionally discover `JMBEAudioLibrary.getAudioOutputProcessor()` by reflection. If absent,
use the original 8 kHz samples. Its provider has:

```java
public Map<String,Object> process(float[] samples, int sampleRate, String codec)
// result: "samples" -> float[], "sample_rate" -> Integer
```

Pass the complete native call, `8000`, and `"IMBE"` or `"AMBE_PLUS_2"`. Successful model processing returns exactly
2 × the samples at `16000`, preserving duration. Other codecs (`"NONE"` for analog), other input rates, empty calls
and calls longer than 120 seconds pass through with their original rate. The provider clones passed-through audio,
does not modify its input and rejects inputs exceeding 16,777,216 samples or containing non-finite/out-of-bound PCM.
If processing fails, the host should retain the original samples and rate. Never invoke this whole-call model on a
real-time decoder callback. Do not infer the output rate from a version number, artifact name or sample count.

The V4 bundle is data-only, loaded lazily, with validated shapes, bounds, constants and payload hashes. It is included
inside the JAR; no external model path, model-management interface, native runtime or Python is required by the host.
To update weights, use `tools/export_audio_enhancement_model.py` with trusted checkpoints for the fixed architecture,
replace `codec/src/main/resources/jmbe/audio/enhance/voice-model.zip`, update the expected model hash test, and release
a new optional flavor. The exporter requires Python/PyTorch only at build time. Evaluate replacement weights before
release; changing weights does not change the model architecture or promise improved speech intelligibility.

## Fork Notes

This fork is maintained for use with [sdrtrunk-vce](https://github.com/tylerwatt12/sdrtrunk) and
focuses on practical codec maintenance:

* **Lint and code quality** — visibility narrowing, dead code removal, encapsulation of internal
  implementation details, and general cleanup throughout the AMBE/IMBE codec paths
* **Removed unused code** — debug wave-generation utilities, the unused `ambeplus` package, and
  internal tables and classes not reachable from the sdrtrunk API. The old code is still in the repo
  history, just not in the production version.
* **Build compatibility** — updated for current Gradle and JDK toolchains; the library targets Java 17 and the creator uses Java 25
* **Reduced allocation pressure** — instance-field reuse for hot-path buffers (noise samples, phase
  arrays, DFT bin scalars); eliminated per-frame array allocations in spectral amplitude interpolation,
  enum lookups, and overlap-add synthesis
* **Tuning** - AMBE frames are no longer decoded twice
* **Bug fixes** — fixed off-by-one in voiced band counting that inflated phase noise injection
* **Codec correctness fixes** — unvoiced band scaling now guards zero-energy FFT bands to prevent NaN
  audio output, and voiced phase handling uses consistent normalization to keep overlap-add synthesis
  stable across frame transitions
* **Output calibration** — final AMBE output is trimmed slightly before clipping so routine high-energy
  frames stay below the hard limiter more often without changing gain decoding or spectral amplitude math
* **Voiced synthesis performance** — replaced per-sample `Math.cos()` calls with incremental phasor
  rotation, reducing transcendental function calls from O(samples × harmonics) to O(harmonics) per
  frame. At 50 voice frames/sec, 160 samples/frame, and up to 56 harmonics, this moves the hot path
  from ~448,000 `Math.cos()` calls/sec to ~22,000 setup calls/sec, with the inner loop handled by
  multiply/add rotation. Audio quality is preserved or improved under A/B testing.

In short, this fork has been radically pruned to just what sdrtrunk requires and tuned to be faster
than the original. Vectorization of the synthesis loop is difficult given the data-dependent branching
on voicing decisions, but eliminating trigonometry from the inner loop and replacing it with fused
multiply-add phasor rotation is a significant win on any hardware.

The original patent notice and upstream usage notes remain below.

Audio conversion library for decoding MBE encoded audio frames.
  
Decodes IMBE 144-bit and AMBE 72-bit encoded 20 millisecond audio frames to 8 kHz 16-bit mono PCM encoded audio.

**PATENT NOTICE**

This source code is provided for educational purposes only.  It is a written
description of how certain voice encoding/decoding algorithms could be
implemented.  Executable objects compiled or derived from this package may be
covered by one or more patents.  Readers are strongly advised to check for any
patent restrictions or licensing requirements before compiling or using this
source code.

Note: this patent notice is verbatim from the mbelib library README at (https://github.com/szechyjs/mbelib)

# End Users: Creating the JMBE Library (Version 1.0.7+)
***YOU DO NOT HAVE TO INSTALL THE JAVA JDK.*** The instructions for creating the JMBE library have changed starting with version 1.0.7.

1. Download the latest **JMBE Creator** for your operating system from the [Releases](https://github.com/tylerwatt12/jmbe/releases) page.
2. Unzip the JMBE Creator
3. Open a command/console window and run the JMBE Creator application
  * **Windows:**  (unzip directory)/bin/creator.bat
  * **Linux/OSX:** (unzip directory)/bin/creator
4. When the program finishes, it will display the location of your JMBE library.
5. Move the library to a permanent location

Note: for **sdrtrunk** use the menu item **View > Preferences** and then use the **JMBE Audio Library** section to tell sdrtrunk where your compiled JMBE library is located. 
	
# Software Developers - Using the JMBE audio conversion library in your own java program

* Follow the same instructions for downloading the source code above.  Use the following command to build the API:

WINDOWS:
> gradlew.bat api

LINUX:
> ./gradlew api

* Add the API library jar to your project.

* Add the following code to your program
	
		IAudioCodecLibrary audioCodecLibrary = null;
		
		try
		{
                    URLClassLoader childClassLoader = new URLClassLoader(new URL[]{path.toUri().toURL()},
                        this.getClass().getClassLoader());

                    Class classToLoad = Class.forName("jmbe.JMBEAudioLibrary", true, childClassLoader);

                    Object instance = classToLoad.getDeclaredConstructor().newInstance();

                    if(instance instanceof IAudioCodecLibrary)
                    {
                        audioCodecLibrary = (IAudioCodecLibrary)instance;
    		    } 
		catch (Exception e)
		{
		    //error handling
		}
	
* To convert 18-byte IMBE audio frames:

		IAudioCodec audioCodec = library.getAudioConverter("IMBE");
		float[] convertedAudio = audioCodec.getAudio(byte[] imbeFrameData);

* To convert 9-byte AMBE audio and tone frames:

		IAudioCodec audioCodec = library.getAudioConverter("AMBE");
		float[] convertedAudio = audioCodec.getAudio(byte[] ambeFrameData);

* To convert 9-byte AMBE audio frames and tone frames along with tone frame metadata:

		IAudioCodec audioCodec = library.getAudioConverter("AMBE");
		IAudioWithMetadata convertedAudio = audioCodec.getAudioWithMetadata(byte[] ambeFrameData);

# Creating Legacy JMBE Library (Versions prior to 1.0.7)
# Preparing to Compile the Library From Source Code

* Install the Java 8 (or higher) Java Development Kit (JDK). Note: this is different from the Java Runtime
Environment (JRE) that most users have installed on their computers.
	
  * **Liberica OpenJDK: (https://bell-sw.com/)**
  * **Oracle: (http://www.oracle.com/technetwork/java/javase/downloads/index.html)**

* Download the source code branch from GitHub:

  * **Version 1.0.6 (current): (https://github.com/DSheirer/jmbe/archive/v1.0.6.zip)**
  * **Version 0.3.4 (previous): (https://github.com/DSheirer/jmbe/archive/v0.3.4.zip)**

# WINDOWS: Compiling the Library from Source Code

* Setup the JAVA_HOME and PATH environment variables

  * (https://www.theserverside.com/tutorial/How-to-install-the-JDK-on-Windows-and-setup-JAVA_HOME)

* Verify that JAVA_HOME points to the Java Development Kit (JDK) version 8 or higher.  At a command prompt type:

  * **> echo %JAVA_HOME%**

This should respond with the directory where you have installed the JDK.

* Verify that your computer is using the correct version of the Java compiler.  At a command prompt type:

  * **> javac -version**

This should respond with the java version.

* Unzip the source code file with a tool like 7-Zip or using the Windows File Manager (right-click on file)

* Using the command prompt, change to the directory where you downloaded and unzipped the source code (jmbe-master.zip in this example):

  * **> cd C:\Users\Denny\Downloads\jmbe-1.0.6**

* Run the build script

  * **> gradlew.bat build**

* The build script will compile the source code and create the library.  The first time that you run the build script,
it may download some additional files needed for installing the gradle build tool and some java libraries needed for
compiling the jmbe library code.

* The compiled JMBE library will be located in a sub-folder named '\codec\build\libs', for example:

  * **> C:\Users\Denny\Downloads\jmbe-master\codec\build\libs\jmbe-1.0.6.jar**

* Follow the instructions for the application that will use the JMBE library.

Note: for **sdrtrunk** use the menu item **View > Preferences** and then use the **JMBE Audio Library** section to tell sdrtrunk where your compiled JMBE library is located. 

# LINUX: Compiling the Library from Source Code

* Setup the JAVA_HOME and PATH environment variables

(https://askubuntu.com/questions/175514/how-to-set-java-home-for-java)

* Verify that JAVA_HOME points to the Java Development Kit (JDK) version 8 or higher.  Open a terminal and type:

  * **> echo $JAVA_HOME$**

This should respond with the directory where you have installed the JDK.

* Verify that your computer is using the correct version of the Java compiler.  At a command prompt type:

  * **> javac -version**

This should respond with the java version.

* Unzip the source code file with a tool like 7-Zip or ark using the File Manager (right-click on file)

* In the terminal window, change to the directory where you downloaded and unzipped the source code (jmbe-master.zip in this example):

  * **> denny@denny-desktop:~$ cd Downloads\jmbe-1.0.6**

* Run the build script

  * **> ./gradlew build**

* The build script will compile the source code and create the library.  The first time that you run the build script,
it may download some additional files needed for installing the gradle build tool and some java libraries needed for
compiling the jmbe library code.

* The compiled JMBE library will be located at:

  * **> ~\Downloads\jmbe-master\codec\build\libs\jmbe-1.0.6.jar**

* Follow the instructions for the application that will use the JMBE library.  
