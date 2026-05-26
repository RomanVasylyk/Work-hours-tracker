package com.example.worktr.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.worktr.R
import com.example.worktr.data.InvoiceRecord
import com.example.worktr.data.Job
import com.example.worktr.databinding.ItemClientBinding
import java.text.NumberFormat
import java.util.Locale

class ClientsAdapter(
    private val onEdit: (ClientUiModel) -> Unit
) : ListAdapter<ClientUiModel, ClientsAdapter.ClientViewHolder>(Diff) {
    private val numberFormat = NumberFormat.getNumberInstance(Locale("sk", "SK")).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClientViewHolder {
        val binding = ItemClientBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ClientViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ClientViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ClientViewHolder(
        private val binding: ItemClientBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(model: ClientUiModel) {
            val context = binding.root.context
            binding.textClientJobName.text = model.job.name
            binding.textClientName.text = model.customerName
            binding.textClientRate.text = context.getString(
                R.string.client_rate_format,
                numberFormat.format(model.job.hourlyRate)
            )
            binding.textClientLastInvoice.text = model.lastInvoice?.let { invoice ->
                context.getString(
                    R.string.client_last_invoice,
                    invoice.invoiceNumber,
                    numberFormat.format(invoice.totalAmount),
                    invoice.currency
                )
            } ?: context.getString(R.string.client_no_invoice)
            binding.buttonEditClient.setOnClickListener { onEdit(model) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<ClientUiModel>() {
        override fun areItemsTheSame(oldItem: ClientUiModel, newItem: ClientUiModel): Boolean =
            oldItem.job.jobId == newItem.job.jobId

        override fun areContentsTheSame(oldItem: ClientUiModel, newItem: ClientUiModel): Boolean =
            oldItem == newItem
    }
}

data class ClientUiModel(
    val job: Job,
    val customerName: String,
    val lastInvoice: InvoiceRecord?
)
