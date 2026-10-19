package com.msebenzi.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.msebenzi.app.data.ServiceCatalog
import com.msebenzi.app.databinding.ItemServiceBinding

/**
 * Horizontal gallery of every service Msebenzi offers, each with its own picture.
 * Tapping a card filters the job feed to that service; tapping it again clears the filter.
 */
class ServiceAdapter(private val onSelected: (String?) -> Unit) :
    RecyclerView.Adapter<ServiceAdapter.ServiceViewHolder>() {

    private val services = ServiceCatalog.all
    private var selectedKey: String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServiceViewHolder {
        val binding = ItemServiceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ServiceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ServiceViewHolder, position: Int) {
        holder.bind(services[position])
    }

    override fun getItemCount(): Int = services.size

    inner class ServiceViewHolder(private val binding: ItemServiceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(service: ServiceCatalog.Service) {
            binding.imageService.setImageResource(service.imageRes)
            binding.textServiceName.setText(service.nameRes)
            binding.cardService.isChecked = service.key == selectedKey
            binding.cardService.setOnClickListener {
                selectedKey = if (selectedKey == service.key) null else service.key
                notifyDataSetChanged() // only 8 cards, so a full rebind is the simplest correct option
                onSelected(selectedKey)
            }
        }
    }
}
