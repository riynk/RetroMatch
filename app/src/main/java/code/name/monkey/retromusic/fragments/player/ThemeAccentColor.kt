package code.name.monkey.retromusic.fragments.player

import androidx.annotation.ColorInt
import androidx.fragment.app.Fragment

/** Resolves the active app theme's accent color for the karaoke word highlight. */
@ColorInt
internal fun Fragment.accentColor(): Int {
    val attributes = requireContext().obtainStyledAttributes(intArrayOf(android.R.attr.colorAccent))
    return try {
        attributes.getColor(0, 0xFFFFFFFF.toInt())
    } finally {
        attributes.recycle()
    }
}
