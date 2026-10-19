package com.msebenzi.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.msebenzi.app.data.AppNotification
import com.msebenzi.app.databinding.ItemNotificationBinding
import com.msebenzi.app.notify.NotificationHelper

class NotificationAdapter(private val onClick: (AppNotification) -> Unit) :
    ListAdapter<AppNotification, NotificationAdapter.NotificationViewHolder>(NotificationDiff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NotificationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class NotificationViewHolder(private val binding: ItemNotificationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(notification: AppNotification) {
            binding.textType.text = NotificationHelper.titleFor(binding.root.context, notification.type).uppercase()
            binding.textMessage.text = notification.message
            binding.unreadDot.visibility = if (notification.isRead) View.INVISIBLE else View.VISIBLE
            binding.root.setOnClickListener { onClick(notification) }
        }
    }

    private class NotificationDiff : DiffUtil.ItemCallback<AppNotification>() {
        override fun areItemsTheSame(oldItem: AppNotification, newItem: AppNotification) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: AppNotification, newItem: AppNotification) = oldItem == newItem
    }
}
