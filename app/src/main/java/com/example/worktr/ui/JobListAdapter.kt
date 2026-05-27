package com.example.worktr.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.worktr.R
import com.example.worktr.data.Job
import com.google.android.material.button.MaterialButton
import java.text.NumberFormat
import java.util.Locale

class JobListAdapter(
    private val onClick: (Job) -> Unit,
    private val onEdit: (Job) -> Unit
) : ListAdapter<Job, JobListAdapter.JobViewHolder>(JobDiff) {
    private var monthlySummaries: Map<Int, JobMonthSummary> = emptyMap()

    fun setMonthlySummaries(summaries: Map<Int, JobMonthSummary>) {
        monthlySummaries = summaries
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JobViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_job, parent, false)
        return JobViewHolder(view)
    }

    override fun onBindViewHolder(holder: JobViewHolder, position: Int) {
        val job = getItem(position)
        holder.bind(job, monthlySummaries[job.jobId])
    }

    inner class JobViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.textJobName)
        private val rateText: TextView = itemView.findViewById(R.id.textJobRate)
        private val bonusesText: TextView = itemView.findViewById(R.id.textJobBonuses)
        private val captionText: TextView = itemView.findViewById(R.id.textJobCaption)
        private val nextIcon: ImageView = itemView.findViewById(R.id.imageNext)
        private val editButton: MaterialButton = itemView.findViewById(R.id.buttonEditJob)
        private val numberFormatter = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = 0
        }
        private val hoursFormatter = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            maximumFractionDigits = 1
            minimumFractionDigits = 0
        }
        private val moneyFormatter = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = 2
        }

        fun bind(job: Job, monthSummary: JobMonthSummary?) {
            val summary = monthSummary ?: JobMonthSummary()
            nameText.text = job.name
            rateText.text = itemView.context.getString(
                R.string.job_rate_badge,
                numberFormatter.format(job.hourlyRate)
            )
            bonusesText.text = itemView.context.getString(
                R.string.job_month_summary,
                hoursFormatter.format(summary.hours),
                moneyFormatter.format(summary.salary)
            )
            captionText.visibility = View.GONE
            nextIcon.contentDescription = job.name
            itemView.setOnClickListener { onClick(job) }
            itemView.setOnLongClickListener {
                onEdit(job)
                true
            }
            editButton.setOnClickListener { onEdit(job) }
        }
    }

    object JobDiff : DiffUtil.ItemCallback<Job>() {
        override fun areItemsTheSame(oldItem: Job, newItem: Job) = oldItem.jobId == newItem.jobId
        override fun areContentsTheSame(oldItem: Job, newItem: Job) = oldItem == newItem
    }
}

data class JobMonthSummary(
    val hours: Double = 0.0,
    val salary: Double = 0.0
)
