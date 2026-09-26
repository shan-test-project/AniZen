package mihon.feature.announcements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow

object AnnouncementsTab {

    @Composable
    fun Content(
        contentPadding: PaddingValues,
        screenModel: AnnouncementsScreenModel,
    ) {
        val state by screenModel.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        when (val current = state) {
            is AnnouncementsScreenModel.State.Loading -> LoadingState(contentPadding)
            is AnnouncementsScreenModel.State.Error -> ErrorState(
                contentPadding = contentPadding,
                onRetry = { screenModel.load(forceRefresh = true) },
                onShowCached = { screenModel.showCachedData() },
            )
            is AnnouncementsScreenModel.State.Success -> SuccessState(
                contentPadding = contentPadding,
                state = current,
                onSelectCategory = screenModel::selectCategory,
                onCardClick = { entry ->
                    navigator.push(AnnouncementDetailScreen(entry))
                },
            )
        }
    }

    @Composable
    private fun LoadingState(contentPadding: PaddingValues) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }

    @Composable
    private fun ErrorState(
        contentPadding: PaddingValues,
        onRetry: () -> Unit,
        onShowCached: () -> Unit,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(imageVector = Icons.Outlined.Warning, contentDescription = null)
            Text(
                text = "Couldn't load announcements",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = "AniList is currently unavailable or rate limited. Showing cached data (if available).",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(onClick = onRetry, modifier = Modifier.padding(top = 24.dp)) {
                Text("Try Again")
            }
            OutlinedButton(onClick = onShowCached, modifier = Modifier.padding(top = 8.dp)) {
                Text("Show Cached Data")
            }
        }
    }

    @Composable
    private fun SuccessState(
        contentPadding: PaddingValues,
        state: AnnouncementsScreenModel.State.Success,
        onSelectCategory: (AnnouncementCategory?) -> Unit,
        onCardClick: (AnnouncementEntry) -> Unit,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CategoryChipsRow(selected = state.selectedCategory, onSelect = onSelectCategory)
            LazyColumn(
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.filteredEntries, key = { it.mediaId }) { entry ->
                    AnnouncementCard(entry = entry, onClick = { onCardClick(entry) })
                }
            }
        }
    }

    @Composable
    private fun CategoryChipsRow(
        selected: AnnouncementCategory?,
        onSelect: (AnnouncementCategory?) -> Unit,
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            item {
                FilterChip(
                    selected = selected == null,
                    onClick = { onSelect(null) },
                    label = { Text("All") },
                )
            }
            items(AnnouncementCategory.entries.toList()) { category ->
                FilterChip(
                    selected = selected == category,
                    onClick = { onSelect(category) },
                    label = { Text(category.displayName()) },
                )
            }
        }
    }

    private fun AnnouncementCategory.displayName(): String = when (this) {
        AnnouncementCategory.ADAPTATION -> "Adaptation"
        AnnouncementCategory.SEQUEL -> "Sequel"
        AnnouncementCategory.NEW_SEASON -> "New Season"
        AnnouncementCategory.MOVIE -> "Movie"
    }
}