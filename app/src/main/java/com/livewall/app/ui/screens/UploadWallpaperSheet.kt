package com.livewall.app.ui.screens

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livewall.app.data.CommunityRepository
import com.livewall.app.model.CommunityWallpaper
import com.livewall.app.service.WallpaperColorHelper
import com.livewall.app.ui.theme.ShapeBottomSheet
import com.livewall.app.ui.theme.ShapeCard
import com.livewall.app.ui.theme.ShapePill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

import androidx.compose.runtime.collectAsState
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.TextButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadWallpaperSheet(
    repository: CommunityRepository,
    onDismiss: () -> Unit,
    onUploaded: (CommunityWallpaper) -> Unit,
    onOpenProfile: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val authManager = repository.authManager

    val currentUser by authManager.authStateFlow.collectAsState(initial = authManager.currentUser)
    val profileVersion by authManager.profileVersionFlow.collectAsState()
    val isGoogleUser = currentUser != null && !currentUser!!.isAnonymous
    val isAdmin = remember(currentUser) { authManager.isCurrentUserAdmin(context) }

    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var thumbnailBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var title by remember { mutableStateOf("") }
    var authorName by remember(currentUser, profileVersion) {
        mutableStateOf(authManager.getEffectiveDisplayName(context))
    }
    var durationSec by remember { mutableFloatStateOf(3.0f) }
    var primaryColor by remember { mutableStateOf(Color(0xFF1E88E5)) }
    var secondaryColor by remember { mutableStateOf(Color(0xFF90CAF9)) }
    var tertiaryColor by remember { mutableStateOf(Color(0xFF42A5F5)) }

    var isUploading by remember { mutableStateOf(false) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedVideoUri = uri
            scope.launch(Dispatchers.IO) {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, uri)

                    val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    val durMs = durStr?.toLongOrNull() ?: 3000L
                    durationSec = (durMs / 1000f).coerceAtLeast(1.0f)

                    val frame = retriever.getFrameAtTime(
                        (durMs * 1000) / 2,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    ) ?: retriever.frameAtTime

                    retriever.release()

                    if (frame != null) {
                        thumbnailBitmap = frame
                        val extracted = WallpaperColorHelper.extractTopRgb(frame)
                        withContext(Dispatchers.Main) {
                            primaryColor = Color(extracted.first)
                            secondaryColor = Color(extracted.second)
                            tertiaryColor = Color(extracted.third)
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Could not load video metadata", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = ShapeBottomSheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.CloudUpload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Publish to Community",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Share your live wallpaper with everyone",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                FilledTonalIconButton(onClick = onDismiss, shape = CircleShape) {
                    Icon(imageVector = Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            // Creator Profile Status
            Surface(
                shape = ShapeCard,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val photoUrl = authManager.getPhotoUrl(currentUser)
                    if (isGoogleUser && photoUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(photoUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = if (isGoogleUser) Icons.Rounded.AccountCircle else Icons.Rounded.Info,
                            contentDescription = null,
                            tint = if (isGoogleUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isGoogleUser) authManager.getEffectiveDisplayName(context) else "Publishing as Guest",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isGoogleUser) "Verified Google Creator" else "Sign in to associate creations with your profile",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!isGoogleUser) {
                        TextButton(onClick = onOpenProfile) {
                            Text("Sign In", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Video Picker Card
            Surface(
                shape = ShapeCard,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ShapeCard)
                    .clickable {
                        videoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    }
                    .border(1.dp, Color.White.copy(alpha = 0.12f), ShapeCard)
            ) {
                if (thumbnailBitmap != null) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Image(
                            bitmap = thumbnailBitmap!!.asImageBitmap(),
                            contentDescription = "Selected video preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 64.dp, height = 96.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Video Selected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Duration: ${"%.1f".format(durationSec)}s",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            // Swatches
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(primaryColor))
                                Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(secondaryColor))
                                Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(tertiaryColor))
                            }
                        }

                        Text(
                            text = "Change",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Movie,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "Tap to choose video (MP4)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Max 10 seconds • Under 25MB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Title input
            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= 50) title = it },
                label = { Text("Wallpaper Title") },
                placeholder = { Text("e.g. Celestial Orbit") },
                leadingIcon = {
                    Icon(imageVector = Icons.Rounded.Title, contentDescription = null)
                },
                supportingText = {
                    Text("${title.length} / 50")
                },
                shape = ShapeCard,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )

            // Author Name input
            OutlinedTextField(
                value = authorName,
                onValueChange = { if (it.length <= 30) authorName = it },
                label = { Text("Creator Display Name") },
                placeholder = { Text("e.g. Alex") },
                leadingIcon = {
                    Icon(imageVector = Icons.Rounded.Person, contentDescription = null)
                },
                shape = ShapeCard,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )

            // Submit Button
            Button(
                onClick = {
                    if (selectedVideoUri == null) {
                        Toast.makeText(context, "Please pick a video first", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (title.isBlank()) {
                        Toast.makeText(context, "Please enter a title", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isUploading = true
                    scope.launch(Dispatchers.IO) {
                        try {
                            val baos = ByteArrayOutputStream()
                            val originalBmp = thumbnailBitmap
                            val scaledThumb = if (originalBmp != null) {
                                val maxW = 400
                                if (originalBmp.width > maxW) {
                                    val aspect = originalBmp.height.toFloat() / originalBmp.width.toFloat()
                                    Bitmap.createScaledBitmap(originalBmp, maxW, (maxW * aspect).toInt(), true)
                                } else {
                                    originalBmp
                                }
                            } else null
                            scaledThumb?.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                            if (scaledThumb != null && scaledThumb != originalBmp) {
                                scaledThumb.recycle()
                            }
                            val thumbBytes = baos.toByteArray()

                            val primaryHex = "#%06X".format(0xFFFFFF and primaryColor.hashCode())
                            val secondaryHex = "#%06X".format(0xFFFFFF and secondaryColor.hashCode())
                            val tertiaryHex = "#%06X".format(0xFFFFFF and tertiaryColor.hashCode())

                            val result = repository.uploadWallpaper(
                                title = title,
                                videoUri = selectedVideoUri!!,
                                thumbnailBytes = thumbBytes,
                                primaryHex = primaryHex,
                                secondaryHex = secondaryHex,
                                tertiaryHex = tertiaryHex,
                                durationSec = durationSec,
                                authorName = authorName,
                                tags = listOf("community", "user_upload")
                            )

                            withContext(Dispatchers.Main) {
                                isUploading = false
                                result.onSuccess { item ->
                                    val msg = if (item.isApproved) {
                                        "Published \"${item.title}\" to Community!"
                                    } else {
                                        "\"${item.title}\" submitted for review! It will appear once approved."
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    onUploaded(item)
                                    onDismiss()
                                }.onFailure { err ->
                                    Toast.makeText(context, "Upload error: ${err.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                isUploading = false
                                Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                enabled = !isUploading && selectedVideoUri != null && title.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = ShapePill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Uploading to Community...")
                } else {
                    Icon(imageVector = Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isAdmin) "Publish Live Wallpaper" else "Submit for Review",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            // Moderation info card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isAdmin) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isAdmin) Icons.Rounded.Verified else Icons.Rounded.Info,
                        contentDescription = null,
                        tint = if (isAdmin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isAdmin) {
                            "Admin Mode: Your wallpaper will be published immediately without review."
                        } else {
                            "Community submissions are reviewed by moderators before appearing publicly in the feed."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isAdmin) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
