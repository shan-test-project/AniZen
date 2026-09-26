package mihon.feature.announcements

import android.os.Parcelable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import kotlinx.parcelize.Parcelize

@Parcelize
data class AnnouncementDetailScreen(private val entry: AnnouncementEntry) : Screen, Parcelable {

    @Composable
    override fun Content() {
        val repository = remember { AnnouncementsRepository() }
        var isWatchlisted by remember { mutableStateOf(repository.isWatchlisted(entry.mediaId)) }
        val navigator = LocalNavigator.currentOrThrow

        Scaffold { contentPadding ->
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
            ) {
                AsyncImage(
                    model = entry.bannerImageUrl ?: entry.coverImageUrl,
                    contentDescription = entry.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                )
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = entry.title, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = entry.description,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )

                    Row(
                        modifier = Modifier.padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        entry.expectedYear?.let { year ->
                            LabeledValue(label = "Expected", value = year.toString())
                        }
                        LabeledValue(
                            label = "Source",
                            value = if (entry.fetchedFrom.startsWith("MAL")) "MAL" else "AniList",
                        )
                    }

                    if (entry.relatedAniListMediaId != null && entry.relatedTitle != null) {
                        Text(
                            text = "Related Anime",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                        )
                        RelatedAnimeCard(
                            title = entry.relatedTitle,
                            year = entry.relatedYear,
                            onClick = {
                                navigator.push(GlobalSearchScreen(entry.relatedTitle))
                            },
                        )
                    }

                    Button(
                        onClick = {
                            repository.toggleWatchlist(entry.mediaId)
                            isWatchlisted = !isWatchlisted
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                    ) {
                        Icon(
                            imageVector = if (isWatchlisted) {
                                Icons.Filled.NotificationsActive
                            } else {
                                Icons.Filled.Notifications
                            },
                            contentDescription = null,
                        )
                        Text(
                            text = if (isWatchlisted) {
                                "Watching for Release Date"
                            } else {
                                "Add to Watchlist"
                            },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun LabeledValue(label: String, value: String) {
        Column {
            Text(text = label, style = MaterialTheme.typography.labelSmall)
            Text(text = value, style = MaterialTheme.typography.bodyMedium)
        }
    }

    @Composable
    private fun RelatedAnimeCard(title: String, year: Int?, onClick: () -> Unit) {
        Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                if (year != null) {
                    Text(text = year.toString(), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}