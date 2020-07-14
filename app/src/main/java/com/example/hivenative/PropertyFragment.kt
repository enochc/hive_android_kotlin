package com.example.hivenative

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.hivenative.databinding.EditPropertyBinding
import com.example.hivenative.databinding.FragmentItemListBinding
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import kotlinx.android.synthetic.main.fragment_item_list.view.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex


/**
 * A fragment representing a list of Items.
 */
class PropertyFragment(
    private val showBack: Boolean = true,
    val hiveName: String = "Android Hive"
) : Fragment() {
    private val hive = Hive(hiveName)
    private var hiveJob: Job? = null

    private val propertyAdapter = MyPropertyRecyclerViewAdapter(hive.propertyList)
    private var peerAdapter: ArrayAdapter<String>? = null
    private var peerList: MutableList<String> = mutableListOf()
    private val peerLock: Mutex = Mutex()
    private var peerMessageButton: Button? = null
    private var frag_binding: FragmentItemListBinding? = null
    private var prop_edit_binding: EditPropertyBinding? = null

    private suspend fun hiveMessages() = withContext(Dispatchers.IO) {
        if (!hive.connected) {

            launch(Dispatchers.Main) {
                hive.peerMessages().collect {
                    frag_binding?.peerMessageFrom = it
                }
            }

            propertyAdapter.onItemRemoved = {
                confirmDelete(it) { removed_index ->
                    propertyAdapter.notifyItemRemoved(removed_index)
                }
            }
            propertyAdapter.onItemEdit = {
                editDialog(it)
            }

            hive.peersChanged = {
                launch(Dispatchers.Default) {
                    peerLock.lock()
                    peerList.clear()
                    peerList.addAll(hive.peersList.filter { it != hiveName })
                    withContext(Dispatchers.Main) {
                        peerAdapter?.notifyDataSetChanged()
                    }
                    peerLock.unlock()

                    // update the send to peer button
                    withContext(Dispatchers.Main) {
                        peerMessageButton?.isEnabled = peerList.size > 0
                    }
                }
            }

            // localhost for machine that android emulator is running on
            hive.connect("10.0.2.2", 3000).collect {
                onPropertyReceived(it)

                // Show the new value on the list
                if (it.doRemove == null) {
                    withContext(Dispatchers.Main) {
                        propertyAdapter.notifyItemInserted(hive.propertyList.size)
                    }
                } else {
                    // Removed from another client and came over the hive stream
                    withContext(Dispatchers.Main) {
                        propertyAdapter.notifyItemRemoved(it.doRemove)
                    }
                }
            }

        }
    }

    private fun editDialog(prop: PropType) {
        val view = prop_edit_binding?.root
        val group = view?.parent as ViewGroup?
        group?.removeView(view)
        prop_edit_binding?.prop = prop

        AlertDialog.Builder(this.requireContext())
            .setTitle("Edit Property")
            .setView(prop_edit_binding?.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val v: Any? = if (prop.isBool()) {
                    view?.findViewById<MaterialCheckBox>(R.id.prop_bool_value)?.isChecked
                } else {
                    view?.findViewById<TextInputEditText>(R.id.prop_value)?.text.toString()
                }
                hive.updateProperty(prop.name, v)

            }
            .show()
    }

    private fun confirmDelete(msg: String, done: (Int) -> Unit) {
        AlertDialog.Builder(this.requireContext())
            .setTitle("Delete Property")
            .setMessage("Do you really want to delete \"$msg\"?")
            .setPositiveButton(android.R.string.yes) { _, _ ->
                val index = hive.deleteProperty(msg)
                done(index)
            }
            .setNegativeButton(android.R.string.no, null)
            .show()
    }

    private fun onPropertyReceived(p: PropType) {
        val position = hive.propertyList.size - 1

        // update value when it changes
        p.property.connect {
            GlobalScope.launch {
                withContext(Dispatchers.Main) {
                    propertyAdapter.notifyItemChanged(position)
                }
            }
        }
        debug("added $p")
    }

    override fun onPause() {
        super.onPause()
        hive.disconnect()
        hiveJob?.cancel()
    }

    override fun onResume() {
        super.onResume()

        hiveJob = viewLifecycleOwner.lifecycleScope.launch {
            hiveMessages()
        }
    }

    private var columnCount = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            columnCount = it.getInt(ARG_COLUMN_COUNT)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        prop_edit_binding = EditPropertyBinding.inflate(inflater, container, false)
        frag_binding = FragmentItemListBinding.inflate(inflater, container, false)
        frag_binding?.back = showBack

        return frag_binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Set the adapter
        val recView = view.list
        if (recView is RecyclerView) {
            with(recView) {
                layoutManager = when {
                    columnCount <= 1 -> LinearLayoutManager(context)
                    else -> GridLayoutManager(context, columnCount)
                }
                adapter = propertyAdapter
            }
            debug("items: ${recView.adapter?.itemCount}")
        }
        this.peerAdapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, peerList)
        view.peers_spinner.adapter = peerAdapter

        if (showBack) {
            view.findViewById<Button>(R.id.button_second).setOnClickListener {
                findNavController().navigate(R.id.action_PropertiesFragment_to_FirstFragment)
            }
        }
        peerMessageButton = view.peer_message_btn
        peerMessageButton?.setOnClickListener {
            val name = view.peers_spinner.selectedItem.toString()
            val msg = view.peer_message.text.toString()
            println("<<<< SEND TO PEER: $name = $msg")
            if (msg.isNotEmpty()) {
                hive.writeToPeer(name, msg)
            }
            activity?.hideKeyboard(view)
            view.peer_message.setText("")
        }
        peerMessageButton?.isEnabled = peerList.size > 0
    }

    companion object {
        //
//        // TODO: Customize parameter argument names
        const val ARG_COLUMN_COUNT = "column-count"
//
//        // TODO: Customize parameter initialization
//        @JvmStatic
//        fun newInstance(columnCount: Int) =
//            PropertyFragment().apply {
//                arguments = Bundle().apply {
//                    putInt(ARG_COLUMN_COUNT, columnCount)
//                }
//            }
    }
}

fun Context.hideKeyboard(view: View) {
    val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
}