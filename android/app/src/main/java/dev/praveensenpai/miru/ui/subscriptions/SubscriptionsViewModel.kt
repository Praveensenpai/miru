package dev.praveensenpai.miru.ui.subscriptions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.praveensenpai.miru.data.library.LibraryRepository
import dev.praveensenpai.miru.data.library.SubscriptionEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the Subscriptions tab.
 *
 * The list is a live Room query, so subscribing from the player shows up here
 * immediately — no refresh, no sync round-trip.
 */
class SubscriptionsViewModel(app: Application) : AndroidViewModel(app) {

    private val library = LibraryRepository(app)

    val subscriptions: StateFlow<List<SubscriptionEntity>> = library.observeSubscriptions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun unsubscribe(channelId: String) {
        viewModelScope.launch { library.unsubscribe(channelId) }
    }
}