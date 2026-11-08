package com.github.andreyasadchy.xtra.util

import android.app.Activity
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.View
import android.widget.EditText
import androidx.core.content.ContextCompat
import com.github.andreyasadchy.xtra.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.navigationrail.NavigationRailView
import java.lang.ref.WeakReference

/**
 * On a TV the only way to know where you are is the focus indicator. The default card ripple is a
 * faint overlay that is hard to see from across the room, so outline the focused card instead.
 */
object TvFocusHighlight {

    private class OriginalStroke(val width: Int, val color: ColorStateList?)

    fun install(activity: Activity) {
        val strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3f, activity.resources.displayMetrics).toInt()
        // The card that currently has the outline. oldFocus isn't always that card (focus can be cleared or the
        // card recycled while focused), so it's tracked here to make sure only one card is ever outlined.
        var highlightedCard: WeakReference<MaterialCardView>? = null
        activity.window.decorView.viewTreeObserver.addOnGlobalFocusChangeListener { oldFocus, newFocus ->
            (oldFocus as? MaterialCardView)?.let { removeHighlight(it) }
            highlightedCard?.get()?.takeIf { it !== newFocus }?.let { removeHighlight(it) }
            highlightedCard = null
            when (newFocus) {
                is MaterialCardView -> {
                    addHighlight(newFocus, strokeWidth)
                    highlightedCard = WeakReference(newFocus)
                }
                null -> {}
                else -> addFocusForeground(newFocus)
            }
        }
    }

    /**
     * Most views only show focus as a faint ripple (toolbar icons, tabs, the sort bar, rail items) or not at all
     * (chat messages). From the couch that looks like the D-pad stopped working, so every focused view gets an
     * outline drawn on top. It's a state list drawable that only shows while focused, so it can stay set.
     */
    private fun addFocusForeground(view: View) {
        if (view.getTag(R.id.tv_focus_foreground) != null) return
        val drawable = when {
            isNavigationRailItem(view) -> R.drawable.tv_focus_rail_item
            // views with their own foreground, text fields (cursor), the whole player (own white indicators on its
            // controls; it can take the focus before it's full screen) and other full screen containers keep their look
            view.foreground != null || view is EditText || isInside(view, R.id.playerContainer) || isFullScreen(view) -> return
            else -> R.drawable.tv_focus_outline
        }
        view.setTag(R.id.tv_focus_foreground, true)
        view.foreground = ContextCompat.getDrawable(view.context, drawable)
    }

    private fun isInside(view: View, id: Int): Boolean {
        var current: View? = view
        while (current != null) {
            if (current.id == id) return true
            current = current.parent as? View
        }
        return false
    }

    private fun isFullScreen(view: View): Boolean {
        val root = view.rootView
        return view.width >= root.width * 0.9f && view.height >= root.height * 0.9f
    }

    private fun isNavigationRailItem(view: View): Boolean {
        var parent = view.parent
        for (i in 0 until 4) {
            if (parent is NavigationRailView) {
                return parent.menu.findItem(view.id) != null
            }
            parent = parent?.parent ?: return false
        }
        return false
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
