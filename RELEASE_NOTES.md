# Optional 16 kHz JMBE 1.0.17

This is the deliberately selected 16 kHz variant of JMBE 1.0.17. Standard JMBE 1.0.17 remains the default release with normal 8 kHz output.

The optional build bundles the V4 model and automatically produces 16 kHz completed-call audio for IMBE and AMBE+2. It has no enhancement switch, model selector or continuous automatic gain. Processing uses additional CPU compared with the standard library.

Requires a VCE build that supports JMBE's supplied audio-output format to use the result for browser playback, recordings and uploads. Older hosts continue receiving the original 8 kHz frame audio; installing this JAR alone does not enable 16 kHz output on an older host. Calls longer than 120 seconds retain native audio.

Choose this release explicitly and install its JAR through the normal JMBE library selection. The optional Creator builds this exact release. Source and dataset attribution are included in MODEL_NOTICES.md in the source archive and library JAR.
