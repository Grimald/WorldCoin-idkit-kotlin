# IDKit Kotlin ProGuard Rules
# These rules are applied when consumer apps minify their code

# ===== Public API =====
# Keep all classes and interfaces in the root package (IDKit.kt, Types.kt)
# This includes: IDKit, Session, Status, Proof, AppError, AppID, BridgeURL, etc.
-keep,allowobfuscation class com.worldcoin.idkit_kotlin.* { *; }
-keep,allowobfuscation interface com.worldcoin.idkit_kotlin.* { *; }

# Keep all nested classes (e.g., Status$Confirmed, Proof$Default)
-keep,allowobfuscation class com.worldcoin.idkit_kotlin.*$* { *; }

# Keep exception classes for better stack traces
-keep class com.worldcoin.idkit_kotlin.AppErrorThrowable { *; }

# ===== Kotlinx Serialization =====
-keepattributes *Annotation*, InnerClasses, Signature, Exception

# Don't warn about kotlinx.serialization
-dontwarn kotlinx.serialization.**
-dontnote kotlinx.serialization.**

# Keep all generated serializers
-keep,includedescriptorclasses class com.worldcoin.idkit_kotlin.**$$serializer { *; }

# Keep companion objects (often used by serializers)
-keepclassmembers class com.worldcoin.idkit_kotlin.** {
    *** Companion;
}

# Keep classes with serializer() method
-keepclasseswithmembers class com.worldcoin.idkit_kotlin.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep @Serializable annotated classes
-keepattributes RuntimeVisibleAnnotations
-keep @kotlinx.serialization.Serializable class com.worldcoin.idkit_kotlin.** { *; }

# ===== Kotlin Metadata =====
-keepattributes RuntimeVisibleAnnotations,
                RuntimeInvisibleAnnotations,
                RuntimeVisibleParameterAnnotations,
                RuntimeInvisibleParameterAnnotations

# Keep Kotlin metadata to allow reflection
-keep class kotlin.Metadata { *; }

# ===== Coroutines =====
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# ===== OkHttp 5.x =====
# OkHttp 5.x ships with its own ProGuard rules in META-INF/proguard/
# These rules are automatically applied by R8/ProGuard.
# The following rules are supplementary for edge cases.

# JSR 305 annotations are for embedding nullability information
-dontwarn javax.annotation.**

# A resource is loaded with a relative path so the package of this class must be preserved
-keeppackagenames okhttp3.internal.publicsuffix.*
-adaptresourcefilenames okhttp3/internal/publicsuffix/PublicSuffixDatabase.gz

# Animal Sniffer compileOnly dependency to ensure APIs are compatible with older versions of Java
-dontwarn org.codehaus.mojo.animal_sniffer.*

# OkHttp platform used only on JVM and when Conscrypt and other security providers are available
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Okio (OkHttp dependency)
-dontwarn okio.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
