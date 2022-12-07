package app.habitap.swipereveallayout.demo.adapter

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import app.habitap.swipereveallayout.ViewBinderHelper
import app.habitap.swipereveallayout.demo.databinding.ListItemBinding
import mu.KotlinLogging

class RecyclerAdapter(
    private val dataSet: MutableList<String>
) : RecyclerView.Adapter<RecyclerAdapter.ViewHolder>() {
    class ViewHolder(
        private val adapter: RecyclerAdapter,
        private val binding: ListItemBinding,
        private val dataSet: MutableList<String>
    ) : RecyclerView.ViewHolder(binding.root) {
        private val logger = KotlinLogging.logger {}

        val swipeLayout = binding.swipeLayout

        fun bind(data: String) {
            binding.apply {
                text.text = data

                frontLayout.setOnClickListener {
                    val displayText = "$data clicked"

                    Toast.makeText(binding.root.context, displayText, Toast.LENGTH_SHORT).show()
                    logger.debug { displayText }
                }

                deleteLayout.setOnClickListener {
                    val position = adapterPosition

                    dataSet.removeAt(position)
                    adapter.notifyItemRemoved(position)
                }
            }
        }
    }

    private val binderHelper = ViewBinderHelper().apply {
        // Open only 1 row at a time.
        setOpenOnlyOne(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return ViewHolder(this, binding, dataSet)
    }

    override fun getItemCount(): Int = dataSet.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (position in 0 until dataSet.size) {
            val data = dataSet[position]

            // Bind your data here
            holder.bind(data)

            // Use ViewBindHelper to restore and save the open/close state of the SwipeRevealView
            // put an unique string id as value, can be any string which uniquely define the data
            binderHelper.bind(holder.swipeLayout, data)
        }
    }

    /**
     * Only if you need to restore open/close state when the orientation is changed.
     * Call this method in [android.app.Activity.onSaveInstanceState].
     */
    fun saveStates(outState: Bundle) {
        binderHelper.saveStates(outState)
    }

    /**
     * Only if you need to restore open/close state when the orientation is changed.
     * Call this method in [android.app.Activity.onRestoreInstanceState].
     */
    fun restoreStates(inState: Bundle) {
        binderHelper.restoreStates(inState)
    }
}
