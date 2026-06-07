package com.webscraper

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.webscraper.models.Product

class ScrapeResultAdapter : RecyclerView.Adapter<ScrapeResultAdapter.ViewHolder>() {

    private val items = mutableListOf<Product>()

    fun addProduct(product: Product) {
        items.add(product)
        notifyItemInserted(items.size - 1)
    }

    fun clear() {
        val size = items.size
        items.clear()
        notifyItemRangeRemoved(0, size)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvProductTitle)
        val tvPrice: TextView = view.findViewById(R.id.tvProductPrice)
        val imgProduct: ImageView = view.findViewById(R.id.imgProduct)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_product, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val product = items[position]
        holder.tvTitle.text = product.title
        if (product.price.isNotEmpty()) {
            holder.tvPrice.text = product.price
            holder.tvPrice.visibility = View.VISIBLE
        } else {
            holder.tvPrice.visibility = View.GONE
        }
        Glide.with(holder.itemView.context)
            .load(product.imageUrl)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_close_clear_cancel)
            .into(holder.imgProduct)
    }

    override fun getItemCount() = items.size
}
