# MoChat ProGuard / R8 rules
# Base rules (apply to both easy and hard). hard flavor enables R8 full.

# Keep the application class and entry points.
-keep class com.mochat.app.MoChatApp { *; }
-keep class com.mochat.app.MainActivity { *; }

# Keep Manifest-declared components (Activities/Services/Providers/Receivers)
# otherwise export-by-name invocation from drozer/attacker app would break.
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.content.BroadcastReceiver

# Keep the JNI bridge and all native methods.
-keep class com.mochat.app.nbridge.NativeBridge { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep @JavascriptInterface annotated methods (chain #3, #7).
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep Parcelable CREATOR fields (chain #11 parcel mismatch).
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep service interfaces (the anti-analysis api layer) so reflection binding
# still resolves after minification.
-keep interface com.mochat.app.api.** { *; }
-keep class com.mochat.app.api.** { *; }

# Keep impl classes — they are loaded by Class.forName() with an Obf-encrypted
# name from ServiceRegistry, so R8 cannot see the static reference and would
# otherwise remove them. The class NAMES must be preserved for the lookup to
# resolve; method internals may still be optimized.
-keep class com.mochat.app.impl.** { *; }

# Keep ServiceLocator / ServiceRegistry / Obf (the reflection entry points).
-keep class com.mochat.app.core.ServiceLocator { *; }
-keep class com.mochat.app.core.ServiceRegistry { *; }
-keep class com.mochat.app.core.ServiceProxy { *; }
-keep class com.mochat.app.core.Obf { *; }
-keep class com.mochat.app.core.Reflector { *; }

# Suppress warnings, not errors.
-dontwarn android.webkit.**
-dontwarn org.apache.http.**
