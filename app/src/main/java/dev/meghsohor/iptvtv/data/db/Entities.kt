package dev.meghsohor.iptvtv.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Source order preserved via [sortOrder] — lists are never re-sorted, per spec. */
@Entity(tableName = "categories")
data class CategoryEntity(@PrimaryKey val id: String, val name: String, val sortOrder: Int)

@Entity(tableName = "countries")
data class CountryEntity(@PrimaryKey val code: String, val name: String, val flag: String, val sortOrder: Int)

/**
 * One row per iptv-org channel+feed (id = "channelId@feedId", iptv-org's own stable key).
 * Multiple stream URLs for this same feed live in [StreamUrlEntity], not here — see
 * "Multiple sources per channel" in the spec.
 */
@Entity(
  tableName = "channels",
  indices = [Index("countryCode"), Index("categoryIds")],
)
data class ChannelEntity(
  @PrimaryKey val id: String,
  val displayName: String,
  val countryCode: String,
  /** Semicolon-joined, matching iptv-org's own multi-value convention (see owners/categories in channels.csv). */
  val categoryIds: String,
  val sortOrder: Int,
  /** The user's manual Source pick (a URL from this channel's [StreamUrlEntity] rows), or null = automatic order. */
  val selectedSourceUrl: String? = null,
)

@Entity(
  tableName = "stream_urls",
  primaryKeys = ["channelId", "sortOrder"],
  foreignKeys = [
    ForeignKey(
      entity = ChannelEntity::class,
      parentColumns = ["id"],
      childColumns = ["channelId"],
      onDelete = ForeignKey.CASCADE,
    )
  ],
  indices = [Index("channelId")],
)
data class StreamUrlEntity(val channelId: String, val url: String, val sortOrder: Int)

@Entity(
  tableName = "bookmarks",
  foreignKeys = [
    ForeignKey(
      entity = ChannelEntity::class,
      parentColumns = ["id"],
      childColumns = ["channelId"],
      onDelete = ForeignKey.CASCADE,
    )
  ],
)
data class BookmarkEntity(@PrimaryKey val channelId: String, val addedAt: Long)
