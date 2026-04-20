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
import java.text.NumberFormat
import java.util.Locale

class JobListAdapter(
    private val onClick: (Job) -> Unit,
    private val onLongClick: (Job) -> Unit
) : ListAdapter<Job, JobListAdapter.JobViewHolder>(JobDiff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JobViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_job, parent, false)
        return JobViewHolder(view)
    }

    override fun onBindViewHolder(holder: JobViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class JobViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.textJobName)
        private val rateText: TextView = itemView.findViewById(R.id.textJobRate)
        private val bonusesText: TextView = itemView.findViewById(R.id.textJobBonuses)
        private val captionText: TextView = itemView.findViewById(R.id.textJobCaption)
        private val nextIcon: ImageView = itemView.findViewById(R.id.imageNext)
        private val numberFormatter = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = 0
        }

        fun bind(job: Job) {
            nameText.text = job.name
            rateText.text = itemView.context.getString(
                R.string.job_rate_badge,
                numberFormatter.format(job.hourlyRate)
            )
            bonusesText.text = itemView.context.getString(
                R.string.job_bonuses_summary,
                numberFormatter.format(job.nightBonus),
                numberFormatter.format(job.saturdayBonus),
                numberFormatter.format(job.sundayBonus),
                numberFormatter.format(job.holidayBonus)
            )
            captionText.text = itemView.context.getString(R.string.job_item_hint)
            nextIcon.contentDescription = job.name
            itemView.setOnClickListener { onClick(job) }
            itemView.setOnLongClickListener {
                onLongClick(job)
                true
            }
        }
    }

    object JobDiff : DiffUtil.ItemCallback<Job>() {
        override fun areItemsTheSame(oldItem: Job, newItem: Job) = oldItem.jobId == newItem.jobId
        override fun areContentsTheSame(oldItem: Job, newItem: Job) = oldItem == newItem
    }
}
