package com.logon.p22nfcwriter

import android.app.PendingIntent
import android.content.Intent
import android.content.res.Configuration
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.NfcA
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.logon.p22nfcwriter.model.CatalogSource
import com.logon.p22nfcwriter.model.LabelCatalog
import com.logon.p22nfcwriter.model.LabelEntry
import com.logon.p22nfcwriter.model.Previews
import com.logon.p22nfcwriter.ui.LabelSpinnerAdapter
import com.logon.p22nfcwriter.tag.TagFormat

class MainActivity : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var tvInfo: TextView
    private lateinit var tvDecoded: TextView
    private lateinit var spinnerSize: Spinner
    private lateinit var spinnerDesign: Spinner
    private lateinit var etCode: EditText
    private lateinit var cbReadOnly: CheckBox

    private lateinit var tvSource: TextView
    private lateinit var ivPreview: ImageView

    private var catalog: List<LabelEntry> = emptyList()
    private var sizes: List<String> = emptyList()
    private var designs: List<LabelEntry> = emptyList()
    private var pendingCode: String? = null

    /** Status bar icon colour follows the theme (dark on light background and vice versa) + content insets. */
    private fun setupSystemBars() {
        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
            nightMode != Configuration.UI_MODE_NIGHT_YES
        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupSystemBars()

        tvInfo = findViewById(R.id.tv_info)
        tvDecoded = findViewById(R.id.tv_decoded)
        spinnerSize = findViewById(R.id.spinner_size)
        spinnerDesign = findViewById(R.id.spinner_design)
        etCode = findViewById(R.id.et_code)
        cbReadOnly = findViewById(R.id.cb_read_only)

        tvSource = findViewById(R.id.tv_source)
        ivPreview = findViewById(R.id.iv_preview)
        spinnerSize.onItemSelectedListener = onSelected { pos ->
            designs = catalog.filter { it.category == sizes[pos] }
            spinnerDesign.adapter = LabelSpinnerAdapter(this, designs)
            pendingCode?.let { code ->
                designs.indexOfFirst { it.code == code }.takeIf { it >= 0 }?.let { spinnerDesign.setSelection(it) }
            }
        }
        spinnerDesign.onItemSelectedListener = onSelected { pos ->
            pendingCode = null
            etCode.setText(designs[pos].code)
        }

        val (offline, source) = LabelCatalog.loadOffline(this)
        setCatalog(offline, source)
        refreshFromServer()

        etCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = showDecoded(s?.toString().orEmpty())
        })

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
    }

    /** Puts a new list into the spinners. If the selected code is in the new list, the selection is kept. */
    private fun setCatalog(entries: List<LabelEntry>, source: CatalogSource) {
        val keepCode = TagFormat.parseCode(etCode.text.toString())?.let { TagFormat.toHex(it) }
        catalog = entries
        sizes = entries.map { it.category }.distinct()
        tvSource.text = getString(
            when (source) {
                CatalogSource.SERVER -> R.string.catalog_server
                CatalogSource.CACHE -> R.string.catalog_cache
                CatalogSource.BUNDLED -> R.string.catalog_bundled
            },
            entries.size,
        )
        val keep = keepCode?.let { LabelCatalog.findByCode(entries, it) }
        pendingCode = keep?.code
        spinnerSize.adapter = ArrayAdapter(this, R.layout.item_spinner_simple, R.id.text1, sizes.map(::sizeTitle))
        val sizeIndex = sizes.indexOf(keep?.category ?: "14x40mm").takeIf { it >= 0 } ?: 0
        spinnerSize.setSelection(sizeIndex)
    }

    /** Catalog categories are English keys ("14x40mm", "Continuous 14mm"); only the tape name is localized. */
    private fun sizeTitle(category: String): String =
        if (category.startsWith(CONTINUOUS)) getString(R.string.size_continuous, category.removePrefix(CONTINUOUS))
        else category

    private fun refreshFromServer() {
        Thread {
            val result = runCatching { LabelCatalog.fetchOnline(applicationContext) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { setCatalog(it, CatalogSource.SERVER) }
                    .onFailure { tvSource.append("\n" + getString(R.string.catalog_offline, it.message ?: it.javaClass.simpleName)) }
            }
            // Previews of new labels that are not in the bundled set.
            val entries = result.getOrNull() ?: return@Thread
            if (Previews.downloadMissing(applicationContext, entries) > 0) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    (spinnerDesign.adapter as? LabelSpinnerAdapter)?.notifyDataSetChanged()
                    showDecoded(etCode.text.toString())
                }
            }
        }.start()
    }

    private fun onSelected(action: (Int) -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = action(position)
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    private fun describe(code: ByteArray): String {
        val d = TagFormat.decode(code)
        val entry = LabelCatalog.findByCode(catalog, TagFormat.toHex(code))
        val name = entry?.name ?: getString(R.string.unknown_label)
        return getString(R.string.decoded_info, name, d.widthMm, d.lengthMm, d.format, d.design)
    }

    private fun showDecoded(text: String) {
        val code = TagFormat.parseCode(text)
        tvDecoded.text = if (code == null) getString(R.string.invalid_code) else describe(code)
        val bmp = code?.let { Previews.get(this, TagFormat.toHex(it)) }
        ivPreview.setImageBitmap(bmp)
        ivPreview.visibility = if (bmp == null) View.GONE else View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        val intent = Intent(this, javaClass).apply { addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP) }
        // FLAG_UPDATE_CURRENT: without it Android may reuse an intent with stale Tag extras.
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val tag: Tag = (if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }) ?: return

        // Only NTAG203/213 (NfcA) are supported. A foreign tag is not a write error but a clear message.
        if (NfcA.get(tag) == null) {
            val msg = getString(R.string.unsupported_tag)
            tvInfo.text = msg
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            if (cbReadOnly.isChecked) {
                val code = readCode(tag)
                tvInfo.text = if (code == null) getString(R.string.not_roll_tag)
                else getString(R.string.read_done, TagFormat.toHex(code), describe(code))
            } else {
                val code = TagFormat.parseCode(etCode.text.toString())
                    ?: throw IllegalArgumentException(getString(R.string.invalid_code))
                writeCode(tag, code)
                Toast.makeText(this, R.string.success, Toast.LENGTH_SHORT).show()
                tvInfo.text = getString(R.string.writing_done)
            }
        } catch (e: Exception) {
            val reason = if (e is TagLostException) getString(R.string.tag_lost) else e.message ?: e.javaClass.simpleName
            val msg = "${getString(R.string.writing_failed)}: $reason"
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            tvInfo.text = msg
        }
    }

    private fun readPages4to7(nfcA: NfcA): ByteArray =
        nfcA.transceive(byteArrayOf(0x30, TagFormat.FIRST_PAGE.toByte())) // READ: 4 pages = 16 bytes

    private fun readCode(tag: Tag): ByteArray? {
        val nfcA = NfcA.get(tag)
        nfcA.connect()
        try {
            return TagFormat.codeFromRead(readPages4to7(nfcA))
        } finally {
            nfcA.close()
        }
    }

    private fun writeCode(tag: Tag, code: ByteArray) {
        val nfcA = NfcA.get(tag)
        nfcA.connect()
        try {
            for ((page, data) in TagFormat.pages(code)) {
                val cmd = byteArrayOf(0xA2.toByte(), page.toByte()) + data // WRITE
                val response = nfcA.transceive(cmd)
                if (response.isEmpty() || (response[0].toInt() and 0x0F) != 0x0A) {
                    throw Exception(getString(R.string.writing_failed_on_page, page))
                }
            }
            val readBack = readPages4to7(nfcA)
            val expected = TagFormat.PAGE4 + code
            if (readBack.size < 16 || !readBack.copyOfRange(0, 16).contentEquals(expected)) {
                throw Exception(getString(R.string.verify_failed, TagFormat.toHex(readBack)))
            }
        } finally {
            nfcA.close()
        }
    }

    private companion object {
        const val CONTINUOUS = "Continuous "
    }
}
