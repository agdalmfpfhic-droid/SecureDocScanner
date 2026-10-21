# SQLCipher (code natif appelé par JNI)
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }

# Tink (utilisé par androidx.security-crypto)
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-keep class com.google.crypto.tink.proto.** { *; }

# Retire les logs en release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
