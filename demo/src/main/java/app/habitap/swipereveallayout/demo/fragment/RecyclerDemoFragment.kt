package app.habitap.swipereveallayout.demo.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import app.habitap.swipereveallayout.demo.adapter.RecyclerAdapter
import app.habitap.swipereveallayout.demo.databinding.FragmentRecyclerDemoBinding

class RecyclerDemoFragment : Fragment() {
    private lateinit var binding: FragmentRecyclerDemoBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentRecyclerDemoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupList()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        // Only if you need to restore open/close state when the orientation is changed.
        (binding.recyclerView.adapter as? RecyclerAdapter)?.saveStates(outState)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        // Only if you need to restore open/close state when the orientation is changed.
        savedInstanceState?.also { state ->
            (binding.recyclerView.adapter as? RecyclerAdapter)?.restoreStates(state)
        }
    }

    private fun setupList() {
        binding.recyclerView.adapter = RecyclerAdapter(
            MutableList(20) {
                "View $it"
            }
        )
    }
}
