package com.github.andreyasadchy.xtra.util

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.github.andreyasadchy.xtra.R

/**
 * Cards on phones have several tap targets (channel picture and name, game, tags, a menu button). With a remote
 * those make the focus jump around inside and between cards, so on a TV a card is a single focus stop and its
 * other targets are offered in a list when OK is held (or MENU is pressed).
 */
object TvCardActions {

    fun prepare(item: View) {
        if (item is ViewGroup) {
            item.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
        item.isFocusable = true
    }

    fun click(item: View) {
        if (item.hasOnClickListeners()) {
            item.performClick()
        } else {
            show(item)
        }
    }

    fun show(item: View) {
        val context = item.context
        val labels = mutableListOf<CharSequence>()
        val actions = mutableListOf<() -> Unit>()
        collect(item, item) { label, view ->
            if (labels.none { it.toString() == label.toString() }) {
                labels.add(label)
                actions.add { view.performClick() }
            }
        }
        if (item.isLongClickable) {
            val label = (item.getTag(R.id.tv_long_press_label) as? Int)?.let { context.getString(it) }
                ?: context.getString(R.string.tv_action_more)
            labels.add(label)
            actions.add { item.performLongClick() }
        }
        if (actions.isNotEmpty()) {
            context.getAlertDialogBuilder()
                .setItems(labels.toTypedArray()) { _, which -> actions.getOrNull(which)?.invoke() }
                .show()
        }
    }

    private fun collect(item: View, view: View, add: (CharSequence, View) -> Unit) {
        if (view !== item && view.isShown && view.hasOnClickListeners()) {
            label(item, view)?.let { add(it, view) }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                collect(item, view.getChildAt(i), add)
            }
        }
    }

    private fun label(item: View, view: View): CharSequence? {
        val context = view.context
        val text = (view as? TextView)?.text?.takeIf { it.isNotBlank() }
        return when (idName(view)) {
            "userImage", "username", "userName", "channel", "channelName" -> {
                val channel = item.findViewById<TextView>(R.id.username)?.takeIf { it.isShown }?.text?.takeIf { it.isNotBlank() } ?: text
                if (channel != null) context.getString(R.string.tv_action_channel, channel) else context.getString(R.string.tv_action_channel_no_name)
            }
            "gameName" -> text?.let { context.getString(R.string.tv_action_game, it) }
            "options" -> context.getString(R.string.tv_action_options)
            else -> when {
                text != null && isTag(item, view) -> context.getString(R.string.tv_action_tag, text)
                text != null -> text
                else -> view.contentDescription?.takeIf { it.isNotBlank() }
            }
        }
    }

    private fun isTag(item: View, view: View): Boolean {
        var parent = view.parent as? View
        while (parent != null && parent !== item) {
            if (idName(parent)?.contains("tag", true) == true) return true
            parent = parent.parent as? View
        }
        return false
    }

    private fun idName(view: View): String? {
        if (view.id == View.NO_ID) return null
        return try {
            view.resources.getResourceEntryName(view.id)
        } catch (e: Exception) {
            null
        }
    }
}
