# The extension API, kept whole.
#
# Every rule in this file exists for one reason: the callers of this module's public surface are
# extension APKs, compiled elsewhere and loaded at runtime, so R8 can never see a call to any of
# it. Anything not pinned here survives only if the app itself happens to call it — luck, not
# policy. The narrow version of this file proved that twice on a real device:
#
#   * R8 deleted the `$DefaultImpls` bodies of the API's interfaces — getHosterList, getVideoList,
#     getSeasonList — because rules keeping `model.**`, `online.**` and subclasses of AnimeSource
#     matched none of them. Every video list came back empty.
#   * With those restored, R8 deleted `ConfigurableAnimeSourceKt.sourcePreferences(String)` — a
#     top-level function in the package root, outside every one of those patterns. Extensions read
#     their preferences through it, typically at video-selection time.
#
# So: the whole package, public and protected members included, interfaces, `$DefaultImpls` and
# top-level facades alike — `**` matches nested classes too. The surface is small; keeping all of
# it costs nothing next to one more silent deletion.
#
# The one exclusion is the module's generated `R` classes, which land in this same package because
# it is the module's namespace. They aggregate several thousand transitive resource ids, no
# extension references them (each has its own R), and pinning them would be real dex weight for
# nothing.
-keep class !eu.kanade.tachiyomi.animesource.R,!eu.kanade.tachiyomi.animesource.R$*,eu.kanade.tachiyomi.animesource.** { public protected *; }

# The torrent half of the extension API. Called only from extensions, so to R8 it is dead code end
# to end.
-keep class eu.kanade.tachiyomi.torrentutils.** { public protected *; }

# The manga-side surface this module also carries, on the same terms as its own file.
-keep class eu.kanade.tachiyomi.source.model.** { public protected *; }
-keep class eu.kanade.tachiyomi.source.online.** { public protected *; }
-keep class eu.kanade.tachiyomi.source.** extends eu.kanade.tachiyomi.source.MangaSource { public protected *; }

# Response.asJsoup and friends — the parsing helpers every scraping extension calls.
-keep,allowoptimization class eu.kanade.tachiyomi.util.JsoupExtensionsKt { public protected *; }
