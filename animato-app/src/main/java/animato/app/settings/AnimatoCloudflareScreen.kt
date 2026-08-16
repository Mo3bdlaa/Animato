package animato.app.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import animato.domain.content.ContentType
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Every installed source, and a way to open one in a real browser window.
 *
 * The point is not the list. The point is that passing a Cloudflare check by hand is already
 * possible and completely undiscoverable: it happens to work if you find the "open in WebView"
 * action buried in one particular screen, and nothing anywhere says that is the fix.
 *
 * What makes it work at all is that the pieces already line up. `WebViewScreen` renders with the
 * same user agent the app's own requests use, and a WebView writes its cookies into Android's
 * process-wide `CookieManager` — the same store `AndroidCookieJar` reads. So a check passed here is
 * a check passed for every extension, every tracker and both halves of the library, with nothing to
 * copy across and nothing to configure.
 *
 * Both halves are listed together, deliberately. Cloudflare is a property of a *site*, and the
 * anime screens and the manga screens are not the same screens — only this one can reach both.
 */
object AnimatoCloudflareScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val sites = remember { installedSites() }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(AYMR.strings.pref_cloudflare_title),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            if (sites.isEmpty()) {
                EmptyScreen(
                    stringRes = AYMR.strings.cloudflare_no_sources,
                    modifier = Modifier.padding(contentPadding),
                )
                return@Scaffold
            }

            LazyColumn(contentPadding = contentPadding) {
                item {
                    Text(
                        text = stringResource(AYMR.strings.cloudflare_explainer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    HorizontalDivider()
                }

                items(sites, key = { "${it.contentType}:${it.id}" }) { site ->
                    SiteRow(
                        site = site,
                        onClick = {
                            navigator.push(
                                WebViewScreen(
                                    url = site.url,
                                    initialTitle = site.name,
                                    // Mihon's screen looks this up in its own SourceManager, so an
                                    // anime id would resolve to nothing and its headers would be
                                    // lost. Better no headers than another source's.
                                    sourceId = site.id.takeIf { site.contentType == ContentType.MANGA },
                                ),
                            )
                        },
                    )
                }
            }
        }
    }

    @Composable
    private fun SiteRow(site: Site, onClick: () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = site.name,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = site.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    /**
     * One entry per online source, both halves, sorted by name.
     *
     * Local sources and stubs are excluded by asking each manager for its *online* sources: a check
     * cannot be passed for a folder on the phone.
     */
    private fun installedSites(): List<Site> {
        val manga = Injekt.get<SourceManager>().getOnlineSources().map {
            Site(it.id, it.name, it.getHomeUrl(), ContentType.MANGA)
        }
        val anime = Injekt.get<AnimeSourceManager>().getOnlineSources().map {
            Site(it.id, it.name, it.getHomeUrl(), ContentType.ANIME)
        }
        return (manga + anime)
            .distinctBy { it.url }
            .sortedBy { it.name.lowercase() }
    }

    private data class Site(
        val id: Long,
        val name: String,
        val url: String,
        val contentType: ContentType,
    )
}
