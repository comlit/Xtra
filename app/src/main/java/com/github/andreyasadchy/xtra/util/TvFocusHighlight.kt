package com.github.andreyasadchy.xtra.util

import android.app.Activity
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.View
import com.github.andreyasadchy.xtra.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

/**
 * On a TV the only way to know where you are is the focus indicator. The default card ripple is a
 * faint overlay that is hard to see from across the room, so outline the focused card instead.
 */
object TvFocusHighlight {

    private class OriginalStroke(val width: Int, val color: ColorStateList?)

    fun install(activity: Activity) {
        val strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3f, activity.resources.displayMetrics).toInt()
        activity.window.decorView.viewTreeObserver.addOnGlobalFocusChangeListener { oldFocus, newFocus ->
            (oldFocus as? MaterialCardView)?.let { removeHighlight(it) }
            (newFocus as? MaterialCardView)?.let { addHighlight(it, strokeWidth) }
        }
    }

    private fun addHighlight(card: MaterialCardView, strokeWidth: Int) {
        if (card.getTag(R.id.tv_focus_original_stroke) == null) {
            card.setTag(R.id.tv_focus_original_stroke, OriginalStroke(card.strokeWidth, card.strokeColorStateList))
        }
        card.strokeWidth = strokeWidth
        card.setStrokeColor(ColorStateList.valueOf(MaterialColors.getColor(card, androidx.appcompat.R.attr.colorPrimary)))
    }

    private fun removeHighlight(card: View) {
        val original = card.getTag(R.id.tv_focus_original_stroke) as? OriginalStroke ?: return
        card.setTag(R.id.tv_focus_original_stroke, null)
        (card as MaterialCardView).apply {
            strokeWidth = original.width
            setStrokeColor(original.color)
        }
    }
}
