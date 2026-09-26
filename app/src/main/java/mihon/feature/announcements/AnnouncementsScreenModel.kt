package mihon.feature.announcements

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

class AnnouncementsScreenModel(
    private val repository: AnnouncementsRepository = AnnouncementsRepository(),
) : StateScreenModel<AnnouncementsScreenModel.State>(State.Loading) {

    sealed interface State {
        data object Loading : State

        data class Success(
            val allEntries: ImmutableList<AnnouncementEntry>,
            val selectedCategory: AnnouncementCategory?,
            val fromCache: Boolean,
        ) : State {
            val filteredEntries: ImmutableList<AnnouncementEntry>
                get() = if (selectedCategory == null) {
                    allEntries
                } else {
                    allEntries.filter { it.category == selectedCategory }.toImmutableList()
                }
        }

        data class Error(val cachedEntries: ImmutableList<AnnouncementEntry>) : State
    }

    init {
        load()
    }

    fun load(forceRefresh: Boolean = false) {
        screenModelScope.launch {
            val previousCategory = (state as? State.Success)?.selectedCategory
            mutableState.value = State.Loading
            when (val result = repository.getAnnouncements(forceRefresh)) {
                is AnnouncementsRepository.Result.Success -> {
                    mutableState.value = State.Success(
                        allEntries = result.entries.toImmutableList(),
                        selectedCategory = previousCategory,
                        fromCache = result.fromCache,
                    )
                }
                is AnnouncementsRepository.Result.Failure -> {
                    mutableState.value = State.Error(result.cachedEntries.toImmutableList())
                }
            }
        }
    }

    fun showCachedData() {
        val cached = (state as? State.Error)?.cachedEntries ?: return
        mutableState.value = State.Success(
            allEntries = cached,
            selectedCategory = null,
            fromCache = true,
        )
    }

    fun selectCategory(category: AnnouncementCategory?) {
        val current = state as? State.Success ?: return
        mutableState.value = current.copy(selectedCategory = category)
    }
}