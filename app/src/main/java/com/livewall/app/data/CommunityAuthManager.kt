package com.livewall.app.data

import android.content.Context
import android.net.Uri
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.livewall.app.R
import com.livewall.app.util.findActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class CommunityAuthManager(
    val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    private val _profileVersionFlow = MutableStateFlow(0)
    val profileVersionFlow: StateFlow<Int> = _profileVersionFlow.asStateFlow()

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val isUserSignedIn: Boolean
        get() = auth.currentUser != null && !auth.currentUser!!.isAnonymous

    val isAnonymous: Boolean
        get() = auth.currentUser?.isAnonymous ?: true

    val currentUserId: String?
        get() = auth.currentUser?.uid

    val displayName: String
        get() = getEffectiveDisplayName()

    fun getEffectiveDisplayName(context: Context? = null): String {
        if (context != null) {
            val custom = WallpaperPreferences(context).customDisplayName
            if (!custom.isNullOrBlank()) return custom
        }
        val user = auth.currentUser
        return when {
            user == null -> "Guest"
            !user.displayName.isNullOrBlank() -> user.displayName!!
            user.isAnonymous -> "Anonymous Creator"
            !user.email.isNullOrBlank() -> user.email!!.substringBefore('@')
            else -> "Creator"
        }
    }

    val email: String?
        get() = auth.currentUser?.email

    val photoUrl: String?
        get() = getPhotoUrl()

    /**
     * Resolves avatar picture from Firebase profile or any attached provider (e.g. google.com).
     */
    fun getPhotoUrl(user: FirebaseUser? = auth.currentUser): String? {
        val u = user ?: auth.currentUser ?: return null
        u.photoUrl?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
        for (profile in u.providerData) {
            profile.photoUrl?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    /**
     * Syncs Google account profile photo & display name from providerData if missing.
     */
    suspend fun syncGoogleProfileIfNeeded() = withContext(Dispatchers.IO) {
        try {
            val user = auth.currentUser ?: return@withContext
            if (user.isAnonymous) return@withContext
            val providerPhoto = user.providerData.firstNotNullOfOrNull { it.photoUrl }
            val providerName = user.providerData.firstNotNullOfOrNull { it.displayName }

            val needsPhoto = user.photoUrl == null && providerPhoto != null
            val needsName = user.displayName.isNullOrBlank() && !providerName.isNullOrBlank()

            if (needsPhoto || needsName) {
                val updates = UserProfileChangeRequest.Builder().apply {
                    if (needsPhoto) setPhotoUri(providerPhoto)
                    if (needsName) setDisplayName(providerName)
                }.build()
                user.updateProfile(updates).await()
                user.reload().await()
                _profileVersionFlow.value += 1
            }
        } catch (_: Exception) {}
    }

    /**
     * Updates and customizes the user's default creator display name.
     * Persists locally to WallpaperPreferences, in Firebase Auth profile, and in Firestore.
     */
    suspend fun updateDisplayName(newName: String, context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val trimmed = newName.trim()
            if (trimmed.isEmpty()) throw IllegalArgumentException("Display name cannot be empty")

            // 1. Save in SharedPreferences
            WallpaperPreferences(context).customDisplayName = trimmed

            // 2. If signed into Firebase, update Firebase User profile & Firestore user record
            val user = auth.currentUser
            if (user != null) {
                val req = UserProfileChangeRequest.Builder().setDisplayName(trimmed).build()
                user.updateProfile(req).await()
                try {
                    FirebaseFirestore.getInstance().collection("users").document(user.uid)
                        .set(
                            mapOf(
                                "displayName" to trimmed,
                                "photoUrl" to (getPhotoUrl(user) ?: ""),
                                "updatedAt" to FieldValue.serverTimestamp()
                            ),
                            SetOptions.merge()
                        ).await()
                } catch (_: Exception) {}
                try {
                    user.reload().await()
                } catch (_: Exception) {}
            }
            _profileVersionFlow.value += 1
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Flow emitting the authenticated user whenever sign-in state changes.
     */
    val authStateFlow: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /**
     * Resolves the Web Client ID:
     * 1. Explicitly stored in preferences.
     * 2. Auto-generated by google-services gradle plugin (R.string.default_web_client_id).
     * 3. Fallback to Nova Firebase project web client id.
     */
    fun resolveServerClientId(context: Context): String? {
        val prefs = WallpaperPreferences(context)
        val custom = prefs.googleWebClientId
        if (!custom.isNullOrBlank()) {
            return custom.trim()
        }

        // Direct compile-time reference ensures R8/shrinkResources does not strip default_web_client_id
        try {
            val resStr = context.getString(R.string.default_web_client_id)
            if (resStr.isNotBlank() && !resStr.startsWith("AIzaSy")) {
                return resStr.trim()
            }
        } catch (_: Throwable) {
            // Fall through if not present
        }

        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resId != 0) {
            val resStr = context.getString(resId)
            if (resStr.isNotBlank() && !resStr.startsWith("AIzaSy")) {
                return resStr.trim()
            }
        }

        return FALLBACK_WEB_CLIENT_ID
    }

    companion object {
        const val FALLBACK_WEB_CLIENT_ID = "18387689298-4phk0c10at8il0c289cj33cprim7d8pf.apps.googleusercontent.com"
    }

    /**
     * Authenticates with Google using Android Credential Manager (Android 14+ / Google Play Services).
     * Automatically links anonymous credentials or signs into existing account.
     */
    suspend fun signInWithGoogle(context: Context, serverClientId: String): Result<FirebaseUser> {
        return try {
            val activityContext = context.findActivity() ?: context
            val credentialManager = CredentialManager.create(activityContext)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId.trim())
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response = credentialManager.getCredential(
                request = request,
                context = activityContext
            )

            val credential = response.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                val authCredential = GoogleAuthProvider.getCredential(idToken, null)

                val prevUser = auth.currentUser
                val result = if (prevUser != null && prevUser.isAnonymous) {
                    try {
                        prevUser.linkWithCredential(authCredential).await()
                    } catch (_: Exception) {
                        auth.signInWithCredential(authCredential).await()
                    }
                } else {
                    auth.signInWithCredential(authCredential).await()
                }

                val finalUser = result.user ?: throw IllegalStateException("Firebase sign-in yielded null user")

                // Sync Google profile picture and display name to Firebase User Profile
                val pfpUri = googleIdTokenCredential.profilePictureUri ?: finalUser.providerData.firstNotNullOfOrNull { it.photoUrl }
                val googleName = googleIdTokenCredential.displayName ?: finalUser.providerData.firstNotNullOfOrNull { it.displayName }

                val profileUpdates = UserProfileChangeRequest.Builder().apply {
                    if (pfpUri != null) setPhotoUri(pfpUri)
                    if (!googleName.isNullOrBlank() && finalUser.displayName.isNullOrBlank()) {
                        setDisplayName(googleName)
                    }
                }.build()

                try {
                    finalUser.updateProfile(profileUpdates).await()
                    finalUser.reload().await()
                    _profileVersionFlow.value += 1
                } catch (_: Exception) {}

                Result.success(finalUser)
            } else {
                Result.failure(IllegalStateException("Unsupported credential type: ${credential.type}"))
            }
        } catch (e: GetCredentialCancellationException) {
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Automatically ensures the device has a Firebase UID without requiring a login dialog.
     * Anonymous auth enables frictionless browsing, liking, and download tracking.
     */
    suspend fun ensureAuthenticated(): Result<FirebaseUser> {
        return try {
            val user = auth.currentUser
            if (user != null) {
                Result.success(user)
            } else {
                val result = auth.signInAnonymously().await()
                val newUser = result.user ?: throw IllegalStateException("Anonymous sign-in yielded null user")
                Result.success(newUser)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Signs out from Firebase and clears Google credential selection state.
     */
    suspend fun signOut(context: Context) {
        try {
            auth.signOut()
            val credentialManager = CredentialManager.create(context)
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {
            auth.signOut()
        }
    }
}
