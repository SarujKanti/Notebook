package com.skd.notebook.ui.screens

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.skd.notebook.R
import com.skd.notebook.data.local.NoteEntity
import com.skd.notebook.ui.NoteAdapter
import com.skd.notebook.ui.NoteViewModel

class SearchActivity : AppCompatActivity() {

    private lateinit var viewModel: NoteViewModel
    private lateinit var adapter: NoteAdapter
    private lateinit var etSearch: EditText
    private lateinit var btnBack: ImageButton
    private lateinit var btnClear: ImageButton
    private lateinit var recyclerSearch: RecyclerView
    private lateinit var layoutHint: LinearLayout
    private lateinit var tvHintMessage: TextView

    private val noteColors = listOf(
        "",        "#FFCDD2", "#F8BBD9", "#FFE0B2", "#FFF9C4",
        "#DCEDC8", "#B2EBF2", "#BBDEFB", "#E1BEE7", "#D7CCC8"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_search)

        etSearch       = findViewById(R.id.etSearch)
        btnBack        = findViewById(R.id.btnBack)
        btnClear       = findViewById(R.id.btnClear)
        recyclerSearch = findViewById(R.id.recyclerSearch)
        layoutHint     = findViewById(R.id.layoutHint)
        tvHintMessage  = findViewById(R.id.tvHintMessage)

        viewModel = ViewModelProvider(this)[NoteViewModel::class.java]

        adapter = NoteAdapter(
            onClick     = { note -> openNoteEditor(note) },
            onLongClick = { /* no long-press actions in search */ }
        )

        val spanCount = resources.getInteger(R.integer.grid_span_count)
        recyclerSearch.layoutManager = StaggeredGridLayoutManager(spanCount, StaggeredGridLayoutManager.VERTICAL)
        recyclerSearch.adapter = adapter

        // ── Search text watcher ──────────────────────────────────────────
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                btnClear.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                performSearch(query)
            }
        })

        btnBack.setOnClickListener { finish() }

        btnClear.setOnClickListener {
            etSearch.setText("")
            etSearch.requestFocus()
        }

        // Auto-open keyboard
        etSearch.requestFocus()
        etSearch.postDelayed({
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT)
        }, 150)
    }

    private fun performSearch(query: String) {
        if (query.isEmpty()) {
            recyclerSearch.visibility = View.GONE
            layoutHint.visibility     = View.VISIBLE
            tvHintMessage.text        = "Type to search your notes"
            return
        }

        // Remove previous observer before adding a new one (query changes)
        viewModel.searchNotes(query).observe(this) { results ->
            if (results.isEmpty()) {
                recyclerSearch.visibility = View.GONE
                layoutHint.visibility     = View.VISIBLE
                tvHintMessage.text        = "No notes found for \"$query\""
            } else {
                layoutHint.visibility     = View.GONE
                recyclerSearch.visibility = View.VISIBLE
                adapter.submitList(results)
            }
        }
    }

    private fun openNoteEditor(note: NoteEntity) {
        val dialog = BottomSheetDialog(this)
        val view   = layoutInflater.inflate(R.layout.dialog_add_note, null)

        val dialogRoot = view.findViewById<LinearLayout>(R.id.dialogRoot)
        val etTitle    = view.findViewById<EditText>(R.id.etTitle)
        val etDesc     = view.findViewById<EditText>(R.id.etDesc)
        val btnClose   = view.findViewById<ImageButton>(R.id.btnClose)
        val btnDone    = view.findViewById<MaterialButton>(R.id.btnDone)
        val colorRow   = view.findViewById<LinearLayout>(R.id.colorPickerRow)

        var selectedColor = note.color
        etTitle.setText(note.title)
        etDesc.setText(note.description)
        applyNoteColor(dialogRoot, note.color)

        buildColorPicker(colorRow, selectedColor) { chosen ->
            selectedColor = chosen
            applyNoteColor(dialogRoot, chosen)
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        btnDone.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val desc  = etDesc.text.toString().trim()
            if (title.isNotEmpty() || desc.isNotEmpty()) {
                viewModel.updateNote(note.copy(title = title, description = desc, color = selectedColor))
            }
            dialog.dismiss()
        }

        dialog.setContentView(view)
        val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        sheet?.let {
            it.layoutParams?.height = ViewGroup.LayoutParams.MATCH_PARENT
            val behavior = BottomSheetBehavior.from(it)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
        }
        dialog.show()
    }

    private fun applyNoteColor(root: LinearLayout, hex: String) {
        val bg = if (hex.isEmpty()) Color.WHITE
                 else runCatching { Color.parseColor(hex) }.getOrDefault(Color.WHITE)
        root.setBackgroundColor(bg)
    }

    private fun buildColorPicker(container: LinearLayout, currentColor: String, onPick: (String) -> Unit) {
        val sizePx   = resources.getDimensionPixelSize(R.dimen.color_circle_size)
        val marginPx = resources.getDimensionPixelSize(R.dimen.color_circle_margin)
        noteColors.forEach { hex ->
            val v = View(this)
            v.layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).also {
                it.setMargins(marginPx, marginPx, marginPx, marginPx)
            }
            val fill  = if (hex.isEmpty()) Color.WHITE
                        else runCatching { Color.parseColor(hex) }.getOrDefault(Color.WHITE)
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(fill)
                val sel = hex == currentColor
                setStroke(if (sel) 4 else 2, if (sel) Color.DKGRAY else Color.LTGRAY)
            }
            v.background = shape
            v.setOnClickListener {
                onPick(hex)
                for (i in 0 until container.childCount) {
                    val child    = container.getChildAt(i)
                    val childHex = noteColors.getOrNull(i) ?: ""
                    val nowSel   = childHex == hex
                    (child.background as? GradientDrawable)?.setStroke(
                        if (nowSel) 4 else 2, if (nowSel) Color.DKGRAY else Color.LTGRAY
                    )
                }
            }
            container.addView(v)
        }
    }
}
