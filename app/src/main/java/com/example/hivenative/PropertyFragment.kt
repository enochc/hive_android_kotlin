package com.example.hivenative

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.hivenative.dummy.DummyContent
import kotlinx.android.synthetic.main.fragment_item_list.view.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/**
 * A fragment representing a list of Items.
 */
class PropertyFragment : Fragment() {
    private val hive = Hive()
    private var hiveJob: Job? = null

    private val propertyAdapter = MyPropertyRecyclerViewAdapter(hive.propertyList)

    suspend fun hiveMessages() = withContext(Dispatchers.IO) {
        if(!hive.connected) {
            // localhost for machine that android emulator is running on
            hive.connect("10.0.2.2",3000).collect{
                var propVal = it.property.value
                debug("<< received: ${it.name} = $propVal")
                val position = hive.propertyList.size -1

                // update value when it changes
                it.property.connect {
                    GlobalScope.launch {
                        withContext(Dispatchers.Main) {
                            propertyAdapter.notifyItemChanged(position)
                        }
                    }
                }

                debug("added $it")

                // Show the new value on the list
                withContext(Dispatchers.Main) {
                    propertyAdapter.notifyItemInserted(hive.propertyList.size)
                }
            }
        }
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
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<Button>(R.id.button_second).setOnClickListener {
            findNavController().navigate(R.id.action_PropertiesFragment_to_FirstFragment)
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