package com.github.andreyasadchy.xtra.util

import android.view.View
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.tabs.TabLayout

/**
 * Turns the phone layout of an overview screen (header that scrolls away with the list, swipeable tab pages)
 * into a TV layout. Applied by MainActivity to every fragment view on a TV.
 *
 * - Headers stay put. A big header (channel, game, team) shrinks to toolbar + tabs + sort bar while the focus is
 *   in the list and opens again when the focus goes up into it.
 * - Tab pages can't be swiped and the focus can't enter a page that isn't the visible one.
 * - The focus coming up from a list lands on the selected tab; moving along the tabs switches the page.
 */
object TvScreen {

    private const val COLLAPSING_FLAGS =
        AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED

    fun adapt(root: View) {
        forEachView(root) { view ->
            when (view) {
                is AppBarLayout -> fixAppBar(view)
                is ViewPager2 -> lockPages(view)
            }
        }
    }

    private fun fixAppBar(appBar: AppBarLayout) {
        for (i in 0 until appBar.childCount) {
            val child = appBar.getChildAt(i)
            (child.layoutParams as? AppBarLayout.LayoutParams)?.scrollFlags =
                if (child is CollapsingToolbarLayout) COLLAPSING_FLAGS else 0
        }
        appBar.requestLayout()
    }

    private fun lockPages(pager: ViewPager2) {
        pager.isUserInputEnabled = false
        val recyclerView = pager.getChildAt(0) as? RecyclerView ?: return
        // ViewPager2's own list takes the focus itself (FOCUS_BEFORE_DESCENDANTS); nothing can be reached from there
        recyclerView.isFocusable = false
        recyclerView.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        fun update() {
            for (i in 0 until recyclerView.childCount) {
                val page = recyclerView.getChildAt(i) as? ViewGroup ?: continue
                page.descendantFocusability = if (recyclerView.getChildAdapterPosition(page) == pager.currentItem) {
                    ViewGroup.FOCUS_AFTER_DESCENDANTS
                } else {
                    ViewGroup.FOCUS_BLOCK_DESCENDANTS
                }
            }
        }
        recyclerView.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                recyclerView.post { update() }
            }

            override fun onChildViewDetachedFromWindow(view: View) {}
        })
        // Registered after the screen's own callbacks, so it runs after them. Some screens change the header's
        // scroll flags when the page changes (channel page), put the TV ones back.
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                update()
                findAppBar(pager)?.let { fixAppBar(it) }
            }
        })
    }

    /**
     * The selected tab, when a key is about to move the focus from outside into a tab bar ([next] is where it
     * would go). Applied before the key is handled, so the focus doesn't visibly stop at the closest tab first.
     */
    fun tabEntry(focused: View, next: View): View? {
        if (next !is TabLayout.TabView) return null
        val tabLayout = findParent<TabLayout>(next) ?: return null
        if (isInside(focused, tabLayout)) return null
        return tabLayout.getTabAt(tabLayout.selectedTabPosition)?.view?.takeIf { it !== next }
    }

    fun onFocusChanged(oldFocus: View?, newFocus: View?) {
        if (newFocus == null) return
        if (newFocus is TabLayout.TabView) {
            val tabLayout = findParent<TabLayout>(newFocus)
            if (tabLayout != null) {
                if (oldFocus == null || !isInside(oldFocus, tabLayout)) {
                    // coming from the list or the toolbar: go to the selected tab, not the closest one
                    val selected = tabLayout.getTabAt(tabLayout.selectedTabPosition)?.view
                    if (selected != null && selected !== newFocus) {
                        newFocus.post { selected.requestFocus() }
                        return
                    }
                } else {
                    newFocus.tab?.takeIf { !it.isSelected }?.select()
                }
            }
        }
        val appBar = findAppBar(newFocus) ?: return
        if (appBar.getChildAt(0) is CollapsingToolbarLayout) {
            appBar.setExpanded(isInside(newFocus, appBar), true)
        }
    }

    private fun findAppBar(view: View): AppBarLayout? {
        var current: View? = view
        while (current != null) {
            if (current is AppBarLayout) return current
            if (current is CoordinatorLayout) {
                for (i in 0 until current.childCount) {
                    (current.getChildAt(i) as? AppBarLayout)?.let { return it }
                }
            }
            current = current.parent as? View
        }
        return null
    }

    private inline fun <reified T : View> findParent(view: View): T? {
        var current = view.parent
        while (current != null) {
            if (current is T) return current
            current = current.parent
        }
        return null
    }

    private fun isInside(view: View, container: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current === container) return true
            current = current.parent as? View
        }
        return false
    }

    private fun forEachView(view: View, action: (View) -> Unit) {
        action(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                forEachView(view.getChildAt(i), action)
            }
        }
    }
}
