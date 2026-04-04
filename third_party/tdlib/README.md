# TDLib drop location

Harbor consumes prebuilt TDLib Android output from this directory.

Expected contents:
- `java/` with generated Java sources from the official TDLib Android build
- `libs/` with ABI-specific `libtdjni.so` files from the same build

Build TDLib using the official upstream instructions:
- Java/JNI overview: https://github.com/tdlib/td#using-in-java-projects
- Android build flow: https://raw.githubusercontent.com/tdlib/td/master/example/android/README.md

After the upstream Android build finishes, copy `tdlib/java` into
`third_party/tdlib/java` and `tdlib/libs` into `third_party/tdlib/libs`.

These generated artifacts stay out of git. `briar-android` compiles the TDLib
Java sources and packages the JNI libraries when those directories are present.
