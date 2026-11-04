package org.liberty.android.localreceiver

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.liberty.android.localreceiver.databinding.ItemCopyHistoryBinding
import java.text.DateFormat
import java.util.Date

class CopyHistoryAdapter(
    private val onItemClick: (CopyHistoryEntry) -> Unit,
    private val onQuickOpenClick: (CopyHistoryEntry) -> Unit
) : RecyclerView.Adapter<CopyHistoryAdapter.ViewHolder>() {

    private val items = mutableListOf<CopyHistoryEntry>()
    private val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

    fun submitList(newItems: List<CopyHistoryEntry>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCopyHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemCopyHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CopyHistoryEntry) {
            val context = binding.root.context
            binding.tvFileName.text = item.sourceName

            // Format timestamp and size
            val dateStr = dateFormat.format(Date(item.timestamp))
            val sizeStr = FileMetadataResolver.formatFileSize(item.sizeBytes)
            binding.tvMetadata.text = "$sizeStr • $dateStr"

            // Source and destination paths
            binding.tvSource.text = context.getString(R.string.history_source_prefix, item.sourceUri)
            binding.tvDestination.text = context.getString(R.string.history_dest_prefix, item.destinationDir)

            // Status handling
            when (item.status) {
                CopyHistoryEntry.Status.SUCCESS -> {
                    if (item.destinationUri == null) {
                        // File was deleted from storage
                        binding.ivStatus.setImageResource(R.drawable.ic_check_circle)
                        binding.ivStatus.imageTintList = ColorStateList.valueOf(
                            ContextCompat.getColor(context, R.color.status_deleted)
                        )
                        binding.tvStatusBadge.text = context.getString(R.string.history_file_deleted_badge)
                        binding.tvStatusBadge.setTextColor(
                            ContextCompat.getColor(context, R.color.status_deleted)
                        )
                        binding.tvErrorMessage.visibility = View.GONE
                        binding.btnQuickOpen.visibility = View.GONE
                    } else {
                        binding.ivStatus.setImageResource(R.drawable.ic_check_circle)
                        binding.ivStatus.imageTintList = null
                        binding.tvStatusBadge.text = context.getString(R.string.history_status_success)
                        binding.tvStatusBadge.setTextColor(
                            ContextCompat.getColor(context, R.color.status_success)
                        )
                        binding.tvErrorMessage.visibility = View.GONE
                        binding.btnQuickOpen.visibility = View.VISIBLE
                    }
                }
                CopyHistoryEntry.Status.FAILURE -> {
                    binding.ivStatus.setImageResource(R.drawable.ic_error_circle)
                    binding.ivStatus.imageTintList = null
                    binding.tvStatusBadge.text = context.getString(R.string.history_status_failure)
                    binding.tvStatusBadge.setTextColor(
                        ContextCompat.getColor(context, R.color.status_error)
                    )
                    binding.tvErrorMessage.visibility = if (item.errorMessage.isNullOrBlank()) {
                        View.GONE
                    } else {
                        binding.tvErrorMessage.text = item.errorMessage
                        View.VISIBLE
                    }
                    binding.btnQuickOpen.visibility = View.GONE
                }
                CopyHistoryEntry.Status.CANCELLED -> {
                    binding.ivStatus.setImageResource(R.drawable.ic_cancel_circle)
                    binding.ivStatus.imageTintList = null
                    binding.tvStatusBadge.text = context.getString(R.string.history_status_cancelled)
                    binding.tvStatusBadge.setTextColor(
                        ContextCompat.getColor(context, R.color.status_cancelled)
                    )
                    binding.tvErrorMessage.visibility = if (item.errorMessage.isNullOrBlank()) {
                        View.GONE
                    } else {
                        binding.tvErrorMessage.text = item.errorMessage
                        View.VISIBLE
                    }
                    binding.btnQuickOpen.visibility = View.GONE
                }
            }

            binding.cardHistoryItem.setOnClickListener {
                onItemClick(item)
            }

            binding.btnItemMenu.setOnClickListener {
                onItemClick(item)
            }

            binding.btnQuickOpen.setOnClickListener {
                onQuickOpenClick(item)
            }
        }
    }
}
