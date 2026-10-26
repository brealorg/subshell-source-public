# R8_RELEASE_SHRINKS_WITHOUT_RENAMING_V1 (owner 2026-10-16)
# R8 shrinks and optimises release builds but keeps class and method names, so
# crash reports (CRASH01: exception classes and code locations) stay readable
# without a mapping file per release, and nothing looked up by name breaks.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable

# SECAUDIT15_RELEASE_BUILDS_WRITE_NO_LOGCAT_V1 (security audit #15): the app's
# logcat lines carry account stable IDs and post/comment/inbox IDs (no tokens
# or names), readable over adb or by a bug report. Release builds drop every
# android.util.Log call (a call whose result is used reads 0); crashes and
# failures are kept by CRASH01 and the diagnostics log, which never hold such
# IDs. Debug builds still log.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int) return false;
    public static int v(...) return 0;
    public static int d(...) return 0;
    public static int i(...) return 0;
    public static int w(...) return 0;
    public static int e(...) return 0;
    public static int wtf(...) return 0;
    public static int println(...) return 0;
}
