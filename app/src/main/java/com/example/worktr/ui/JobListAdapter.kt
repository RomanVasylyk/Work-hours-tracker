package com.example.worktr.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.worktr.R
import com.example.worktr.data.Job

class JobListAdapter(
    private val onClick: (Job) -> Unit
) : ListAdapter<Job, JobListAdapter.JobViewHolder>(JobDiff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JobViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_job, parent, false)
        return JobViewHolder(view)
    }

    override fun onBindViewHolder(holder: JobViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class JobViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.textJobName)
        fun bind(job: Job) {
            nameText.text = job.name
            itemView.setOnClickListener { onClick(job) }
        }
    }

    object JobDiff : DiffUtil.ItemCallback<Job>() {
        override fun areItemsTheSame(oldItem: Job, newItem: Job): Boolean = oldItem.jobId == newItem.jobId
        override fun areContentsTheSame(oldItem: Job, newItem: Job): Boolean = oldItem == newItem
    }
}
