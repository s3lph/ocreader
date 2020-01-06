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

import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.annotation.TargetApi
import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.preference.PreferenceManager
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.Keep
import androidx.core.graphics.ColorUtils
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.viewpager.widget.ViewPager.OnPageChangeListener
import email.schaal.ocreader.ItemPageFragment.Companion.newInstance
import email.schaal.ocreader.database.Queries
import email.schaal.ocreader.database.model.Item
import email.schaal.ocreader.database.model.TemporaryFeed
import email.schaal.ocreader.databinding.ActivityItemPagerBinding
import email.schaal.ocreader.util.FaviconLoader
import email.schaal.ocreader.util.FaviconLoader.FeedColorsListener
import email.schaal.ocreader.util.FeedColors
import java.util.*

class ItemPagerActivity : RealmActivity() {
    private lateinit var binding: ActivityItemPagerBinding
    @ColorInt
    private var defaultToolbarColor = 0
    @ColorInt
    private var defaultAccent = 0
    private var item: Item? = null
    private var items: List<Item>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= 24) {
            WebView(this)
        }
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_item_pager)
        setSupportActionBar(binding.bottomAppbar)
        val order = Preferences.ORDER.getOrder(PreferenceManager.getDefaultSharedPreferences(this))
        val sortField = Preferences.SORT_FIELD.getString(PreferenceManager.getDefaultSharedPreferences(this))
        val title: String
        if (intent.hasExtra("ARG_ITEMS")) {
            title = "Test"
            items = intent.getParcelableArrayListExtra("ARG_ITEMS")
        } else {
            TemporaryFeed.updatePagerTemporaryFeed(realm)
            val temporaryFeed = TemporaryFeed.getPagerTemporaryFeed(realm)
            items = temporaryFeed.items.sort(sortField, order)
            title = temporaryFeed.name
        }
        val typedArray = obtainStyledAttributes(intArrayOf(R.attr.colorPrimary, R.attr.colorAccent))
        try {
            defaultToolbarColor = typedArray.getColor(0, 0)
            defaultAccent = typedArray.getColor(1, 0)
        } finally {
            typedArray.recycle()
        }
        val position = intent.getIntExtra(EXTRA_CURRENT_POSITION, 0)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        binding.toolbarLayout.toolbar.title = title
        binding.toolbarLayout.toolbar.subtitle = ""
        val mSectionsPagerAdapter = SectionsPagerAdapter(supportFragmentManager)
        binding.fabOpenInBrowser.setOnClickListener {
            if (item != null && item!!.url != null) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item!!.url))
                startActivity(intent)
            }
        }
        binding.container.adapter = mSectionsPagerAdapter
        val pageChangeListener: OnPageChangeListener = MyOnPageChangeListener(mSectionsPagerAdapter)
        binding.container.addOnPageChangeListener(pageChangeListener)
        // The initial position is 0, so the pageChangeListener won't be called when setting the position to 0
        if (position == 0) pageChangeListener.onPageSelected(position)
        binding.container.setCurrentItem(position, false)
    }

    private fun shareArticle() {
        if (item!!.url != null) {
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            shareIntent.putExtra(Intent.EXTRA_TEXT, item!!.title + " - " + item!!.url)
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_article)))
        }
    }

    fun getItemForPosition(position: Int): Item {
        return items!![position]
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_item_pager_bottom, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_play_enclosure_media).isVisible = item!!.enclosureLink != null
        val menuItemRead = menu.findItem(R.id.menu_mark_read)
        updateMenuItem(menuItemRead, !item!!.isUnread, R.drawable.ic_check_box, R.drawable.ic_check_box_outline_blank)
        val menuItemStarred = menu.findItem(R.id.menu_mark_starred)
        updateMenuItem(menuItemStarred, item!!.isStarred, R.drawable.ic_star, R.drawable.ic_star_outline)
        return super.onPrepareOptionsMenu(menu)
    }

    private fun updateMenuItem(menuItem: MenuItem, value: Boolean, @DrawableRes checkedIcon: Int, @DrawableRes uncheckedIcon: Int) {
        menuItem.isChecked = value
        menuItem.setIcon(if (value) checkedIcon else uncheckedIcon)
    }

    private fun setItemUnread(unread: Boolean) {
        Queries.setItemsUnread(realm, unread, item)
    }

    private fun setItemStarred(starred: Boolean) {
        Queries.setItemsStarred(realm, starred, item)
    }

    fun updateResult() {
        val result = Intent()
        result.putExtra(EXTRA_CURRENT_POSITION, binding.container.currentItem)
        setResult(Activity.RESULT_OK, result)
    }

    override fun onBackPressed() {
        updateResult()
        super.onBackPressed()
    }

    override fun onOptionsItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            android.R.id.home -> {
                updateResult()
                super.onOptionsItemSelected(menuItem)
            }
            R.id.action_play_enclosure_media -> {
                item!!.play(this)
                true
            }
            R.id.action_share_article -> {
                shareArticle()
                true
            }
            R.id.menu_mark_read -> {
                setItemUnread(!item!!.isUnread)
                updateMenuItem(menuItem, !item!!.isUnread, R.drawable.ic_check_box, R.drawable.ic_check_box_outline_blank)
                true
            }
            R.id.menu_mark_starred -> {
                setItemStarred(!item!!.isStarred)
                updateMenuItem(menuItem, item!!.isStarred, R.drawable.ic_star, R.drawable.ic_star_outline)
                true
            }
            else -> super.onOptionsItemSelected(menuItem)
        }
    }

    private inner class SectionsPagerAdapter internal constructor(fm: FragmentManager?) : FragmentStatePagerAdapter(fm!!, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        private val fragments: WeakHashMap<Int, ItemPageFragment?>
        override fun getItem(position: Int): Fragment {
            val fragment: ItemPageFragment
            if (fragments.containsKey(position)) fragment = fragments[position]!! else {
                fragment = newInstance(getItemForPosition(position))
                fragments[position] = fragment
            }
            return fragment
        }

        override fun getCount(): Int {
            return items!!.size
        }

        init {
            fragments = WeakHashMap(count)
        }
    }

    private open inner class StatusBarChanger {
        @Keep
        open fun setStatusBarColor(backgroundColor: Int) {
            binding.toolbarLayout.toolbar.setBackgroundColor(backgroundColor)
            binding.bottomAppbar.backgroundTint = ColorStateList.valueOf(backgroundColor)
        }
    }

    private inner class StatusBarChangerLollipop : StatusBarChanger() {
        private val hsl = FloatArray(3)
        private fun changeLightness(backgroundColor: Int, lightnessChange: Float): Int {
            ColorUtils.colorToHSL(backgroundColor, hsl)
            hsl[2] *= lightnessChange
            return ColorUtils.HSLToColor(hsl)
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Keep
        override fun setStatusBarColor(backgroundColor: Int) {
            val statusbarColor = changeLightness(backgroundColor, 0.7f)
            window.statusBarColor = statusbarColor
            super.setStatusBarColor(backgroundColor)
        }
    }

    private inner class MyOnPageChangeListener internal constructor(private val mSectionsPagerAdapter: SectionsPagerAdapter) : OnPageChangeListener {
        private val DURATION = 250

        private val currentNightMode: Int = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        private val statusBarChanger: StatusBarChanger = StatusBarChangerLollipop()
        private var fabColorFrom = 0
        private var colorTo = defaultToolbarColor
        private var fabColorTo = defaultAccent
        private var colorFrom = 0
        private var progressFrom = 0f
        private var progressTo = 0f
        private val argbEvaluator = ArgbEvaluator()
        private var firstRun = true

        private val toListener: FeedColorsListener = object : FeedColorsListener {
            override fun onGenerated(feedColors: FeedColors) {
                colorTo = feedColors.getColor(FeedColors.Type.TEXT, defaultToolbarColor)
                fabColorTo = feedColors.getColor(FeedColors.Type.BACKGROUND, defaultAccent)
                if (firstRun) {
                    firstRun = false
                    if (currentNightMode == Configuration.UI_MODE_NIGHT_NO) statusBarChanger.setStatusBarColor(colorTo)
                    binding.fabOpenInBrowser.setBackgroundColor(fabColorTo)
                } else {
                    val fabAnimator = ObjectAnimator
                            .ofInt(binding.fabOpenInBrowser, "fabBackgroundColor", fabColorFrom, fabColorTo)
                    fabAnimator.setEvaluator(argbEvaluator)
                    val animatorSet = AnimatorSet()
                    val animatorSetBuilder = animatorSet.play(fabAnimator)
                    if (currentNightMode == Configuration.UI_MODE_NIGHT_NO) {
                        val statusBarAnimator = ObjectAnimator.ofInt(statusBarChanger, "statusBarColor", colorFrom, colorTo)
                        statusBarAnimator.setEvaluator(argbEvaluator)
                        animatorSetBuilder.with(statusBarAnimator)
                    }
                    animatorSet.duration = DURATION.toLong()
                    animatorSet.start()
                }
            }

            override fun onStart() {
                colorFrom = colorTo
                fabColorFrom = fabColorTo
            }
        }

        override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
        override fun onPageSelected(position: Int) {
            invalidateOptionsMenu()
            item = getItemForPosition(position)
            setItemUnread(false)
            binding.toolbarLayout.toolbar.subtitle = item!!.feed.name
            FaviconLoader.Builder(binding.fabOpenInBrowser)
                    .withGenerateFallbackImage(false)
                    .withPlaceholder(R.drawable.ic_open_in_browser)
                    .build()
                    .load(this@ItemPagerActivity, item!!.feed, toListener)
            progressFrom = progressTo
            progressTo = (position + 1).toFloat() / mSectionsPagerAdapter.count.toFloat()
            binding.bottomAppbar.performShow()
            binding.fabOpenInBrowser.show()
            val progressAnimator = ObjectAnimator
                    .ofFloat(binding.fabOpenInBrowser, "progress", progressFrom, progressTo)
                    .setDuration(DURATION.toLong())
            progressAnimator.start()
        }

        override fun onPageScrollStateChanged(state: Int) {}
    }

    companion object {
        const val REQUEST_CODE = 2
        const val EXTRA_CURRENT_POSITION = "email.schaal.ocreader.extra.CURRENT_POSIION"
    }
}