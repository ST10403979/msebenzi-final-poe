package com.msebenzi.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.msebenzi.app.data.Rating
import com.msebenzi.app.databinding.ItemRatingBinding

class RatingAdapter(private var ratings: List<Rating>) :
    RecyclerView.Adapter<RatingAdapter.RatingViewHolder>() {

    fun updateData(newRatings: List<Rating>) {
        ratings = newRatings
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RatingViewHolder {
        val binding = ItemRatingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RatingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RatingViewHolder, position: Int) {
        holder.bind(ratings[position])
    }

    override fun getItemCount(): Int = ratings.size

    class RatingViewHolder(private val binding: ItemRatingBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(rating: Rating) {
            binding.textStars.text = "★".repeat(rating.score) + "☆".repeat(5 - rating.score)
            binding.textRaterName.text = rating.rater?.let { "— ${it.name}" } ?: ""
            if (rating.comment.isNullOrBlank()) {
                binding.textComment.visibility = View.GONE
            } else {
                binding.textComment.visibility = View.VISIBLE
                binding.textComment.text = rating.comment
            }
        }
    }
}
