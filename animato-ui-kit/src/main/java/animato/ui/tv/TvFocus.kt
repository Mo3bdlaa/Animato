package animato.ui.tv

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.onFocusedBoundsChanged
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import kotlin.math.abs

/**
 * Whether this is a television.
 *
 * A composition local rather than a lookup at each call site so that the system service is asked
 * once, and so a preview can draw the television treatment on a development machine.
 */
val LocalIsTelevision = staticCompositionLocalOf { false }

/**
 * Provides [LocalIsTelevision] from the device's own answer.
 *
 * `UI_MODE_TYPE_TELEVISION` is what the platform sets for a leanback device — the same signal the
 * launcher uses to decide which of the app's intent filters it can see, so the app and the launcher
 * agree about what this device is instead of each guessing.
 */
@Composable
fun ProvideIsTelevision(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isTelevision = remember(context) { context.isTelevision() }

    if (!isTelevision) {
        CompositionLocalProvider(LocalIsTelevision provides false, content = content)
        return
    }

    /*
     * Material's own controls — buttons, chips, icon buttons, the navigation bar — draw a ripple
     * they choose themselves rather than the ambient indication, so the ring below is what marks
     * them on the main screen. Inside a dialog, which is its own window and out of the ring's
     * reach, this is what is left: the ripple's focus layer, three times as strong as Material's
     * default, so a focused dialog button is obviously lighter than its neighbour from across a
     * room instead of faintly.
     */
    val ripple = RippleConfiguration(rippleAlpha = TvRippleAlpha)

    CompositionLocalProvider(
        LocalIsTelevision provides true,
        LocalRippleConfiguration provides ripple,
    ) {
        VerticalFocusFallback {
            TvFocusRingHost(content)
        }
    }
}

/**
 * One ring, drawn around whatever has focus, whatever it is.
 *
 * ## Why at the root and not on each control
 *
 * The first ring was an indication, which reached every `Modifier.clickable` in the app — the
 * settings rows, the addon list — but not Material's filled buttons, chips or icon buttons: those
 * pick their ripple themselves and never ask for the ambient indication. On a television that left
 * *Discover*, *Open the addon store*, the filter chips and the top bar's icons marked by nothing
 * but a faint lightening, on exactly the screens where they are the thing to press.
 *
 * Every one of them is focusable, though, and every focusable reports its bounds to the layouts
 * around it when it takes focus — it is how a list knows to scroll a focused row into view. So the
 * root listens for the same report and draws one ring around those bounds, on top of everything.
 * It does not matter whose control it is, Animato's or Mihon's, or whether it was written next
 * year: if a remote can reach it, it is ringed.
 *
 * ## The shape
 *
 * The ring does not know the control's shape, so it guesses from the size: anything up to a
 * button's height is a pill or a circle — buttons, chips, icon buttons — a square up to the
 * player's play button is a circle, and anything else is a row or a card, which gets the same
 * 12dp corners the cards themselves use.
 *
 * ## Dialogs
 *
 * A dialog is a window of its own, and a ring drawn in the activity's window cannot reach into it.
 * [ProvideIsTelevision] puts one host at the root; a full-screen dialog that wants the same ring
 * wraps its content in another. Off a television this is only its content.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvFocusRingHost(content: @Composable () -> Unit) {
    if (!LocalIsTelevision.current) {
        content()
        return
    }
    val accent = MaterialTheme.colorScheme.primary
    var focused by remember { mutableStateOf<Rect?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .onFocusedBoundsChanged { coordinates ->
                focused = coordinates?.takeIf { it.isAttached }?.boundsInRoot()
            }
            .drawWithContent {
                drawContent()
                val bounds = focused ?: return@drawWithContent
                val stroke = FocusBorderWidth.toPx()
                val gap = FocusRingGap.toPx()
                val rect = bounds.translate(-origin).inflate(gap + stroke / 2)
                val squarish = abs(bounds.width - bounds.height) <= bounds.height * SQUARISH_TOLERANCE
                val radius = when {
                    // Buttons, chips: pills. Icon buttons and the player's round controls: circles.
                    bounds.height <= PillMaxHeight.toPx() -> rect.height / 2
                    squarish && bounds.height <= CircleMaxSize.toPx() -> rect.height / 2
                    else -> FocusRadius.toPx() + gap
                }
                drawRoundRect(
                    color = accent,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    cornerRadius = CornerRadius(radius),
                    style = Stroke(width = stroke),
                )
            },
    ) {
        content()
    }
}

/**
 * Up and down that always go somewhere.
 *
 * ## What a television showed
 *
 * With the focus ring finally drawing, a remote could be seen moving left and right along a screen's
 * top bar — and *down* did nothing, in settings and in the Stremio addon list alike: the rows below
 * were never reached and nothing in them could be chosen. Both screens are a top bar over a lazy list
 * of ordinary clickable rows, every one of them focusable. Nothing in the app consumes the key; the
 * framework's geometric search for "the nearest focusable below this one" was simply coming back
 * empty from the bar.
 *
 * ## What this does about it
 *
 * Asks the same question first, so wherever the geometric search works nothing changes. Only when it
 * answers *nowhere* does this fall back to the framework's other notion of order — *next* and
 * *previous*, the sequence a keyboard's Tab key walks — which visits every focusable on the screen
 * and so cannot strand a remote in a bar. From a top bar that means a press or two along the bar
 * before entering the list, rather than never entering it.
 *
 * Left and right are left alone. They already worked, and on a television they also carry meaning a
 * fallback would get wrong — leaving a row for the next one is not what *right* means on a carousel.
 *
 * Applied at the root because the failure is not one screen's: settings come from Mihon, the addon
 * list is this fork's, and both stranded the remote the same way. A key that a focused element
 * handles itself — a text field, a slider — never reaches here, because this only sees what bubbles
 * up unconsumed.
 */
