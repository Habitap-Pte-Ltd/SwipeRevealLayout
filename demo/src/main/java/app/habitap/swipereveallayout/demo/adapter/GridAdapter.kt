package app.habitap.swipereveallayout.demo.adapter

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import app.habitap.swipereveallayout.SwipeRevealLayout
import app.habitap.swipereveallayout.ViewBinderHelper
import app.habitap.swipereveallayout.demo.R
import mu.KotlinLogging

class GridAdapter(
    context: Context,
    dataSet: MutableList<String>
) : ArrayAdapter<String>(context, R.layout.grid_item, dataSet) {
    class ViewHolder(
        private val adapter: GridAdapter,
        convertView: View
    ) {
        private val logger = KotlinLogging.logger {}

        val swipeLayout: SwipeRevealLayout = convertView.findViewById(R.id.swipe_layout)

        private val context = convertView.context
        private val text = convertView.findViewById<TextView>(R.id.text)
        private val frontLayout = convertView.findViewById<FrameLayout>(R.id.front_layout)
        private val deleteLayout = convertView.findViewById<FrameLayout>(R.id.delete_layout)

        fun bind(data: String) {
            text.text = data

            frontLayout.setOnClickListener {
                val displayText = "$data clicked"

                Toast.makeText(context, displayText, Toast.LENGTH_SHORT).show()
                logger.debug { displayText }
            }

            deleteLayout.setOnClickListener {
                adapter.remove(data)
            }
        }
    }

    private val inflater = LayoutInflater.from(context)

    private val binderHelper = ViewBinderHelper().apply {
        // Open only 1 row at a time.
        setOpenOnlyOne(true)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val (view, holder) = if (convertView == null) {
            val view = inflater.inflate(R.layout.grid_item, parent, false)

            val holder = ViewHolder(
                this,
                view
            ).also {
                view.tag = it // Store the ViewHolder in the view's tag property.
            }

            view to holder
        } else {
            convertView to (convertView.tag as ViewHolder) // Get the ViewHolder previously stored in the tag property.
        }

        getItem(position)?.also { item ->
            holder.bind(item)
            binderHelper.bind(holder.swipeLayout, item)
        }

        return view
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
