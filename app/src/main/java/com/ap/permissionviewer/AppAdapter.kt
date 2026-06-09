package com.ap.permissionviewer

import android.content.Intent
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class AppAdapter(
    private val apps: List<AppInfo>,
    private val packageManager: PackageManager
) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        val appName: TextView = itemView.findViewById(R.id.appName)
        val packageNameText: TextView = itemView.findViewById(R.id.packageName)
        val headerLayout: View = itemView.findViewById(R.id.headerLayout)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]

        holder.appIcon.setImageDrawable(app.icon)
        holder.appName.text = app.name
        holder.packageNameText.text = app.packageName

        holder.headerLayout.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, AppDetailsActivity::class.java).apply {
                putExtra("PACKAGE_NAME", app.packageName)
            }
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = apps.size
}
