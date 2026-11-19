package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.AppDatabase
import com.example.data.remote.FirestoreRepository
import com.example.data.sync.ConnectivityObserver
import com.example.data.sync.PreferencesManager
import com.example.data.sync.SyncEngine
import com.example.i18n.AppLanguage
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.MainScaffold
import com.example.ui.theme.AromaPosTheme
import com.example.ui.viewmodel.StoreViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AromaPosRoot()
        }
    }
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

    private data class ActiveSessionIdentity(
        val uid: String,
        val displayName: String,
        val email: String?,
        val roleKey: String
    )


@Composable
private fun AromaPosRoot() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val prefs = remember { PreferencesManager(appContext) }
    val authFlow = remember { Firebase.auth.authStateFlow() }
    val currentUser by authFlow.collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)
    val localSession by prefs.localSessionFlow.collectAsStateWithLifecycle(initialValue = null)

    val savedLangCode by prefs.languageFlow.collectAsStateWithLifecycle(initialValue = "ar")
    val savedDarkMode by prefs.darkModeFlow.collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()

    val db = remember { AppDatabase.getInstance(appContext) }
    val dao = remember { db.storeDao() }

    val activeIdentity = when {
        currentUser != null -> ActiveSessionIdentity(
            uid = currentUser!!.uid,
            displayName = currentUser!!.displayName ?: "",
            email = currentUser!!.email,
            roleKey = "admin"
        )
        localSession != null -> ActiveSessionIdentity(
            uid = localSession!!.uid,
            displayName = localSession!!.displayName,
            email = null,
            roleKey = localSession!!.roleKey
        )
        else -> null
    }

    if (activeIdentity == null) {
        AromaPosTheme(
            darkTheme = savedDarkMode,
            language = AppLanguage.fromCode(savedLangCode)
        ) {
            AuthScreen(
                language = AppLanguage.fromCode(savedLangCode),
                darkMode = savedDarkMode,
                onToggleLanguage = {
                    scope.launch {
                        val next = if (savedLangCode == "ar") "en" else "ar"
                        prefs.setLanguage(next)
                    }
                },
                onToggleDarkMode = {
                    scope.launch {
                        prefs.setDarkMode(!savedDarkMode)
                    }
                },
                onLocalAdminLogin = { username, password ->
                    prefs.verifyAndSignInLocalUser(dao, username, password)
                },
                onAuthSuccess = { /* authStateFlow or localSessionFlow updates automatically */ }
            )
        }
    } else {
        key(activeIdentity.uid) {
            val storeViewModel: StoreViewModel = viewModel(
                key = activeIdentity.uid,
                factory = viewModelFactory {
                    initializer {
                        val remote = FirestoreRepository(appContext)
                        val connectivity = ConnectivityObserver(appContext)
                        val syncEngine = SyncEngine(
                            dao = dao,
                            remote = remote,
                            connectivity = connectivity,
                            prefs = prefs
                        )
                        StoreViewModel(
                            dao = dao,
                            remote = remote,
                            syncEngine = syncEngine,
                            connectivity = connectivity,
                            prefs = prefs,
                            currentUserId = activeIdentity.uid,
                            currentUserDisplayName = activeIdentity.displayName,
                            currentUserEmail = activeIdentity.email,
                            initialRoleKey = activeIdentity.roleKey
                        )
                    }
                }
            )
            val uiState by storeViewModel.uiState.collectAsStateWithLifecycle()

            AromaPosTheme(
                darkTheme = uiState.darkMode,
                primaryColorHex = uiState.settings.primaryColorHex,
                secondaryColorHex = uiState.settings.secondaryColorHex,
                language = uiState.language
            ) {
                MainScaffold(
                    state = uiState,
                    viewModel = storeViewModel
                )
            }
        }
    }
}
