package com.studyai.ui.dictionary

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

/**
 * Custom floating TextToolbar hiển thị nút "✨ Từ điển sống" bên cạnh "Sao chép" khi bôi đen.
 */
class LivingDictionaryTextToolbar(
    private val view: View,
    private val onLookupSelectedText: () -> Unit,
) : TextToolbar {

    private var actionMode: ActionMode? = null
    override var status: TextToolbarStatus = TextToolbarStatus.Hidden
        private set

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        actionMode?.finish()
        status = TextToolbarStatus.Shown
        actionMode = view.startActionMode(
            object : ActionMode.Callback2() {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                    menu.add(0, MENU_ID_LIVING_DICT, 0, "✨ Từ điển sống")
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    if (onCopyRequested != null) {
                        menu.add(0, MENU_ID_COPY, 1, "Sao chép")
                            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                    }
                    if (onSelectAllRequested != null) {
                        menu.add(0, MENU_ID_SELECT_ALL, 2, "Chọn tất cả")
                            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                    }
                    return true
                }

                override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

                override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                    return when (item.itemId) {
                        MENU_ID_LIVING_DICT -> {
                            onCopyRequested?.invoke()
                            onLookupSelectedText()
                            mode.finish()
                            true
                        }
                        MENU_ID_COPY -> {
                            onCopyRequested?.invoke()
                            mode.finish()
                            true
                        }
                        MENU_ID_SELECT_ALL -> {
                            onSelectAllRequested?.invoke()
                            true
                        }
                        else -> false
                    }
                }

                override fun onDestroyActionMode(mode: ActionMode) {
                    status = TextToolbarStatus.Hidden
                    actionMode = null
                }

                override fun onGetContentRect(
                    mode: ActionMode,
                    view: View,
                    outRect: android.graphics.Rect,
                ) {
                    outRect.set(
                        rect.left.toInt(),
                        rect.top.toInt(),
                        rect.right.toInt(),
                        rect.bottom.toInt(),
                    )
                }
            },
            ActionMode.TYPE_FLOATING,
        )
    }

    override fun hide() {
        status = TextToolbarStatus.Hidden
        actionMode?.finish()
        actionMode = null
    }

    companion object {
        private const val MENU_ID_LIVING_DICT = 1001
        private const val MENU_ID_COPY = 1002
        private const val MENU_ID_SELECT_ALL = 1003
    }
}
