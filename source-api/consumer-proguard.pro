-keep class eu.kanade.tachiyomi.source.model.** { public protected *; }
-keep class eu.kanade.tachiyomi.source.online.** { public protected *; }
-keep class eu.kanade.tachiyomi.source.** extends eu.kanade.tachiyomi.source.Source { public protected *; }

-keep,allowoptimization class eu.kanade.tachiyomi.util.JsoupExtensionsKt { public protected *; }

# The same hole on the manga side, kept shut for the same reason. See the anime file for the whole
# story; the short version is that a Kotlin interface's default method bodies live in a nested
# `$DefaultImpls` class, every caller of them is in an extension APK, and R8 cannot see those.
-keep class eu.kanade.tachiyomi.source.**$DefaultImpls { public protected *; }
-keep interface eu.kanade.tachiyomi.source.** { public protected *; }
