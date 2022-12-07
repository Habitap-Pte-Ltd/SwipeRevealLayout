package app.habitap.swipereveallayout.demo.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import app.habitap.swipereveallayout.demo.R
import app.habitap.swipereveallayout.demo.databinding.FragmentSwipeRevealLayoutDemoBinding

class SwipeRevealLayoutDemoFragment : Fragment() {
    private lateinit var _binding: FragmentSwipeRevealLayoutDemoBinding
    private val binding: FragmentSwipeRevealLayoutDemoBinding
        get() = _binding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSwipeRevealLayoutDemoBinding.inflate(inflater, container, false)
        setupActions()
        return binding.root
    }

    private fun setupActions() {
        binding.apply {
            buttonRecyclerView.setOnClickListener {
                it.findNavController().navigate(
                    R.id.action_swipeRevealLayoutDemoFragment_to_recyclerDemoFragment
                )
            }

            buttonGridView.setOnClickListener {
                it.findNavController().navigate(
                    R.id.action_swipeRevealLayoutDemoFragment_to_gridDemoFragment
                )
            }

            buttonListView.setOnClickListener {
                it.findNavController().navigate(
                    R.id.action_swipeRevealLayoutDemoFragment_to_listDemoFragment
                )
            }
        }
    }
}
