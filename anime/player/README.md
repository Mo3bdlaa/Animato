# `:anime:player` — work in progress

Registered and building, but only the first stage is here.

## What is here

**The loaders**, `EpisodeLoader` and `HosterLoader`, which turn an episode into a playable video:
ask the source for its hosters, ask each hoster for its video list, pick the best stream the user's
quality preferences allow.

**`PlayerEpisodeVideoResolver`**, the implementation of `animato.anime.services.download`'s
`EpisodeVideoResolver`. That interface is why this stage came first: the downloader needs a video
and cannot get one without this code, so anime downloading was inert until it landed. With the
binding in place the anime dependency graph has no unresolved types left.

**`HosterState`**, extracted rather than ported. Aniyomi declared it inside `QualitySheet.kt`, a
Compose file, so the loaders — and through them the downloader — imported a screen to reach a
model. It carries no Compose and describes no drawing, so it is a file of its own here.

## What is not here

The player itself: `PlayerActivity`, `PlayerViewModel`, `AniyomiMPVView`, the observers, the
controls and the settings screens — around 12,000 lines in Aniyomi, and it needs mpv, FFmpeg and
the external-player intents wired up too.

Splitting it this way was deliberate. The loaders are 380 lines that unblock a whole feature; the
playback surface is large, needs a device to verify, and unblocks nothing until it is finished.

| Stage | | State |
| --- | --- | --- |
| 5a | loaders and the video resolver | done |
| 5b | playback core — activity, view model, mpv view, observers | |
| 5c | controls and player settings screens | |

Nothing in 5b or 5c is blocked by anything except itself.
