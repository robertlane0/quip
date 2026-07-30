package com.quip.client

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var etHostIp: EditText
    private lateinit var historyChipsRow: LinearLayout
    private lateinit var spinnerLayout: Spinner
    private lateinit var tvStatus: TextView
    private lateinit var switchAutoConnect: SwitchCompat
    private lateinit var switchHistory: SwitchCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        etHostIp = findViewById(R.id.etHostIp)
        historyChipsRow = findViewById(R.id.historyChipsRow)
        spinnerLayout = findViewById(R.id.spinnerLayout)
        tvStatus = findViewById(R.id.tvStatus)
        switchAutoConnect = findViewById(R.id.switchAutoConnect)
        switchHistory = findViewById(R.id.switchHistory)
        val btnBack: View = findViewById(R.id.btnBack)
        val btnConnect: Button = findViewById(R.id.btnConnect)
        val btnDisconnect: Button = findViewById(R.id.btnDisconnect)

        etHostIp.setText(AppState.hostIp)
        etHostIp.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                v.clearFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                true
            } else {
                false
            }
        }

        setupLayoutSpinner()
        refreshHistoryChips()
        setupPreferenceSwitches()
        refreshStatus()

        btnBack.setOnClickListener { finish() }

        btnConnect.setOnClickListener {
            val ip = etHostIp.text.toString().trim()
            if (ip.isEmpty()) {
                Toast.makeText(this, "Please enter a host IP address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val ok = AppState.connect(ip)
            refreshStatus()
            refreshHistoryChips()
            Toast.makeText(
                this,
                if (ok) "Connected to $ip:9876" else "Failed to initialize client for $ip",
                Toast.LENGTH_SHORT
            ).show()
        }

        btnDisconnect.setOnClickListener {
            AppState.disconnect()
            refreshStatus()
        }
    }

    // ---- Setup ----------------------------------------------------------------------

    private fun setupLayoutSpinner() {
        val adapter = object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            AppState.layouts.map { it.first }
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(Color.WHITE)
                view.setPadding(24, 20, 24, 20)
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.setTextColor(Color.WHITE)
                view.setBackgroundColor(ContextCompat.getColor(context, R.color.quip_bg_elevated))
                view.setPadding(24, 20, 24, 20)
                return view
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLayout.adapter = adapter
        spinnerLayout.setSelection(AppState.selectedLayoutIndex)

        spinnerLayout.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                AppState.setSelectedLayoutIndex(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupPreferenceSwitches() {
        switchAutoConnect.setOnCheckedChangeListener(null)
        switchAutoConnect.isChecked = AppState.autoConnectEnabled
        switchAutoConnect.setOnCheckedChangeListener { _, isChecked ->
            AppState.setAutoConnectEnabled(isChecked)
        }

        switchHistory.setOnCheckedChangeListener(null)
        switchHistory.isChecked = AppState.historyEnabled
        switchHistory.setOnCheckedChangeListener { _, isChecked ->
            AppState.setHistoryEnabled(isChecked)
            refreshHistoryChips()
        }
    }

    // ---- Recent-IP chips ---------------------------------------------------------------

    /** Rebuilds the inline "recent hosts" row from AppState.ipHistory — no popups involved. */
    private fun refreshHistoryChips() {
        historyChipsRow.removeAllViews()

        if (!AppState.historyEnabled || AppState.ipHistory.isEmpty()) {
            historyChipsRow.visibility = View.GONE
            return
        }
        historyChipsRow.visibility = View.VISIBLE

        val density = resources.displayMetrics.density
        val label = TextView(this).apply {
            text = "Recent:"
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, R.color.quip_text_hint))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (8 * density).toInt() }
        }
        historyChipsRow.addView(label)

        for (ip in AppState.ipHistory) {
            historyChipsRow.addView(buildHistoryChip(ip))
        }
    }

    @Suppress("ClickableViewAccessibility")
    private fun buildHistoryChip(ip: String): TextView {
        val density = resources.displayMetrics.density
        return TextView(this).apply {
            text = ip
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.quip_text_primary))
            setPadding(
                (14 * density).toInt(), (8 * density).toInt(),
                (14 * density).toInt(), (8 * density).toInt()
            )
            background = chipBackground(pressed = false)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (8 * density).toInt() }

            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.background = chipBackground(pressed = true)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        v.background = chipBackground(pressed = false)
                        v.performClick()
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        v.background = chipBackground(pressed = false)
                        true
                    }
                    else -> false
                }
            }
            setOnClickListener {
                etHostIp.setText(ip)
                etHostIp.setSelection(ip.length)
                etHostIp.requestFocus()
            }
        }
    }

    private fun chipBackground(pressed: Boolean): GradientDrawable {
        val density = resources.displayMetrics.density
        return GradientDrawable().apply {
            cornerRadius = 16f * density
            setColor(
                ContextCompat.getColor(
                    this@SettingsActivity,
                    if (pressed) R.color.quip_key_fill_pressed else R.color.quip_key_fill
                )
            )
            setStroke((1f * density).toInt(), ContextCompat.getColor(this@SettingsActivity, R.color.quip_key_stroke))
        }
    }

    // ---- Status --------------------------------------------------------------------

    private fun refreshStatus() {
        if (AppState.isConnected) {
            tvStatus.text = "Status: Connected to ${AppState.hostIp}:9876"
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.quip_status_ok))
        } else {
            tvStatus.text = "Status: Disconnected"
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.quip_status_warn))
        }
    }
}
