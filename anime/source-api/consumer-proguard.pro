-keep class eu.kanade.tachiyomi.source.model.** { public protected *; }
-keep class eu.kanade.tachiyomi.source.online.** { public protected *; }
-keep class eu.kanade.tachiyomi.source.** extends eu.kanade.tachiyomi.source.MangaSource { public protected *; }

-keep class eu.kanade.tachiyomi.animesource.model.** { public protected *; }
-keep class eu.kanade.tachiyomi.animesource.online.** { public protected *; }
-keep class eu.kanade.tachiyomi.animesource.** extends eu.kanade.tachiyomi.animesource.AnimeSource { public protected *; }

-keep,allowoptimization class eu.kanade.tachiyomi.util.JsoupExtensionsKt { public protected *; }

# The default bodies of the extension API's own interfaces.
#
# Kotlin puts a default interface method's body in a nested `$DefaultImpls` class, and a class that
# implements the interface without overriding the method delegates to it. Extensions are compiled
# against this API and are full of exactly that: getHosterList, getVideoList, getSeasonList and
# getSourcePreferences all have defaults an extension is free not to implement.
#
# R8 removed every one of them. It could see no caller, because every caller lives in an APK that is
# not part of this build and is loaded hours later. The rules above did not save them: they keep
# classes that *extend* AnimeSource, and `$DefaultImpls` extends nothing.
#
# Nothing in the app's own dex references these, which is why the missing-class check cannot see it
# either — that check reads what this APK refers to, and this is a thing other APKs refer to.
-keep class eu.kanade.tachiyomi.animesource.**$DefaultImpls { public protected *; }
-keep interface eu.kanade.tachiyomi.animesource.** { public protected *; }
