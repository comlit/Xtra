package com.github.andreyasadchy.xtra.ui.view

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.FocusFinder
import android.view.KeyEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TvCardActions
import com.github.andreyasadchy.xtra.util.prefs

/**
 * The list of cards on every overview screen on Android TV (layout-television/common_recycler_view_layout.xml).
 *
 * A normal grid (rows line up, cards in a row have the same height) with remote control rules:
 * - the focus leaves the list only UP (to the header) and LEFT (to the navigation rail); DOWN at the end of the
 *   list and RIGHT at the last column stay where they are
 * - coming back into the list goes to the card that was focused last, not to whatever card is closest
 * - the list jumps to the focused card instead of animating, so the focus can't get ahead of the scrolling
 * - each card is a single focus stop: OK opens it, holding OK (or MENU) lists everything else the card offers
 */
class TvGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : RecyclerView(context, attrs, defStyle) {

    private var lastFocusedPosition = NO_POSITION
    private var pressedItem: View? = null
    private var longPressHandled = false
    private val longPress = Runnable {
        pressedItem?.let { item ->
            longPressHandled = true
            item.isPressed = false
            TvCardActions.show(item)
        }
    }

    init {
        val prefs = context.prefs()
        val columns = prefs.getString(C.LANDSCAPE_COLUMN_COUNT, "3")?.toIntOrNull()?.coerceAtLeast(1) ?: 3
        layoutManager = object : GridLayoutManager(context, columns) {
            override fun requestChildRectangleOnScreen(parent: RecyclerView, child: View, rect: Rect, immediate: Boolean, focusedChildVisible: Boolean): Boolean {
                return super.requestChildRectangleOnScreen(parent, child, rect, true, focusedChildVisible)
            }
        }
        if (!prefs.getBoolean(C.UI_THEME_MATERIAL3, true) && columns > 1) {
            addItemDecoration(MarginItemDecoration(context.resources.getDimension(R.dimen.divider_margin).toInt(), columns))
        }
        // No sliding cards: while a page loads they animated into place over each other. Instant on a TV.
        // (Some screens configure the item animator, so it stays, with zero durations.)
        itemAnimator?.apply {
            addDuration = 0
            removeDuration = 0
            moveDuration = 0
            changeDuration = 0
        }
        // the list itself never holds the focus, only its cards
        isFocusable = false
        descendantFocusability = FOCUS_AFTER_DESCENDANTS
        // the header never moves because of the list, see TvScreen
        isNestedScrollingEnabled = false
        clipToPadding = false
    }

    override fun onChildAttachedToWindow(child: View) {
        super.onChildAttachedToWindow(child)
        TvCardActions.prepare(child)
    }

    override fun requestChildFocus(child: View?, focused: View?) {
        super.requestChildFocus(child, focused)
        if (child != null) {
            getChildAdapterPosition(child).takeIf { it != NO_POSITION }?.let { lastFocusedPosition = it }
        }
    }

    override fun focusSearch(focused: View, direction: Int): View? {
        val next = super.focusSearch(focused, direction)
        if (next != null && next !== focused && isInsideList(next)) return next
        return when (direction) {
            View.FOCUS_DOWN, View.FOCUS_RIGHT -> focused
            // Leaving the list: search the whole window directly. Going through the parents would ask the tab
            // pager (ViewPager2), which tries to scroll to the neighbouring tab page and keeps the focus here.
            else -> (rootView as? ViewGroup)?.let { FocusFinder.getInstance().findNextFocus(it, focused, direction) }
        }
    }

    // From outside, the only way in is the card that had the focus last (or the first one shown).
    // An empty list ("nothing here", still loading) can't take the focus at all.
    override fun addFocusables(views: ArrayList<View>, direction: Int, focusableMode: Int) {
        if (hasFocus()) {
            super.addFocusables(views, direction, focusableMode)
        } else {
            entryCard()?.let { views.add(it) }
        }
    }

    override fun onRequestFocusInDescendants(direction: Int, previouslyFocusedRect: Rect?): Boolean {
        return entryCard()?.requestFocus() == true || super.onRequestFocusInDescendants(direction, previouslyFocusedRect)
    }

    private fun entryCard(): View? {
        val remembered = layoutManager?.findViewByPosition(lastFocusedPosition)?.takeIf { isOnScreen(it) }
        if (remembered != null) return remembered
        return (0 until childCount).map { getChildAt(it) }.firstOrNull { it.isFocusable && it.top >= 0 && isOnScreen(it) }
            ?: (0 until childCount).map { getChildAt(it) }.firstOrNull { it.isFocusable && isOnScreen(it) }
    }

    private fun isOnScreen(child: View): Boolean = child.isShown && child.bottom > 0 && child.top < height

    private fun isInsideList(view: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current === this) return true
            current = current.parent as? View
        }
        return false
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        val item = focusedChild
        if (item != null && event != null) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                    when (event.action) {
                        KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                            longPressHandled = false
                            pressedItem = item
                            item.isPressed = true
                            postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                        } else if (event.isLongPress && !longPressHandled && pressedItem === item) {
                            // the remote reports the long press itself
                            removeCallbacks(longPress)
                            longPress.run()
                        }
                        KeyEvent.ACTION_UP -> {
                            removeCallbacks(longPress)
                            val pressed = pressedItem
                            pressedItem = null
                            pressed?.isPressed = false
                            if (pressed === item && !longPressHandled) {
                                TvCardActions.click(item)
                            }
                        }
                    }
                    return true
                }
                KeyEvent.KEYCODE_MENU -> {
                    if (event.action == KeyEvent.ACTION_UP) {
                        TvCardActions.show(item)
                    }
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPress)
        pressedItem = null
        super.onDetachedFromWindow()
    }
}
