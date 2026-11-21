# yt-dlp / ffmpeg wrapper library and its JSON mapper
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-keep class org.apache.commons.io.** { *; }
-dontwarn org.apache.commons.io.**

# our own data layer (Room entities, engine models)
-keep class com.lonerxone.siphon.data.** { *; }

-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontobfuscate
-ignorewarnings