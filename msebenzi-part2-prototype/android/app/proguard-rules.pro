# Only used if isMinifyEnabled is switched on in build.gradle.kts.
# Gson maps JSON <-> fields by name, so API models and Room entities must keep their names.
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-keep class com.msebenzi.app.data.** { *; }
-keepclassmembers class * { @com.google.gson.annotations.SerializedName <fields>; }
