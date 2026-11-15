# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.example.movieboxexplorer.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.movieboxexplorer.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
