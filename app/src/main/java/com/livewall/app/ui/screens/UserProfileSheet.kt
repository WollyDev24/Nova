package com.livewall.app.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.livewall.app.data.CommunityAuthManager
import com.livewall.app.data.WallpaperPreferences
import com.livewall.app.ui.theme.ShapeBottomSheet
import com.livewall.app.ui.theme.ShapeCard
import com.livewall.app.ui.theme.ShapePill
import com.livewall.app.util.findActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileSheet(
    authManager: CommunityAuthManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val prefs = remember { WallpaperPreferences(context) }

    val currentUser by authManager.authStateFlow.collectAsState(initial = authManager.currentUser)
    val profileVersion by authManager.profileVersionFlow.collectAsState()
    val isGoogleUser = currentUser != null && !currentUser!!.isAnonymous

    var isSigningIn by remember { mutableStateOf(false) }
    var isSigningOut by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var editNameInput by remember { mutableStateOf("") }

    val resolvedClientId = remember(prefs.googleWebClientId) {
        authManager.resolveServerClientId(context)
    }

    fun triggerGoogleSignIn() {
        val serverClientId = resolvedClientId
        if (serverClientId.isNullOrBlank()) {
            Toast.makeText(context, "Google Sign-In is not configured", Toast.LENGTH_SHORT).show()
            return
        }

        // Use Activity lifecycleScope so the coroutine is NOT cancelled if ModalBottomSheet
        // temporarily recomposes, loses window focus, or leaves composition when Credential Manager appears
        val activity = context.findActivity()
        val authScope = activity?.lifecycleScope ?: scope
        val appContext = context.applicationContext

        isSigningIn = true
        authScope.launch {
            try {
                val result = authManager.signInWithGoogle(context, serverClientId)
                withContext(Dispatchers.Main) {
                    isSigningIn = false
                    result.onSuccess { user ->
                        Toast.makeText(
                            appContext,
                            "Welcome, ${user.displayName ?: "Creator"}!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }.onFailure { err ->
                        if (err !is androidx.credentials.exceptions.GetCredentialCancellationException && err !is CancellationException) {
                            val msg = err.message ?: err.toString()
                            Toast.makeText(appContext, "Sign-in error: $msg", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (_: CancellationException) {
                withContext(Dispatchers.Main) {
                    isSigningIn = false
                }
            }
        }
    }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val sheetMaxWidth = if (screenWidthDp >= 900) 640.dp else BottomSheetDefaults.SheetMaxWidth

    ModalBottomSheet(
        onDismissRequest = {
            if (!isSigningIn) {
                onDismiss()
            }
        },
        sheetState = sheetState,
        shape = ShapeBottomSheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        sheetMaxWidth = sheetMaxWidth,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = navBarBottom + 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Account & Profile",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FilledTonalIconButton(
                    onClick = onDismiss,
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // User Identity Hero Card
            Surface(
                shape = ShapeCard,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Profile Avatar
                    val photoUrl = remember(currentUser, profileVersion) {
                        authManager.getPhotoUrl(currentUser)
                    }

                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.40f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (photoUrl != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(photoUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "User Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = if (isGoogleUser) Icons.Rounded.Person else Icons.Rounded.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    // Name & Email
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = authManager.getEffectiveDisplayName(context),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        editNameInput = authManager.getEffectiveDisplayName(context)
                                        showEditNameDialog = true
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "Edit Display Name",
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                        if (isGoogleUser && !authManager.email.isNullOrBlank()) {
                            Text(
                                text = authManager.email!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "Guest creator mode • Tap pencil to customize name",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Account Status Badge
                    Surface(
                        shape = ShapePill,
                        color = if (isGoogleUser) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isGoogleUser) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
                                contentDescription = null,
                                tint = if (isGoogleUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isGoogleUser) "Google Account Connected" else "Guest Mode (Not Signed In)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isGoogleUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Action: Sign in with Google or Sign Out
            if (!isGoogleUser) {
                Button(
                    onClick = { triggerGoogleSignIn() },
                    enabled = !isSigningIn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = ShapePill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (isSigningIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Signing in with Google...")
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Sign in with Google",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            } else {
                OutlinedButton(
                    onClick = {
                        val activity = context.findActivity()
                        val authScope = activity?.lifecycleScope ?: scope
                        val appContext = context.applicationContext
                        isSigningOut = true
                        authScope.launch {
                            authManager.signOut(context)
                            withContext(Dispatchers.Main) {
                                isSigningOut = false
                                Toast.makeText(appContext, "Signed out", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isSigningOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = ShapePill
                ) {
                    if (isSigningOut) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(imageVector = Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Out", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Customize Display Name Dialog
    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text("Customize Display Name", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Choose a default creator name displayed on your community live wallpapers.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = editNameInput,
                        onValueChange = { if (it.length <= 30) editNameInput = it },
                        label = { Text("Display Name") },
                        placeholder = { Text("e.g. wolly") },
                        trailingIcon = if (editNameInput.isNotEmpty()) {
                            {
                                IconButton(onClick = { editNameInput = "" }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else null,
                        singleLine = true,
                        shape = ShapeCard,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = editNameInput.trim()
                        if (trimmed.isNotBlank()) {
                            scope.launch {
                                val res = authManager.updateDisplayName(trimmed, context)
                                res.onSuccess {
                                    Toast.makeText(context, "Display name updated to \"$trimmed\"", Toast.LENGTH_SHORT).show()
                                    showEditNameDialog = false
                                }.onFailure { err ->
                                    Toast.makeText(context, "Error: ${err.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    enabled = editNameInput.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
