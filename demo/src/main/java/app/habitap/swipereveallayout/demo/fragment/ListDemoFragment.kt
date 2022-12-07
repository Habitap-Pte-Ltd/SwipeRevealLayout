package app.habitap.swipereveallayout.demo.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import app.habitap.swipereveallayout.demo.adapter.ListAdapter
import app.habitap.swipereveallayout.demo.databinding.FragmentListDemoBinding

class ListDemoFragment : Fragment() {
    private lateinit var binding: FragmentListDemoBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentListDemoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupGrid()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        // Only if you need to restore open/close state when the orientation is changed.
        (binding.listView.adapter as? ListAdapter)?.saveStates(outState)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        // Only if you need to restore open/close state when the orientation is changed.
        savedInstanceState?.also { state ->
            (binding.listView.adapter as? ListAdapter)?.restoreStates(state)
        }
    }

    private fun setupGrid() {
        binding.listView.adapter = ListAdapter(
            requireContext(),
            MutableList(20) {
                "View $it"
            }
        )
    }
}