@Composable
private fun VerticalFocusFallback(content: @Composable () -> Unit) {
    val focusManager = LocalFocusManager.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionDown ->
                        focusManager.moveFocus(FocusDirection.Down) ||
                            focusManager.moveFocus(FocusDirection.Next)
                    Key.DirectionUp ->
                        focusManager.moveFocus(FocusDirection.Up) ||
                            focusManager.moveFocus(FocusDirection.Previous)
                    else -> false
                }
            },
    ) {
        content()
    }
}

/**
 * Whether this device is driven with a remote rather than a finger.
 *
 * ## Four questions, because one was not enough
 *
 * This asked `UiModeManager` alone, which is what Android's own documentation suggests, and on a
 * real television it was apparently answering no: the focus ring shipped, reached every clickable
 * in the app, and changed nothing on screen — which is what a correct ring gated behind a false
 * answer looks like. A gate that silently fails is worse than no gate, because everything behind
 * it then looks broken for reasons that have nothing to do with it.
 *
 * `UI_MODE_TYPE_TELEVISION` is only as good as the manufacturer's own configuration, and television
 * manufacturers are not famous for getting that right. So this now asks four ways and takes any
 * yes:
 *
 * - the **leanback feature**, which every Android TV device declares by definition — this is the
 *   one the launcher itself filters on, and the one most likely to be true when the others are not;
 * - the older **television feature**, deprecated but still set on sets old enough to have shipped
 *   with it;
 * - the **UI mode**, which is right when it is set;
 * - **no touchscreen**, which is not a television in principle and is one in practice.
 *
 * Being wrong in the generous direction costs a focus ring on a device nobody is focusing anything
 * on, where nothing ever has focus and so nothing is ever drawn. Being wrong the other way costs
 * the whole television treatment, silently, which is what just happened.
 */
fun Context.isTelevision(): Boolean {
    val features = packageManager
    return features.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        features.hasSystemFeature(FEATURE_TELEVISION) ||
        getSystemService<UiModeManager>()?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        !features.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
}

