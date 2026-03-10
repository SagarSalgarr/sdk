# Add project specific ProGuard rules for Simply Save Voice SDK bridge.
# Keep React Native and SDK public API.
-keep class com.nippon.simplysave.rn.** { *; }
-keep class com.nippon.simplysave.sdk.** { *; }

# Strip Log in release (no user data in logs)
-assumenosideeffects class android.util.Log {
    static *** d(...);
    static *** v(...);
    static *** i(...);
}
