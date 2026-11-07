package com.skd.notebook.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.skd.notebook.data.local.NoteEntity

/**
 * Lets the user change the order of the notes: press and hold a note, then drag it to a new place.
 * The note lifts a little while it is held, and the others make room as it moves. When it is let go,
 * [onDropped] gets the list as it now stands, in the new order.
 *
 * A screen that also swipes notes away passes [swipeDirs] and overrides onSwiped.
 */
open class NoteDragCallback(
    private val adapter: NoteAdapter,
    swipeDirs: Int = 0,
    private val onDropped: (List<NoteEntity>) -> Unit
) : ItemTouchHelper.SimpleCallback(
    ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
    swipeDirs
) {
    private var moved = false

    override fun isLongPressDragEnabled() = true

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val from = viewHolder.bindingAdapterPosition
        val to = target.bindingAdapterPosition
        if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
        adapter.moveItem(from, to)
        moved = true
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
            viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            lift(viewHolder.itemView, up = true)
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        lift(viewHolder.itemView, up = false)
        if (moved) {
            moved = false
            onDropped(adapter.currentList)
            // The columns were handed out before the notes moved: hand them out again so the grid is even.
            recyclerView.post { (recyclerView.layoutManager as? StaggeredGridLayoutManager)?.invalidateSpanAssignments() }
        }
    }

    /**
     * Raises the held note a little (and puts it back). Set at once, not animated: the list's own move and
     * change animations cancel a running view animation, which used to leave a note slightly enlarged and
     * so shrink the gap around it.
     */
    private fun lift(view: View, up: Boolean) {
        view.animate().cancel()
        val scale = if (up) 1.04f else 1f
        view.scaleX = scale
        view.scaleY = scale
        view.alpha = if (up) 0.92f else 1f
    }
}
