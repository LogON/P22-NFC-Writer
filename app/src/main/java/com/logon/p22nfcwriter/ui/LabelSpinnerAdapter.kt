package com.logon.p22nfcwriter.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.logon.p22nfcwriter.R
import com.logon.p22nfcwriter.model.LabelEntry
import com.logon.p22nfcwriter.model.Previews

/** Label list with a roll thumbnail. */
class LabelSpinnerAdapter(
    context: Context,
    private val items: List<LabelEntry>,
) : ArrayAdapter<LabelEntry>(context, R.layout.item_label, items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = bind(position, convertView, parent)

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View = bind(position, convertView, parent)

    private fun bind(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_label, parent, false)
        val item = items[position]
        view.findViewById<TextView>(R.id.text1).text = item.toString()
        view.findViewById<ImageView>(R.id.thumb).setImageBitmap(Previews.get(context, item.code))
        return view
    }
}
