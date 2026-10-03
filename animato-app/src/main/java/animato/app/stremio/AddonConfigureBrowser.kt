package animato.app.stremio

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.getSystemService
import animato.anime.stremio.StremioUrls
import animato.ui.tv.TvFocusRingHost
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

/**
 * An addon's own settings page, inside the app, with a way back that carries the result.
 *
 * ## Why not hand it to the system browser
 *
 * A configurable addon is configured on its own site, and the site ends in one of two ways: an
 * *Install* button that opens Stremio, or a link to copy. Neither lands anywhere useful from an
 * external browser — the install link asks for an app that is not this one, and the copied link
 * has to be carried back by hand, which on a television means a remote and an on-screen keyboard
 * typing a hundred-character address. Kept in here, both endings come back on their own.
 *
 * ## How the address gets back
 *
 * - **Install:** the page navigates to a `stremio://` link, or to Stremio's web app carrying the
 *   address. Either navigation is caught before it leaves, and the address it names is the answer.
 * - **Copy:** the page puts the address on the clipboard. *Paste address* in the bar reads it.
 *
 * Both go through [StremioUrls.addressIn], so a page that copies the address with a sentence
 * around it, or quotes it, still gives up just the address.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddonConfigureBrowser(
    url: String,
    onAddress: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val currentOnAddress by rememberUpdatedState(onAddress)
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var clipboardEmpty by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Back walks the page's own history first. Configure pages are often several steps, and
        // the hardware back button closing the whole thing halfway through would lose the form.
        BackHandler(enabled = canGoBack) { webView?.goBack() }

        // Its own ring: a dialog is a window of its own, and the app's ring cannot reach into it.
        TvFocusRingHost {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(MR.strings.action_close),
                                )
                            }
                        },
                        title = {
                            Text(
                                text = stringResource(AYMR.strings.stremio_configure_title),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        actions = {
                            // A filled button rather than an icon: it is the way out of this screen
                            // with the result, and the person has to be able to find it without being
                            // told where it is.
                            FilledTonalButton(
                                onClick = {
                                    val address = context.clipboardText()?.let(StremioUrls::addressIn)
                                    if (address != null) currentOnAddress(address) else clipboardEmpty = true
                                },
                                modifier = Modifier.padding(end = MaterialTheme.padding.small),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentPaste,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = MaterialTheme.padding.extraSmall),
                                )
                                Text(stringResource(AYMR.strings.stremio_paste_address))
                            }
                        },
                    )
                    Text(
                        text = stringResource(
                            if (clipboardEmpty) {
                                AYMR.strings.stremio_clipboard_no_address
                            } else {
                                AYMR.strings.stremio_configure_hint
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (clipboardEmpty) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(
                            horizontal = MaterialTheme.padding.medium,
                            vertical = MaterialTheme.padding.small,
                        ),
                    )
                    if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    AndroidView(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        factory = { viewContext ->
                            @SuppressLint("SetJavaScriptEnabled")
                            val view = WebView(viewContext).apply {
                                setDefaultSettings()
                                // One window. With several allowed, a link that opens in a new tab —
                                // which is how a good many Install buttons are written — asks for a
                                // window nobody creates, and the press silently does nothing.
                                settings.setSupportMultipleWindows(false)
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView,
                                        request: WebResourceRequest,
                                    ): Boolean {
                                        val address = StremioUrls.addressIn(request.url.toString())
                                            ?: return false
                                        currentOnAddress(address)
                                        return true
                                    }

                                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                        loading = true
                                    }

                                    override fun onPageFinished(view: WebView, url: String?) {
                                        loading = false
                                        canGoBack = view.canGoBack()
                                    }
                                }
                                loadUrl(url)
                            }
                            webView = view
                            view
                        },
                        onRelease = { it.destroy() },
                    )
                }
            }
        }
    }
}

/** The text on the clipboard, or null. Read on demand, which is the only time Android allows it. */
internal fun Context.clipboardText(): String? =
    getSystemService<ClipboardManager>()
        ?.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.coerceToText(this)
        ?.toString()
        ?.takeIf { it.isNotBlank() }
