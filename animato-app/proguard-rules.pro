# Keep rules for the whole program.
#
# Mihon's own rules arrive here as consumer rules from `:app`, and both `source-api` modules send
# theirs the same way — so the extension API, the extension-facing dependencies and everything under
# eu.kanade, tachiyomi and mihon are already covered. What is below is only what those cannot know
# about: the packages this fork added, and the native libraries the anime side brought with it.
#
# Note that Mihon's rules include `-dontobfuscate`. Nothing is renamed, so a class found by name at
# runtime keeps working; what R8 does here is remove code nothing reaches and optimise what is left.
# That is a smaller win than a fully obfuscated build and a far smaller risk, and it is the right
# trade while nobody has run an R8 build of this app on a device yet.
#
# `.github/check-dex-keeps.sh` fails the build if anything named here stops reaching the APK.

# Our own code, on the same terms Mihon keeps its own.
-keep,allowoptimization class animato.**
-keep,allowoptimization class aniyomi.**

# SQLDelight's generated queries and views for the anime database. Nothing writes these packages by
# hand, which is exactly why they are easy to forget.
-keep,allowoptimization class dataanime.**
-keep,allowoptimization class view.**

# Backup models. A serializer is reached through a generated Companion rather than by a call R8 can
# see, so the same treatment Mihon gives its own has to extend to ours — otherwise the first thing
# to break is a restore, which is the worst thing to have break.
-keep,includedescriptorclasses class animato.**$$serializer { *; }
-keepclassmembers class animato.** {
    *** Companion;
}
-keepclasseswithmembers class animato.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Native libraries call back into these by name, so nothing in the app statically references them
# and R8 has no reason to believe they are used.
-keep class is.xyz.mpv.** { *; }
-keep class com.arthenica.ffmpegkit.** { *; }
-keep class xyz.secozzi.torrserver.** { *; }

# What animato.di.MihonGraphBridge reads by reflection to answer Injekt out of Mihon's Metro graph:
# the generated graph's provider fields, with the generic signature that says what each provides,
# and the public constructors of Mihon's classes, for the ones it builds itself. Nothing else calls
# those constructors where R8 can see it — the anime side asks Injekt for a type, not a constructor.
#
# The signature is only half of it. R8 in full mode rewrites a kept signature by dropping every type
# argument whose class is not itself kept, so `Provider<AndroidPreferenceStore>` reached the APK as
# a bare `Provider` — and the bridge, not knowing what any provider built, found nothing, and the
# release build died on its first Injekt call. Debug builds are not minified and never showed it.
# Two things have to be kept for R8 to leave the type arguments in: Metro's `Provider` itself, whose
# own `<T>` R8 otherwise erases (after which any `Provider<X>` is malformed and loses its argument),
# and the classes named as arguments — Mihon's. Both by name only, not their members, and still
# shrinkable. check-dex-keeps.sh checks the APK for this, so it cannot come back quietly.
-keepattributes Signature,RuntimeVisibleAnnotations
-keepclassmembers class mihon.app.di.AppGraph$Impl {
    dev.zacsweers.metro.Provider *;
}
-keep,allowshrinking,allowobfuscation interface dev.zacsweers.metro.Provider
-keep,allowshrinking,allowobfuscation class eu.kanade.**, tachiyomi.**, mihon.**
-keepclassmembers class eu.kanade.**, tachiyomi.**, mihon.** {
    public <init>(...);
}