/**
 * `PackageManager.FEATURE_TELEVISION`, named here because it is deprecated and referencing it
 * directly earns a warning this build turns into noise. The string is part of the platform's
 * contract and cannot change.
 */
private const val FEATURE_TELEVISION = "android.hardware.type.television"

/**
 * Clickable, and obviously reached when a D-pad reaches it.
 *
 * ## Why this exists rather than a theme change
 *
 * On a phone, focus is invisible and irrelevant: the thing you touch is the thing you meant. On a
 * television the only way to know which of forty covers is selected is that it looks different from
 * the other thirty-nine — from three metres away, across a room. Material's focus indication is a
 * low-contrast ripple meant for someone holding the device, and no change of colour turns that into
 * a television affordance.
 *
 * Two cues at once, then: a bright border in the accent colour and a small scale up. A border alone
 * disappears against a cover whose artwork happens to be bright; scale alone is invisible on an item
 * with no neighbours to be bigger than.
 *
 * ## Why it replaces `clickable` rather than wrapping it
 *
 * `clickable` already makes a node focusable and already handles the D-pad centre key. Adding a
 * separate `focusable` beside it produces two focus targets for one item, so a remote needs two
 * presses to cross a row. This *is* the clickable, sharing one interaction source with the
 * highlight, which is also what keeps the ripple and the border talking about the same event.
 *
 * Off a television it is exactly `clickable` and nothing else — no border, no scale, and no change
 * to what a hardware keyboard walks through.
 */
fun Modifier.tvClickable(
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    tvFocusRing(interactionSource).clickable(
        interactionSource = interactionSource,
        indication = LocalIndication.current,
        onClick = onClick,
    )
}

/**
 * The same ring, for a node that already owns its own click.
 *
 * [tvClickable] is the right answer wherever the item's whole job is to be tapped, and it is most of
 * them. Some nodes cannot hand their click over: the player's buttons need a long press as well, and
 * they layer their own ripple over a transparent `combinedClickable` so that the ripple lands inside
 * a circular clip rather than over the video. Giving those a `tvClickable` as well would make two
 * focus targets out of one button, and a remote would need two presses to cross a row of them.
 *
 * So this is the decoration on its own, taking the interaction source the call site already has. It
 * draws nothing off a television, and it never touches focus or click behaviour — a node that was
 * not focusable to begin with does not become focusable by being given a ring, which is why this is
 * only ever added to something already clickable.
 */
fun Modifier.tvFocusRing(
    interactionSource: InteractionSource,
): Modifier = composed {
    val isTelevision = LocalIsTelevision.current
    val focused by interactionSource.collectIsFocusedAsState()
    val highlighted = isTelevision && focused

    val scale by animateFloatAsState(
        targetValue = if (highlighted) FOCUS_SCALE else 1f,
        label = "tv-focus-scale",
    )

    // Scale only. The ring itself is drawn once, at the root, by [TvFocusRingHost] — drawing it here as
    // well put two rings on these items.
    if (!isTelevision) this else this.scale(scale)
}

private const val FOCUS_SCALE = 1.06f
private val FocusBorderWidth = 3.dp
private val FocusRadius = 12.dp

/** Air between a control and its ring, so the ring never sits on the control's own edge. */
private val FocusRingGap = 2.dp

/** Tallest a control can be and still be drawn as a pill or circle — a button, chip or icon. */
private val PillMaxHeight = 56.dp

/** Largest square control drawn as a circle: the player's play button, not a square cover. */
private val CircleMaxSize = 96.dp
private const val SQUARISH_TOLERANCE = 0.1f

/** Material's default ripple alphas, with focus tripled. See [ProvideIsTelevision]. */
private val TvRippleAlpha = RippleAlpha(
    draggedAlpha = 0.16f,
    focusedAlpha = 0.3f,
    hoveredAlpha = 0.08f,
    pressedAlpha = 0.1f,
)
