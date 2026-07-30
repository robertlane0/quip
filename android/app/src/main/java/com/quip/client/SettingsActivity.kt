package com.quip.client

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var etHostIp: AutoCompleteTextView
    private lateinit var spinnerLayout: Spinner
    private lateinit var tvStatus: TextView
    private lateinit var switchAutoConnect: SwitchCompat
    private lateinit var switchHistory: SwitchCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        etHostIp = findViewById(R.id.etHostIp)
        spinnerLayout = findViewById(R.id.spinnerLayout)
        tvStatus = findViewById(R.id.tvStatus)
        switchAutoConnect = findViewById(R.id.switchAutoConnect)
        switchHistory = findViewById(R.id.switchHistory)
        val btnBack: View = findViewById(R.id.btnBack)
        val btnConnect: Button = findViewById(R.id.btnConnect)
        val btnDisconnect: Button = findViewById(R.id.btnDisconnect)

        etHostIp.setText(AppState.hostIp)

        setupLayoutSpinner()
        refreshHistoryAdapter()
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
            refreshHistoryAdapter()
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
                AppState.selectedLayoutIndex = position
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
            refreshHistoryAdapter()
        }
    }

    /** Rebuilds the IP field's autofill suggestions from AppState.ipHistory. */
    private fun refreshHistoryAdapter() {
        if (!AppState.historyEnabled || AppState.ipHistory.isEmpty()) {
            val noSuggestions: ArrayAdapter<String>? = null
            etHostIp.setAdapter(noSuggestions)
            return
        }

        val adapter = object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_dropdown_item_1line,
            AppState.ipHistory
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(Color.WHITE)
                view.setBackgroundColor(ContextCompat.getColor(context, R.color.quip_bg_elevated))
                view.setPadding(28, 22, 28, 22)
                return view
            }
        }
        etHostIp.setAdapter(adapter)
    }

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
