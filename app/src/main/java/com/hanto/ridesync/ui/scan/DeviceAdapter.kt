package com.hanto.ridesync.ui.scan

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hanto.ridesync.ble.scanner.ScannedDevice
import com.hanto.ridesync.databinding.ItemDeviceBinding

class DeviceAdapter(
    private val onDeviceClick: (ScannedDevice) -> Unit
) : ListAdapter<ScannedDevice, DeviceAdapter.DeviceViewHolder>(DeviceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DeviceViewHolder(private val binding: ItemDeviceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(device: ScannedDevice) {
            binding.apply {
                tvName.text = device.name
                tvAddress.text = device.address
                tvRssi.text = "${device.rssi} dBm"

                root.setOnClickListener { onDeviceClick(device) }
            }
        }
    }

    class DeviceDiffCallback : DiffUtil.ItemCallback<ScannedDevice>() {
        override fun areItemsTheSame(oldItem: ScannedDevice, newItem: ScannedDevice): Boolean {
            return oldItem.address == newItem.address // MAC 주소로 동일 기기 식별
        }

        override fun areContentsTheSame(oldItem: ScannedDevice, newItem: ScannedDevice): Boolean {
            return oldItem == newItem // 데이터 클래스 비교 (RSSI 등 변경 확인)
        }
    }
}