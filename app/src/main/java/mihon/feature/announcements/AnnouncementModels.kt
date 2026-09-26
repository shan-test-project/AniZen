package mihon.feature.announcements

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
enum class AnnouncementCategory {
    ADAPTATION,
    SEQUEL,
    NEW_SEASON,
    MOVIE,
}

@Serializable
@Parcelize
data class AnnouncementEntry(
    val mediaId: Int,
    val title: String,
    val category: AnnouncementCategory,
    val description: String,
    val expectedYear: Int?,
    val relatedAniListMediaId: Int?,
    val relatedTitle: String?,
    val relatedYear: Int?,
    val coverImageUrl: String?,
    val bannerImageUrl: String?,
    val exactReleaseDate: Long?,
    val fetchedFrom: String,
) : Parcelable