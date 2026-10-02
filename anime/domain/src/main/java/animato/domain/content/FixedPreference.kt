package animato.domain.content

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import tachiyomi.core.common.preference.Preference

/**
 * A preference the build has already answered, which nothing can change.
 *
 * ## Why this is a `Preference` and not a constant
 *
 * Because around twenty screens read the lens, and they read it three different ways — `get()` for
 * a one-off, `changes()` for a flow, `collectAsState()` for composition. Replacing the type with a
 * constant would mean touching every one of those call sites, and every future one would have to
 * remember the constant exists. Implementing the interface instead means the question "is this
 * build fixed to one content type" is answered in one place and no screen has to ask it.
 *
 * ## Why writes are swallowed rather than refused
 *
 * A build with half its native libraries removed cannot honour a write to this value — not "should
 * not", *cannot*: the code that would run has no library to load. Throwing would turn that into a
 * crash at whatever call site had not been audited, which is a worse answer than the write quietly
 * not happening, and it would make correctness depend on auditing every caller forever. Nothing is
 * supposed to write to a fixed lens; the control that used to is not drawn. This is the floor under
 * that, not the mechanism for it.
 *
 * [isSet] answers false and [delete] does nothing, both for the same reason: there is no stored
 * value here, so a backup has nothing of this to carry and a restore has nothing to put back. That
 * is what keeps a backup taken on a phone from narrowing a television, or the reverse.
 */
class FixedPreference<T>(
    private val storedKey: String,
    private val value: T,
) : Preference<T> {

    private val flow = MutableStateFlow(value)

    override fun key(): String = storedKey

    override fun get(): T = value

    override fun set(value: T) = Unit

    override fun isSet(): Boolean = false

    override fun delete() = Unit

    override fun defaultValue(): T = value

    override fun changes(): Flow<T> = flow

    override fun stateIn(scope: CoroutineScope): StateFlow<T> = flow
}
