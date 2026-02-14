# 1. Proteger modelos de datos que se comunican con la API
-keep class com.pr4y.app.data.remote.** { *; }
-keepclassmembers class com.pr4y.app.data.remote.** { *; }

# 2. Mantener la lógica de Room
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.Entity
-keep class * extends androidx.room.Dao

# 3. SEGURIDAD CRÍTICA: Proteger lógica de Criptografía
-keep class com.pr4y.app.crypto.LocalCrypto { *; }
-keep class com.pr4y.app.crypto.DekManager { *; }
-keep class com.pr4y.app.data.local.JournalDraftStore { *; }

# 4. Soporte para Coroutines y OkHttp
-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# 5. Evitar errores de clases faltantes (Tink / Security Crypto / ErrorProne)
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**

# 6. Compose y Material3
-keep class androidx.compose.** { *; }
