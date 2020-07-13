package com.example.hivenative

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isInvisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.fragment_item_list.view.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect


/**
 * A fragment representing a list of Items.
 */
class PropertyFragment(private val showBack:Boolean=true, val hiveName:String="Android Hive") : Fragment() {
    private val hive = Hive(hiveName)
    private var hiveJob: Job? = null

    private val propertyAdapter = MyPropertyRecyclerViewAdapter(hive.propertyList)
    private var peerAdapter:ArrayAdapter<String>? = null
    private var peerList:MutableList<String> = mutableListOf()

    private suspend fun hiveMessages() = withContext(Dispatchers.IO) {
        if(!hive.connected) {

            propertyAdapter.onItemRemoved = {
                confirmDelete(it){removed_index ->
                    propertyAdapter.notifyItemRemoved(removed_index)
                }
            }
            propertyAdapter.onItemEdit = {
                println("<<<< EDIT: $it")
                editDialog(it)
            }

            hive.peersChanged = {
                GlobalScope.launch {
                    peerList.clear()
                    peerList.addAll( hive.peersList.filter { it != hiveName })
                    withContext(Dispatchers.Main) {
                        peerAdapter?.notifyDataSetChanged()
                    }
                }
            }

            // localhost for machine that android emulator is running on
            hive.connect("10.0.2.2",3000).collect{
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
    private fun editDialog(prop:Hive.Property?){
        AlertDialog.Builder(this.requireContext())
            .setTitle("Edit Property")
            .setMessage(prop?.value.toString())
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) {_dialog, _whichButton ->
                println("<<<< Save property!!")
            }
            .show()
    }

    private fun confirmDelete(msg:String, done:(Int)->Unit){
        AlertDialog.Builder(this.requireContext())
            .setTitle("Delete Property")
            .setMessage("Do you really want to delete \"$msg\"?")
            .setPositiveButton(android.R.string.yes) { _dialog, _whichButton ->
                val index = hive.deleteProperty(msg)
                done(index)
            }
            .setNegativeButton(android.R.string.no, null)
            .show()
    }

    private fun onPropertyReceived(p:PropType){
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
        val view = inflater.inflate(R.layout.fragment_item_list, container, false)
        val recView = view.list


        // Set the adapter
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
        this.peerAdapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1, peerList)
        view.peers_spinner.adapter =  peerAdapter
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if(showBack) {
            view.findViewById<Button>(R.id.button_second).setOnClickListener {
                findNavController().navigate(R.id.action_PropertiesFragment_to_FirstFragment)
            }
        } else {
            view.button_second.visibility = View.INVISIBLE
        }
    }

    companion object {

        // TODO: Customize parameter argument names
        const val ARG_COLUMN_COUNT = "column-count"

        // TODO: Customize parameter initialization
        @JvmStatic
        fun newInstance(columnCount: Int) =
            PropertyFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_COLUMN_COUNT, columnCount)
                }
            }
    }
}