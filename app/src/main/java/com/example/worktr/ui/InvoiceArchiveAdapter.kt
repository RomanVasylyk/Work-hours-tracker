package com.example.worktr.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.worktr.R
import com.example.worktr.data.InvoiceRecord
import com.example.worktr.databinding.ItemInvoiceRecordBinding
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class InvoiceArchiveAdapter(
    private val monthLabels: List<String>,
    private val onOpen: (InvoiceRecord) -> Unit,
    private val onShare: (InvoiceRecord) -> Unit,
    private val onRecreate: (InvoiceRecord) -> Unit
) : ListAdapter<InvoiceRecord, InvoiceArchiveAdapter.InvoiceViewHolder>(Diff) {

    private val locale = Locale("sk", "SK")
    private val numberFormat = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    private val dateFormatter = DateTimeFormatter.ofPattern("d.M.yyyy", locale)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InvoiceViewHolder {
        val binding = ItemInvoiceRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return InvoiceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: InvoiceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class InvoiceViewHolder(
        private val binding: ItemInvoiceRecordBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(invoice: InvoiceRecord) {
            val context = binding.root.context
            val month = monthLabels.getOrNull(invoice.periodMonth - 1).orEmpty()
            val createdDate = Instant.ofEpochMilli(invoice.createdAtMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            binding.textInvoiceNumber.text = context.getString(R.string.invoice_archive_number, invoice.invoiceNumber)
            binding.textInvoiceCustomer.text = invoice.customerName
            binding.textInvoiceMeta.text = context.getString(
                R.string.invoice_archive_meta,
                month,
                invoice.periodYear,
                numberFormat.format(invoice.totalAmount),
                invoice.currency,
                createdDate.format(dateFormatter)
            )
            binding.buttonOpenInvoice.setOnClickListener { onOpen(invoice) }
            binding.buttonShareInvoice.setOnClickListener { onShare(invoice) }
            binding.buttonRecreateInvoice.setOnClickListener { onRecreate(invoice) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<InvoiceRecord>() {
        override fun areItemsTheSame(oldItem: InvoiceRecord, newItem: InvoiceRecord): Boolean =
            oldItem.invoiceId == newItem.invoiceId

        override fun areContentsTheSame(oldItem: InvoiceRecord, newItem: InvoiceRecord): Boolean =
            oldItem == newItem
    }
}
