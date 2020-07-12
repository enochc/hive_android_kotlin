package com.example.hivenative


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.navigation.fragment.findNavController
import kotlinx.android.synthetic.main.fragment_composite.view.*


/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class CompositeFragment : Fragment() {

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_composite, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.button_composite_previous.setOnClickListener {
            findNavController().navigate(R.id.action_CompositeFragment_to_FirstFragment)
        }

        childFragmentManager.beginTransaction().apply {
            add(R.id.child_fragment_container1, PropertyFragment(false, "Android Comp One"))
            commit()
        }
        childFragmentManager.beginTransaction().apply {
            add(R.id.child_fragment_container2, PropertyFragment(false, "Android Comp Two"))
            commit()
        }

    }
}