/*
 * Copyright © 2019. Daniel Schaal <daniel@schaal.email>
 *
 * This file is part of ocreader.
 *
 * ocreader is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ocreader is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Foobar.  If not, see <http://www.gnu.org/licenses/>.
 */
package email.schaal.ocreader.view

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import email.schaal.ocreader.R
import email.schaal.ocreader.database.model.*
import email.schaal.ocreader.databinding.ListDividerBinding
import email.schaal.ocreader.databinding.ListFolderBinding
import io.realm.Realm
import java.util.*

class FoldersAdapter(context: Context, private var folders: List<TreeItem>?, defaultTopFolders: Collection<TreeItem>, private val clickListener: TreeItemClickListener?) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val topFolders: MutableList<TreeItem>
    private var selectedTreeItemId = AllUnreadFolder.ID

    fun setSelectedTreeItemId(id: Long) {
        selectedTreeItemId = id
        notifyDataSetChanged()
    }

    private class DividerTreeItem(private val name: String) : TreeItem {
        override fun getId(): Long {
            return 0
        }

        override fun getName(): String {
            return name
        }

        override fun getCount(realm: Realm): Int {
            return 0
        }

        override fun canLoadMore(): Boolean {
            return false
        }

        override fun getFeeds(realm: Realm, onlyUnread: Boolean): List<Feed> {
            return emptyList()
        }

        override fun getItems(realm: Realm, onlyUnread: Boolean): List<Item> {
            return emptyList()
        }

    }

    fun updateFolders(folders: List<TreeItem>?) {
        this.folders = folders
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == R.id.viewtype_item) {
            FolderViewHolder(ListFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false), clickListener)
        } else DividerViewHolder(ListDividerBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemViewType(position: Int): Int {
        val treeItem = getTreeItem(position)
        return if (treeItem is DividerTreeItem) R.id.viewtype_divider else R.id.viewtype_item
    }

    private fun getTreeItem(position: Int): TreeItem? {
        return if (position >= topFolders.size) if (folders != null) folders!![position - topFolders.size] else null else topFolders[position]
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is FolderViewHolder) holder.bind(getTreeItem(position), selectedTreeItemId) else if (holder is DividerViewHolder) holder.bind(getTreeItem(position)!!)
    }

    override fun getItemCount(): Int {
        return topFolders.size + if (folders != null) folders!!.size else 0
    }

    interface TreeItemClickListener {
        fun onTreeItemClick(treeItem: TreeItem)
    }

    private class DividerViewHolder internal constructor(private val binding: ListDividerBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(treeItem: TreeItem) {
            binding.textViewDivider.text = treeItem.name
        }

    }

    class FolderViewHolder internal constructor(private val binding: ListFolderBinding, private val clickListener: TreeItemClickListener?) : RecyclerView.ViewHolder(binding.root) {
        fun bind(folder: TreeItem?, selectedTreeItemId: Long) {
            if (folder != null) {
                itemView.isSelected = folder.id == selectedTreeItemId
                itemView.setOnClickListener { clickListener?.onTreeItemClick(folder) }
                if (folder is TreeIconable) binding.imageviewFavicon.setImageResource((folder as TreeIconable).icon) else binding.imageviewFavicon.setImageResource(R.drawable.ic_feed_icon)
                binding.textViewTitle.text = folder.name
            }
        }

        private fun setSelected(selected: Boolean) {
            var backgroundResource = R.drawable.item_background
            if (!selected) {
                val attrs = intArrayOf(R.attr.selectableItemBackground)
                val typedArray = itemView.context.obtainStyledAttributes(attrs)
                backgroundResource = typedArray.getResourceId(0, 0)
                typedArray.recycle()
            }
            itemView.setBackgroundResource(backgroundResource)
        }

    }

    init {
        topFolders = ArrayList(defaultTopFolders.size + 1)
        topFolders.addAll(defaultTopFolders)
        topFolders.add(DividerTreeItem(context.getString(R.string.folder)))
        setHasStableIds(true)
    }
}