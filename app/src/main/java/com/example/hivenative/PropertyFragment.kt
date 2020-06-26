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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A fragment representing a list of Items.
 */
class PropertyFragment : Fragment() {
    private val hive = Hive()
    private var hiveJob: Job? = null

    private val propertyList: MutableList<PropType> = mutableListOf()
    private val propertyAdapter = MyPropertyRecyclerViewAdapter(propertyList)

    suspend fun hiveMessages() = withContext(Dispatchers.IO) {

        if(!hive.connected) {
            hive.connect("10.0.2.2",3000).collect{
                var propVal = it.second.value
//                println("<< received: ${it.first} = $propVal")
                if(!propertyList.contains(it)){
                    propertyList.add(it)
                    withContext(Dispatchers.Main) {
                        propertyAdapter.notifyDataSetChanged()
                    }
                    debug("added $it")
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