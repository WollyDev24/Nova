package com.livewall.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class CommunityAuthManager(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val isUserSignedIn: Boolean
        get() = auth.currentUser != null

    val currentUserId: String?
        get() = auth.currentUser?.uid

    /**
     * Flow emitting the authenticated user whenever sign-in state changes.
     */
    val authStateFlow: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
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

    fun signOut() {
        auth.signOut()
    }
}
