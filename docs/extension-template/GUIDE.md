# Writing an extension

Start to finish, including doing the whole thing from a phone.

---

## 1. What you are building

An APK. Not a script, not a config file — an Android package that gets installed on the device
alongside Animato, which then loads a class out of it.

Animato finds it by scanning installed packages for one declaration. Three things in
`AndroidManifest.xml` decide whether it is accepted:

| | |
|---|---|
| `<uses-feature android:name="tachiyomi.animeextension">` | what makes it an extension — this, not the package name, is the filter |
| `tachiyomi.animeextension.class` | where the source class is |
| `tachiyomi.animeextension.lib` | **14** or 16, and 14 is the version published |

Get any of them wrong and the APK installs, shows up in Android's app list, and Animato never sees
it — with nothing said anywhere. If an extension seems not to exist, check these three first.

## 2. Look at the site before writing anything

This is the step people skip and then spend a day on.

Open the site on a desktop browser, press F12, go to the **Network** tab, and play something.
Then work backwards:

1. **The last request before the video starts.** Usually `.m3u8` or `.mp4`. That URL is what your
   extension has to end up producing — everything else is working back towards it.
2. **What that request carried.** Look at its Request Headers. A `Referer`, a `Cookie`, a specific
   `User-Agent` — if it needed one, your extension needs it too, or the same URL returns 403.
3. **Where it came from.** Usually the page holds an `<iframe>` pointing at an embed host, and the
   host's page builds the URL in JavaScript. Note which hosts the site uses; each is its own piece
   of work, and one site typically uses several.
4. **The listing pages.** Right-click a card in the results grid → Inspect. You want the selector
   for one card, and inside it the link, the title and the image.

Write these down before opening an editor. Everything below is mechanical once you have them.

## 3. Fill in the class

Six methods, in two kinds: a `*Request` that says what to fetch, and a `*Parse` that turns the
response into the app's model. The app does the fetching, which is what gives you its cache, its
Cloudflare handling and its proxy for free.

| Method | What it returns |
|---|---|
| `popularAnimeRequest` / `Parse` | the front page grid |
| `latestUpdatesRequest` / `Parse` | usually the same parse on a different URL |
| `searchAnimeRequest` / `Parse` | whatever the site's own search box produces |
| `animeDetailsParse` | title, description, genres, poster, status |
| `episodeListParse` | one `SEpisode` per row, **newest first** |
| `videoListParse` | direct video URLs, one `Video` per quality |

Three things worth getting right the first time, because they are painful later:

**`url` is an identity, not a link.** It is stored in the library and handed back to you when
somebody opens that entry months later. Keep it relative (`setUrlWithoutDomain`) and keep its shape
stable across releases — changing it orphans everything anybody had saved.

**`episode_number` is what the app sorts and tracks by.** A missing one leaves every episode at 0
and the list in whatever order the page happened to be in.

**Return every quality you find.** Animato remembers which one somebody picked per anime, so
offering four is more useful than choosing for them.

## 4. Doing it all from a phone

You never need a local build. The CI builds, signs and publishes; you only edit text.

**One-time setup, from a computer** (this part needs a keytool):

```
keytool -genkey -v -keystore signing.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
base64 -w0 signing.jks
```

Put that base64 string, and the passwords, into the repository's **Settings → Secrets and variables
→ Actions**:

- `SIGNING_KEYSTORE` — the base64
- `SIGNING_KEYSTORE_PASSWORD`
- `SIGNING_KEY_ALIAS`
- `SIGNING_KEY_PASSWORD`

Never commit the keystore itself. And keep it: the store records its fingerprint, so signing a
later release with a different key makes the update be refused as a different app.

**Then, from the phone, every time:**

1. Open the file on github.com and press the pencil. The mobile web editor is workable for this;
   for anything longer, an app with a real editor over the repository is better.
2. Commit. The workflow starts on its own.
3. Watch the **Actions** tab. Two or three minutes.
4. It pushes to the `repo` branch — APK and index together.
5. In Animato: **Sources → Extension stores → Anime → +**, paste the address, and install.

Reinstalling to test each change is the slow part of this loop, not the build.

## 5. The address people add

```
https://raw.githubusercontent.com/<user>/<repository>/repo/index.min.json
```

## 6. Releasing an update

Raise `extVersionCode` in that extension's `build.gradle.kts`. That number is the **only** thing
Animato compares — the version name is never looked at. A release that forgets to raise it is a
release nobody is offered.

## 7. When it stops working

It will. Sites change their markup and an extension breaks with the first change, usually silently
— as a source that returns an empty list rather than an error.

The order to check:

1. **Does the URL still work in a browser?** Sites move paths and rename themselves.
2. **Does it work without your headers?** And with them? A newly added Cloudflare rule shows up
   here.
3. **Do the selectors still match?** Fetch the page and look. This is the usual answer.
4. **Did the embed host change?** The listing half can be fine while the video half is dead, which
   presents as everything loading and nothing playing.

## 8. Before publishing one

Check the terms of the site you are targeting, and what it is actually serving. Whether an
extension is something to publish or to keep to yourself depends on the answer, and the answer is
different for a site that licenses its content than for one that does not.
