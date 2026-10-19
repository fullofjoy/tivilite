# Keep model classes
-keep class com.tivilite.tv.model.** { *; }

# Keep Media3 ExoPlayer
-keep class androidx.media3.** { *; }

# Keep Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
 <init>(...);
}
