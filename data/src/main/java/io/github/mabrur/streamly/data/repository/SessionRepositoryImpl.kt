package io.github.mabrur.streamly.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.mabrur.streamly.domain.model.Session
import io.github.mabrur.streamly.domain.model.SessionState
import io.github.mabrur.streamly.domain.repository.SessionRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class SessionRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SessionRepository {

    override val state: Flow<SessionState> = dataStore.data.map { prefs ->
        val userId = prefs[KeyUserId]
        if (userId.isNullOrEmpty()) {
            SessionState.SignedOut
        } else {
            SessionState.SignedIn(
                Session(
                    userId = userId,
                    displayName = prefs[KeyDisplayName].orEmpty(),
                    email = prefs[KeyEmail].orEmpty(),
                    isGuest = prefs[KeyIsGuest] ?: false,
                )
            )
        }
    }

    override suspend fun signIn(session: Session) {
        dataStore.edit { prefs ->
            prefs[KeyUserId] = session.userId
            prefs[KeyDisplayName] = session.displayName
            prefs[KeyEmail] = session.email
            prefs[KeyIsGuest] = session.isGuest
        }
    }

    override suspend fun signOut() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val KeyUserId = stringPreferencesKey("user_id")
        val KeyDisplayName = stringPreferencesKey("display_name")
        val KeyEmail = stringPreferencesKey("email")
        val KeyIsGuest = booleanPreferencesKey("is_guest")
    }
}
