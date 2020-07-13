package com.example.hivenative

import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView

import com.example.hivenative.dummy.DummyContent.DummyItem
import kotlinx.android.synthetic.main.fragment_item.view.*

/**
 * [RecyclerView.Adapter] that can display a [DummyItem].
 * TODO: Replace the implementation with code for your data type.
 */
class MyPropertyRecyclerViewAdapter(
    private val values: List<PropType>
) : RecyclerView.Adapter<MyPropertyRecyclerViewAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.fragment_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = values[position]
        holder.idView.text = item.name
        holder.editBtn.text = item.property.value.toString()
        holder.property = item.property
    }

    var onItemRemoved: ((i:String) -> Unit)? = null
    var onItemEdit: ((p:Hive.Property?) -> Unit)? = null

    override fun getItemCount(): Int = values.size


    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val idView: TextView = view.item_number
        val editBtn:Button = view.edit_btn
        var property:Hive.Property? = null

        init {
            view.btn_delete.setOnClickListener{
                onItemRemoved?.invoke(idView.text.toString())
            }
            editBtn.setOnClickListener {
                onItemEdit?.invoke(property)
            }
        }


        override fun toString(): String {
            return super.toString() + " '" + editBtn.text + "'"
        }
    }
}