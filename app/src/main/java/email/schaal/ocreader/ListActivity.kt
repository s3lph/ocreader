/*
 * Copyright (C) 2015-2016 Daniel Schaal <daniel@schaal.email>
 *
 * This file is part of OCReader.
 *
 * OCReader is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * OCReader is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OCReader.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package email.schaal.ocreader

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import androidx.preference.PreferenceManager
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.aboutlibraries.Libs.ActivityStyle
import com.mikepenz.aboutlibraries.LibsBuilder
import email.schaal.ocreader.R.string
import email.schaal.ocreader.database.FeedViewModel
import email.schaal.ocreader.database.FeedViewModel.FeedViewModelFactory
import email.schaal.ocreader.database.Queries
import email.schaal.ocreader.database.model.Feed
import email.schaal.ocreader.database.model.Item
import email.schaal.ocreader.database.model.TemporaryFeed
import email.schaal.ocreader.database.model.TreeItem
import email.schaal.ocreader.databinding.ActivityListBinding
import email.schaal.ocreader.service.SyncService
import email.schaal.ocreader.service.SyncType
import email.schaal.ocreader.view.DividerItemDecoration
import email.schaal.ocreader.view.FolderBottomSheetDialogFragment
import email.schaal.ocreader.view.FoldersAdapter.TreeItemClickListener
import email.schaal.ocreader.view.ItemViewHolder
import email.schaal.ocreader.view.LiveItemsAdapter

class ListActivity : RealmActivity(), ItemViewHolder.OnClickListener, OnRefreshListener, ActionMode.Callback, TreeItemClickListener {
    private var actionMode: ActionMode? = null
    private lateinit var binding: ActivityListBinding
    private lateinit var adapter: LiveItemsAdapter
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var feedViewModel: FeedViewModel
    private lateinit var preferenceChangeListener: OnSharedPreferenceChangeListener

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action != null && (action == SyncService.SYNC_STARTED || action == SyncService.SYNC_FINISHED)) {
                val syncType = SyncType.get(intent.getStringExtra(SyncService.EXTRA_TYPE))
                if (syncType != null) {
                    when (syncType) {
                        SyncType.LOAD_MORE -> if (action == SyncService.SYNC_FINISHED) { //todo: adapter.resetLoadMore();
                        }
                        SyncType.FULL_SYNC -> updateSyncStatus()
                        SyncType.SYNC_CHANGES_ONLY -> TODO()
                    }
                }
            }
        }
    }
    private fun updateSyncStatus() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val needsUpdate = Preferences.SYS_NEEDS_UPDATE_AFTER_SYNC.getBoolean(sharedPreferences)
        val syncRunning = Preferences.SYS_SYNC_RUNNING.getBoolean(sharedPreferences)
        if (needsUpdate) {
            feedViewModel.updateTemporaryFeed(PreferenceManager.getDefaultSharedPreferences(this), true)
            sharedPreferences.edit()
                    .putBoolean(Preferences.SYS_NEEDS_UPDATE_AFTER_SYNC.key, false).apply()
        }
        if (binding.swipeRefreshLayout != null) {
            binding.swipeRefreshLayout.isRefreshing = syncRunning
        }
        binding.bottomAppbar.menu.findItem(R.id.menu_sync).isEnabled = !syncRunning
        //todo: if(!syncRunning)
//    adapter.resetLoadMore();
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
    }

    override fun onResume() {
        super.onResume()
        updateSyncStatus()
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, SyncService.syncFilter)
    }

    override fun onStart() {
        super.onStart()
        if (!Preferences.hasCredentials(PreferenceManager.getDefaultSharedPreferences(this))) {
            startActivityForResult(Intent(this, LoginActivity::class.java), LoginActivity.REQUEST_CODE)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_list)
        setSupportActionBar(binding.toolbarLayout.toolbar)
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val menuItemShowOnlyUnread = binding.bottomAppbar.menu.findItem(R.id.menu_show_only_unread)
        menuItemShowOnlyUnread.isChecked = Preferences.SHOW_ONLY_UNREAD.getBoolean(preferences)
        binding.bottomAppbar.setNavigationIcon(R.drawable.ic_folder)
        binding.bottomAppbar.setNavigationOnClickListener { v: View? ->
            val fm = supportFragmentManager
            val bottomSheetDialogFragment = FolderBottomSheetDialogFragment()
            bottomSheetDialogFragment.setTreeItemClickListener(this)
            bottomSheetDialogFragment.show(fm, null)
        }
        binding.bottomAppbar.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.menu_settings -> {
                    val settingsIntent = Intent(this@ListActivity, SettingsActivity::class.java)
                    startActivity(settingsIntent)
                    return@setOnMenuItemClickListener true
                }
                R.id.menu_show_only_unread -> {
                    val showOnlyUnread = !item.isChecked
                    preferences.edit().putBoolean(Preferences.SHOW_ONLY_UNREAD.key, showOnlyUnread).apply()
                    item.isChecked = showOnlyUnread
                    reloadListFragment()
                    return@setOnMenuItemClickListener true
                }
                R.id.menu_sync -> {
                    SyncService.startSync(this@ListActivity)
                    return@setOnMenuItemClickListener true
                }
                R.id.menu_about -> {
                    showAboutDialog()
                    return@setOnMenuItemClickListener true
                }
                R.id.menu_mark_all_as_read -> {
                    Queries.markTemporaryFeedAsRead(realm, null, null)
                    return@setOnMenuItemClickListener true
                }
                R.id.menu_manage_feeds -> {
                    startActivityForResult(Intent(this@ListActivity, ManageFeedsActivity::class.java), ManageFeedsActivity.REQUEST_CODE)
                    return@setOnMenuItemClickListener true
                }
            }
            false
        }
        feedViewModel = ViewModelProviders.of(this, FeedViewModelFactory(this)).get(FeedViewModel::class.java)

        preferenceChangeListener = OnSharedPreferenceChangeListener { sharedPreferences: SharedPreferences?, key: String ->
            if (Preferences.SHOW_ONLY_UNREAD.key == key) {
                feedViewModel.updateFolders(Preferences.SHOW_ONLY_UNREAD.getBoolean(preferences))
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.primary)
        binding.swipeRefreshLayout.setOnRefreshListener(this)
        layoutManager = LinearLayoutManager(this)
        adapter = LiveItemsAdapter(emptyList(), this)
        feedViewModel.items.observe(this, Observer { items: List<Item?> ->
            adapter.updateItems(items)
            if (adapter.selectedItemsCount > 0 && actionMode == null) {
                actionMode = startActionMode(this)
            }
            binding.listviewSwitcher.displayedChild = if (items.isEmpty()) 0 else 1
        })
        feedViewModel.temporaryFeed.observe(this, Observer { temporaryFeed: TemporaryFeed -> supportActionBar!!.setTitle(temporaryFeed.name) })
        binding.itemsRecyclerview.adapter = adapter
        binding.itemsRecyclerview.layoutManager = layoutManager
        binding.itemsRecyclerview.addItemDecoration(DividerItemDecoration(this, R.dimen.divider_inset))
        if (savedInstanceState != null) {
            layoutManager.onRestoreInstanceState(savedInstanceState.getParcelable(LAYOUT_MANAGER_STATE))
            adapter.onRestoreInstanceState(savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(LAYOUT_MANAGER_STATE, layoutManager.onSaveInstanceState())
        adapter.onSaveInstanceState(outState)
    }

    override fun onBackPressed() {
        super.onBackPressed()
    }

    private fun reloadListFragment() {
        feedViewModel.updateTemporaryFeed(PreferenceManager.getDefaultSharedPreferences(this), true)
        binding.itemsRecyclerview.scrollToPosition(0)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                LoginActivity.REQUEST_CODE -> {
                    if (data != null && data.getBooleanExtra(LoginActivity.EXTRA_IMPROPERLY_CONFIGURED_CRON, false)) {
                        Snackbar.make(binding.coordinatorLayout, string.updater_improperly_configured, Snackbar.LENGTH_INDEFINITE)
                                .setAction(string.more_info) { v: View? -> startActivity(data) }
                                .setActionTextColor(ContextCompat.getColor(this, R.color.warning))
                                .show()
                    }
                    reloadListFragment()
                    Queries.resetDatabase()
                    SyncService.startSync(this, true)
                }
                ItemPagerActivity.REQUEST_CODE -> if (data != null) binding.itemsRecyclerview.smoothScrollToPosition(data.getIntExtra(ItemPagerActivity.EXTRA_CURRENT_POSITION, -1))
                ManageFeedsActivity.REQUEST_CODE -> reloadListFragment()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_item_list_top, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_account -> {
                val loginIntent = Intent(this@ListActivity, LoginActivity::class.java)
                startActivityForResult(loginIntent, LoginActivity.REQUEST_CODE)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showAboutDialog() {
        LibsBuilder()
                .withAboutIconShown(true)
                .withAboutVersionShown(true)
                .withAboutDescription(getString(string.about_app, getString(string.app_year_author), getString(string.app_url)))
                .withAboutAppName(getString(string.app_name))
                .withLicenseShown(true)
                .withActivityStyle(if (Preferences.DARK_THEME.getBoolean(PreferenceManager.getDefaultSharedPreferences(this))) ActivityStyle.DARK else ActivityStyle.LIGHT_DARK_TOOLBAR)
                .withActivityTitle(getString(string.about))
                .withFields(string::class.java.fields)
                .start(this)
    }

    override fun onItemClick(item: Item, position: Int) {
        if (actionMode == null) {
            val itemActivityIntent = Intent(this, ItemPagerActivity::class.java)
            itemActivityIntent.putExtra(ItemPagerActivity.EXTRA_CURRENT_POSITION, position)
            startActivityForResult(itemActivityIntent, ItemPagerActivity.REQUEST_CODE)
        } else {
            adapter.toggleSelection(position)
            if (adapter.selectedItemsCount == 0) actionMode!!.finish() else {
                actionMode!!.title = adapter.selectedItemsCount.toString()
                actionMode!!.invalidate()
            }
        }
    }

    override fun onItemLongClick(item: Item, position: Int) {
        if (actionMode != null || Preferences.SYS_SYNC_RUNNING.getBoolean(PreferenceManager.getDefaultSharedPreferences(this))) return
        adapter.toggleSelection(position)
        actionMode = startActionMode(this)
    }

    override fun onRefresh() {
        SyncService.startSync(this)
    }

    // TODO: 12/21/19 implement loadmore
    fun onLoadMore(treeItem: TreeItem) {
        val minId = TemporaryFeed.getListTemporaryFeed(realm)
                .items
                .where()
                .min(Item.ID)
        // minId is null if there are no feed items in treeItem
        SyncService.startLoadMore(this, treeItem.id, minId?.toLong() ?: 0, treeItem is Feed)
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(R.menu.menu_item_list_action, menu)
        mode.title = adapter.selectedItemsCount.toString()
        binding.swipeRefreshLayout.isEnabled = false
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        val selectedItemsCount = adapter.selectedItemsCount
        // the menu only changes on the first and second selection
        if (selectedItemsCount > 2) return false
        val firstSelectedItem = adapter.firstSelectedItem
        val firstSelectedUnread = firstSelectedItem != null && firstSelectedItem.isUnread
        menu.findItem(R.id.action_mark_read).isVisible = firstSelectedUnread
        menu.findItem(R.id.action_mark_unread).isVisible = !firstSelectedUnread
        val firstSelectedStarred = firstSelectedItem != null && firstSelectedItem.isStarred
        menu.findItem(R.id.action_mark_starred).isVisible = !firstSelectedStarred
        menu.findItem(R.id.action_mark_unstarred).isVisible = firstSelectedStarred
        menu.findItem(R.id.action_mark_above_read).isVisible = selectedItemsCount == 1
        return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_mark_read -> {
                Queries.setItemsUnread(realm, false, *adapter.selectedItems)
                mode.finish()
                return true
            }
            R.id.action_mark_unread -> {
                Queries.setItemsUnread(realm, true, *adapter.selectedItems)
                mode.finish()
                return true
            }
            R.id.action_mark_starred -> {
                Queries.setItemsStarred(realm, true, *adapter.selectedItems)
                mode.finish()
                return true
            }
            R.id.action_mark_unstarred -> {
                Queries.setItemsStarred(realm, false, *adapter.selectedItems)
                mode.finish()
                return true
            }
            R.id.action_mark_above_read -> {
                Queries.markAboveAsRead(realm, feedViewModel.items.value, adapter.selectedItems[0].id)
                mode.finish()
                return true
            }
        }
        return false
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        actionMode = null
        binding.swipeRefreshLayout.isEnabled = true
        adapter.clearSelection()
    }

    override fun onTreeItemClick(treeItem: TreeItem) {
        feedViewModel.updateSelectedTreeItem(treeItem)
        reloadListFragment()
    }

    companion object {
        private val TAG = ListActivity::class.java.name
        const val LAYOUT_MANAGER_STATE = "LAYOUT_MANAGER_STATE"
    }
}