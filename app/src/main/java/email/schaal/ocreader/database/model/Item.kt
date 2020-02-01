/*
 * Copyright © 2020. Daniel Schaal <daniel@schaal.email>
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

package email.schaal.ocreader.database.model

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import android.util.Log
import io.realm.Realm
import io.realm.RealmModel
import io.realm.RealmObject
import io.realm.Sort
import io.realm.annotations.PrimaryKey
import io.realm.annotations.RealmClass
import io.realm.exceptions.RealmException
import io.realm.kotlin.where
import kotlinx.android.parcel.Parcelize
import java.util.*

@Parcelize
@RealmClass
open class Item(
        @PrimaryKey var id: Long = 0,
        var guid: String? = null,
        var guidHash: String? = null,
        var url: String? = null,
        var title: String? = null,
        var author: String? = null,
        var pubDate: Date? = null,
        var updatedAt: Date? = null,
        var body: String = "",
        var enclosureMime: String? = null,
        var enclosureLink: String? = null,
        var feed: Feed? = null,
        var feedId: Long = 0,
        var unread: Boolean = true,
        var unreadChanged: Boolean = false,
        var starred: Boolean = false,
        var starredChanged: Boolean = false,
        var lastModified: Long = 0,
        var fingerprint: String? = null,
        var contentHash: String? = null,
        var active: Boolean = true
) : RealmModel, Insertable, Parcelable {
    companion object {
        const val UNREAD = "unread"
        const val UNREAD_CHANGED ="unreadChanged"
        const val STARRED = "starred"
        const val STARRED_CHANGED = "starredChanged"
        const val LAST_MODIFIED = "lastModified"
        const val UPDATED_AT = "updatedAt"
        const val PUB_DATE = "pubDate"
        const val ACTIVE = "active"

        fun setItemsUnread(realm: Realm, newUnread: Boolean, vararg items: Item?) {
            realm.executeTransaction {
                try {
                    for (item in items.filterNotNull()) { /* If the item has a fingerprint, mark all items with the same fingerprint
                              as read
                             */
                        if (item.fingerprint == null) {
                            item.unread = newUnread
                        } else {
                            val sameItems = it.where<Item>()
                                    .equalTo(Item::fingerprint.name, item.fingerprint)
                                    .equalTo(UNREAD, !newUnread)
                                    .findAll()
                            for (sameItem in sameItems) {
                                sameItem.unread = newUnread
                            }
                        }
                    }
                } catch (e: RealmException) {
                    Log.e(Item::class.simpleName, "Failed to set item as unread", e)
                }
            }
        }

        fun setItemsStarred(realm: Realm, newStarred: Boolean, vararg items: Item?) {
            realm.executeTransaction {
                try {
                    for (item in items.filterNotNull()) {
                        item.starred = newStarred
                    }
                } catch (e: RealmException) {
                    Log.e(Item::class.simpleName, "Failed to set item as starred", e)
                }
            }
        }

        fun removeExcessItems(realm: Realm, maxItems: Int) {
            val itemCount = realm.where<Item>().count()
            if (itemCount > maxItems) {
                val expendableItems = realm.where<Item>()
                        .equalTo(Item::unread.name, false)
                        .equalTo(Item::starred.name, false)
                        .equalTo(Item::active.name, false)
                        .sort(Item::lastModified.name, Sort.ASCENDING)
                        .limit(itemCount - maxItems)
                        .findAll()
                realm.executeTransaction { expendableItems.deleteAllFromRealm() }
            }
        }

    }

    class Builder {
        var id: Long = 0
        var guid: String? = null
        var guidHash: String? = null
        var url: String? = null
        var title: String? = null
        var author: String? = null
        var pubDate: Date? = null
        var updatedAt: Date? = null
        var body: String = ""
        var enclosureMime: String? = null
        var enclosureLink: String? = null
        var feed: Feed? = null
        var feedId: Long = 0
        var unread: Boolean = true
        var unreadChanged: Boolean = false
        var starred: Boolean = false
        var starredChanged: Boolean = false
        var lastModified: Long = 0
        var fingerprint: String? = null
        var contentHash: String? = null
        var active: Boolean = true

        fun build() : Item {
            return Item(this)
        }
    }

    constructor(builder: Builder) :this(
            builder.id,
            builder.guid,
            builder.guidHash,
            builder.url,
            builder.title,
            builder.author,
            builder.pubDate,
            builder.updatedAt,
            builder.body,
            builder.enclosureMime,
            builder.enclosureLink,
            builder.feed,
            builder.feedId,
            builder.unread,
            builder.unreadChanged,
            builder.starred,
            builder.starredChanged,
            builder.lastModified,
            builder.fingerprint,
            builder.contentHash,
            builder.active
    )

    override fun insert(realm: Realm) {
        if(title == null) {
            val fullItem = realm.where<Item>().equalTo(Item::contentHash.name, contentHash).findFirst()
            fullItem?.unread = unread
            fullItem?.starred = starred
        } else {
            feed = Feed.getOrCreate(realm, feedId)
        }
        realm.insertOrUpdate(this)
    }

    override fun delete(realm: Realm) {
        RealmObject.deleteFromRealm(this)
    }

    fun play(context: Context) {
        if(enclosureLink != null) {
            val playIntent = Intent(Intent.ACTION_VIEW)
            playIntent.data = Uri.parse(enclosureLink)
            context.startActivity(playIntent)
        }
    }
}