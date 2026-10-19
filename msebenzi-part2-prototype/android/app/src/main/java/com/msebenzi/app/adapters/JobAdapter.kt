package com.msebenzi.app.adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.msebenzi.app.R
import com.msebenzi.app.data.Job
import com.msebenzi.app.data.ServiceCatalog
import com.msebenzi.app.databinding.ItemJobCardBinding
import com.msebenzi.app.util.ImageUtils.loadMedia
import java.util.Locale

/**
 * Shows each job as a "transparent job card": picture, service, location, budget and status
 * are all visible before a worker opens the job (the Part 1 design concept).
 * Uses DiffUtil so only changed cards are redrawn when the feed refreshes.
 */
class JobAdapter(private val onClick: (Job) -> Unit) :
    ListAdapter<Job, JobAdapter.JobViewHolder>(JobDiff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JobViewHolder {
        val binding = ItemJobCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return JobViewHolder(binding)
    }

    override fun onBindViewHolder(holder: JobViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class JobViewHolder(private val binding: ItemJobCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(job: Job) {
            val context = binding.root.context
            val service = ServiceCatalog.find(job.category)

            binding.textTitle.text = job.title
            binding.textCategory.text = service?.let { context.getString(it.nameRes) }
                ?: job.category.replaceFirstChar { it.titlecase(Locale.getDefault()) }
            binding.textLocation.text = job.location
            binding.textBudget.text = String.format(Locale.getDefault(), "R%.2f", job.budget)
            binding.imageJob.loadMedia(job.photoMediaId, ServiceCatalog.imageFor(job.category))

            val (labelRes, colorRes) = when (job.status) {
                "open" -> R.string.status_open to R.color.status_open
                "accepted" -> R.string.status_accepted to R.color.status_accepted
                "completed" -> R.string.status_completed to R.color.status_completed
                else -> R.string.status_cancelled to R.color.status_cancelled
            }
            binding.textStatus.text = context.getString(labelRes)
            binding.textStatus.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(context, colorRes))

            binding.textPending.visibility = if (job.isPendingSync) View.VISIBLE else View.GONE
            binding.root.setOnClickListener { onClick(job) }
        }
    }

    private class JobDiff : DiffUtil.ItemCallback<Job>() {
        override fun areItemsTheSame(oldItem: Job, newItem: Job) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Job, newItem: Job) = oldItem == newItem
    }
}
