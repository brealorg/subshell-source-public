# R8_RELEASE_SHRINKS_WITHOUT_RENAMING_V1 (owner 2026-10-16)
# R8 shrinks and optimises release builds but keeps class and method names, so
# crash reports (CRASH01: exception classes and code locations) stay readable
# without a mapping file per release, and nothing looked up by name breaks.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
