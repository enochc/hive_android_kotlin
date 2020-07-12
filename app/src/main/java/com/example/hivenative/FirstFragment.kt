package com.example.hivenative

import android.content.ClipData
import android.content.ClipDescription
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.Button
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import kotlinx.android.synthetic.main.fragment_first.*
import kotlinx.android.synthetic.main.fragment_first.view.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect


/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {





    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment

        return inflater.inflate(R.layout.fragment_first, container, false)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.button_first).setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_PropertiesFragment)
        }
        view.button_second.setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_CompositeFragment)
        }

        a_button.setOnTouchListener { v, event ->
            when(event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.performClick()

                    val item = ClipData.Item("aaaaa" as CharSequence)
                    val mimeTypes =
                        arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN)

                    val dragData = ClipData("aaaaa", mimeTypes, item)
                    val myShadow = View.DragShadowBuilder(a_button)
                    v.startDragAndDrop(dragData,myShadow,null,0)

                }


            }
            true
        }

        a_button.setOnDragListener {  v, event ->

//            when (event.action) {
//                DragEvent.ACTION_DRAG_STARTED -> println("<< started")
//                DragEvent.ACTION_DRAG_ENTERED -> println("<< entered")
//            }
            true
        }

    }




}

