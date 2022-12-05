package io.github.rexmtorres.android.swipereveallayout.demo.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import io.github.rexmtorres.android.swipereveallayout.demo.adapter.GridAdapter
import io.github.rexmtorres.android.swipereveallayout.demo.databinding.FragmentGridDemoBinding

class GridDemoFragment : Fragment() {
    private lateinit var binding: FragmentGridDemoBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentGridDemoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupGrid()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        // Only if you need to restore open/close state when the orientation is changed.
        (binding.gridView.adapter as? GridAdapter)?.saveStates(outState)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        // Only if you need to restore open/close state when the orientation is changed.
        savedInstanceState?.also { state ->
            (binding.gridView.adapter as? GridAdapter)?.restoreStates(state)
        }
    }

    private fun setupGrid() {
        binding.gridView.adapter = GridAdapter(
            requireContext(),
            MutableList(20) {
                "View $it"
            }
        )
    }
}
