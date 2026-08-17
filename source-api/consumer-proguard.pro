# The manga extension API, kept whole — the same policy as the anime file, for the same reason:
# every caller of this surface is an extension APK that R8 never sees, so member-level shrinking
# of it is deletion by luck. The anime side lost interface `$DefaultImpls` bodies and then a
# top-level `sourcePreferences(String)` to exactly this before the policy widened; the manga
# package has the same shapes in the same places.
#
# The root pattern is one level deep (`*`, not `**`) on purpose: app-side plumbing lives in
# subpackages of the same prefix — `eu.kanade.tachiyomi.source.anime` is this fork's own code, not
# API — and only the module's actual surface should be pinned. One star still matches `$`-nested
# names, so the interfaces' `$DefaultImpls` are covered. The generated `R` classes share the root
# because it is the module's namespace; no extension references them, so they are excluded.
-keep class !eu.kanade.tachiyomi.source.R,!eu.kanade.tachiyomi.source.R$*,eu.kanade.tachiyomi.source.* { public protected *; }
-keep class eu.kanade.tachiyomi.source.model.** { public protected *; }
-keep class eu.kanade.tachiyomi.source.online.** { public protected *; }

# Response.asJsoup and friends — the parsing helpers every scraping extension calls.
-keep,allowoptimization class eu.kanade.tachiyomi.util.JsoupExtensionsKt { public protected *; }
