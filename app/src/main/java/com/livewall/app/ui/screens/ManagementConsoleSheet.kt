package com.livewall.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.livewall.app.data.CommunityAuthManager
import com.livewall.app.data.CommunityRepository
import com.livewall.app.data.ScalingMode
import com.livewall.app.model.CommunityWallpaper
import com.livewall.app.service.WallpaperColorHelper
import com.livewall.app.ui.components.VideoPlayerView
import com.livewall.app.ui.theme.ShapeBottomSheet
import com.livewall.app.ui.theme.ShapeCard
import com.livewall.app.ui.theme.ShapeDialog
import com.livewall.app.ui.theme.ShapePill
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

enum class ModerationFilterTab(val label: String) {
    PENDING("Pending"),
    APPROVED("Live"),
    REJECTED("Rejected"),
    ALL("All")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ManagementConsoleSheet(
    repository: CommunityRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val isAdminUnlocked = remember {
        repository.authManager.isCurrentUserAdmin(context)
    }

    var wallpapers by remember { mutableStateOf<List<CommunityWallpaper>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(ModerationFilterTab.PENDING) }
    var searchQuery by remember { mutableStateOf("") }

    // Dialog state
    var wallpaperToPreview by remember { mutableStateOf<CommunityWallpaper?>(null) }
    var wallpaperToReject by remember { mutableStateOf<CommunityWallpaper?>(null) }
    var rejectionReason by remember { mutableStateOf("Low video resolution or visual artifacting") }
    var wallpaperToDelete by remember { mutableStateOf<CommunityWallpaper?>(null) }
    var isProcessingAction by remember { mutableStateOf(false) }

    fun refreshList() {
        if (!isAdminUnlocked) return
        isLoading = true
        scope.launch {
            val result = repository.getAllWallpapersForModeration()
            isLoading = false
            result.onSuccess { list ->
                wallpapers = list
            }.onFailure { err ->
                Toast.makeText(context, "Error loading submissions: ${err.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(isAdminUnlocked) {
        if (isAdminUnlocked) {
            refreshList()
        }
    }

    fun handleApprove(item: CommunityWallpaper) {
        if (isProcessingAction) return
        isProcessingAction = true
        scope.launch {
            val reviewer = repository.authManager.displayName.ifBlank { "Admin" }
            val res = repository.approveWallpaper(item.id, reviewer)
            isProcessingAction = false
            res.onSuccess {
                Toast.makeText(context, "Approved \"${item.title}\"!", Toast.LENGTH_SHORT).show()
                refreshList()
            }.onFailure { err ->
                Toast.makeText(context, "Approve failed: ${err.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun handleReject(item: CommunityWallpaper, reason: String) {
        if (isProcessingAction) return
        isProcessingAction = true
        scope.launch {
            val reviewer = repository.authManager.displayName.ifBlank { "Admin" }
            val res = repository.rejectWallpaper(item.id, reason, reviewer)
            isProcessingAction = false
            wallpaperToReject = null
            res.onSuccess {
                Toast.makeText(context, "Rejected \"${item.title}\"", Toast.LENGTH_SHORT).show()
                refreshList()
            }.onFailure { err ->
                Toast.makeText(context, "Reject failed: ${err.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun handleDelete(item: CommunityWallpaper) {
        if (isProcessingAction) return
        isProcessingAction = true
        scope.launch {
            val res = repository.adminDeleteWallpaper(item.id)
            isProcessingAction = false
            wallpaperToDelete = null
            res.onSuccess {
                Toast.makeText(context, "Deleted \"${item.title}\"", Toast.LENGTH_SHORT).show()
                refreshList()
            }.onFailure { err ->
                Toast.makeText(context, "Delete failed: ${err.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val pendingCount = remember(wallpapers) { wallpapers.count { it.isPending } }
    val approvedCount = remember(wallpapers) { wallpapers.count { it.isApproved } }
    val rejectedCount = remember(wallpapers) { wallpapers.count { it.isRejected } }

    val filteredList = remember(wallpapers, activeTab, searchQuery) {
        val tabFiltered = when (activeTab) {
            ModerationFilterTab.PENDING -> wallpapers.filter { it.isPending }
            ModerationFilterTab.APPROVED -> wallpapers.filter { it.isApproved }
            ModerationFilterTab.REJECTED -> wallpapers.filter { it.isRejected }
            ModerationFilterTab.ALL -> wallpapers
        }
        if (searchQuery.isBlank()) {
            tabFiltered
        } else {
            tabFiltered.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.authorName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = ShapeBottomSheet,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = navBarBottom)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.AdminPanelSettings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Nova Studio",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = ShapePill,
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            ) {
                                Text(
                                    text = "Console",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Content Moderation & Approvals",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isAdminUnlocked) {
                        IconButton(onClick = { refreshList() }) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Refresh",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!isAdminUnlocked) {
                // Restricted Admin Access Screen
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Admin Access Required",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Nova Studio is restricted to authorized administrators. Please sign in with an authorized Google administrator account to manage community content.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onDismiss,
                        shape = ShapePill,
                        modifier = Modifier.fillMaxWidth(0.6f)
                    ) {
                        Text("Close")
                    }
                }
            } else {
                // Main Console Interface

                // Metrics Chips Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusCounterChip(
                        title = "Pending",
                        count = pendingCount,
                        color = Color(0xFFFFB74D),
                        isSelected = activeTab == ModerationFilterTab.PENDING,
                        onClick = { activeTab = ModerationFilterTab.PENDING },
                        modifier = Modifier.weight(1f)
                    )
                    StatusCounterChip(
                        title = "Live",
                        count = approvedCount,
                        color = Color(0xFF81C784),
                        isSelected = activeTab == ModerationFilterTab.APPROVED,
                        onClick = { activeTab = ModerationFilterTab.APPROVED },
                        modifier = Modifier.weight(1f)
                    )
                    StatusCounterChip(
                        title = "Rejected",
                        count = rejectedCount,
                        color = Color(0xFFE57373),
                        isSelected = activeTab == ModerationFilterTab.REJECTED,
                        onClick = { activeTab = ModerationFilterTab.REJECTED },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter submissions by title or creator...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Rounded.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = ShapeCard,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    }
                } else if (filteredList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = when (activeTab) {
                                    ModerationFilterTab.PENDING -> "No pending submissions to review!"
                                    ModerationFilterTab.APPROVED -> "No live wallpapers found"
                                    ModerationFilterTab.REJECTED -> "No rejected submissions"
                                    ModerationFilterTab.ALL -> "No submissions found"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(520.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredList, key = { it.id }) { item ->
                            ModerationCard(
                                item = item,
                                onPreview = { wallpaperToPreview = item },
                                onApprove = { handleApprove(item) },
                                onReject = { wallpaperToReject = item },
                                onDelete = { wallpaperToDelete = item },
                                isProcessing = isProcessingAction
                            )
                        }
                    }
                }
            }
        }
    }

    // Video Preview Dialog
    if (wallpaperToPreview != null) {
        val previewItem = wallpaperToPreview!!
        Dialog(
            onDismissRequest = { wallpaperToPreview = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                VideoPlayerView(
                    videoUri = previewItem.videoUrl,
                    scalingMode = ScalingMode.CROP,
                    isMuted = true,
                    autoPlay = true,
                    repeatMode = Player.REPEAT_MODE_ALL,
                    modifier = Modifier.fillMaxSize()
                )

                // Top Floating Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 40.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = ShapePill,
                        color = Color.Black.copy(alpha = 0.65f),
                        contentColor = Color.White
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(text = previewItem.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(text = "By ${previewItem.authorName}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                        }
                    }

                    FilledTonalIconButton(
                        onClick = { wallpaperToPreview = null },
                        colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.65f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(imageVector = Icons.Rounded.Close, contentDescription = "Close preview")
                    }
                }

                // Bottom Moderation Floating Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (previewItem.isPending || previewItem.isRejected) {
                        Button(
                            onClick = {
                                handleApprove(previewItem)
                                wallpaperToPreview = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            shape = ShapePill,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Approve Now")
                        }
                    }

                    if (previewItem.isPending || previewItem.isApproved) {
                        OutlinedButton(
                            onClick = {
                                wallpaperToReject = previewItem
                                wallpaperToPreview = null
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252)),
                            shape = ShapePill,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reject")
                        }
                    }
                }
            }
        }
    }

    // Rejection Reason Prompt Dialog
    if (wallpaperToReject != null) {
        val target = wallpaperToReject!!
        AlertDialog(
            onDismissRequest = { wallpaperToReject = null },
            shape = ShapeDialog,
            title = {
                Text("Reject Submission", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        "Rejecting \"${target.title}\" will unpublish it from the community hub.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Select or enter a reason:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    val quickReasons = listOf(
                        "Low video quality / stuttering",
                        "Inappropriate content",
                        "Copyrighted material",
                        "Does not loop or animate properly"
                    )

                    quickReasons.forEach { reason ->
                        Surface(
                            shape = ShapeCard,
                            color = if (rejectionReason == reason) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable { rejectionReason = reason }
                        ) {
                            Text(
                                text = reason,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (rejectionReason == reason) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rejectionReason,
                        onValueChange = { rejectionReason = it },
                        label = { Text("Custom reason note") },
                        singleLine = true,
                        shape = ShapeCard,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { handleReject(target, rejectionReason) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = ShapePill
                ) {
                    Text("Reject & Hide")
                }
            },
            dismissButton = {
                TextButton(onClick = { wallpaperToReject = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Force Delete Confirmation Dialog
    if (wallpaperToDelete != null) {
        val target = wallpaperToDelete!!
        AlertDialog(
            onDismissRequest = { wallpaperToDelete = null },
            shape = ShapeDialog,
            title = {
                Text("Delete Wallpaper Permanently?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Are you sure you want to permanently delete \"${target.title}\" by ${target.authorName}? This removes the record from Firestore completely.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = { handleDelete(target) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = ShapePill
                ) {
                    Text("Delete Forever")
                }
            },
            dismissButton = {
                TextButton(onClick = { wallpaperToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StatusCounterChip(
    title: String,
    count: Int,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = ShapeCard,
        color = if (isSelected) color.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHighest,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, color) else null,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                fontWeight = FontWeight.Black,
                fontSize = 17.sp,
                color = if (isSelected) color else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ModerationCard(
    item: CommunityWallpaper,
    onPreview: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onDelete: () -> Unit,
    isProcessing: Boolean
) {
    val context = LocalContext.current
    val dateStr = remember(item.createdAt) {
        item.createdAt?.let {
            SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(it)
        } ?: "Recent"
    }

    Card(
        shape = ShapeCard,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.70f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Clickable Thumbnail with play overlay
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .aspectRatio(9f / 16f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
                    .clickable(onClick = onPreview)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(WallpaperColorHelper.resolveCoilModel(item.thumbnailUrl))
                        .crossfade(true)
                        .build(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier
                        .size(28.dp)
                        .align(Alignment.Center)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Preview",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details & Actions Column
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    StatusBadge(item = item)
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "By ${item.authorName} • $dateStr",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (item.isRejected && item.reviewNote.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Note: ${item.reviewNote}",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick Preview Button
                    FilledTonalButton(
                        onClick = onPreview,
                        shape = ShapePill,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(imageVector = Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Watch", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Approve Button
                    if (item.isPending || item.isRejected) {
                        Button(
                            onClick = onApprove,
                            enabled = !isProcessing,
                            shape = ShapePill,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(imageVector = Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Approve", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Reject Button
                    if (item.isPending || item.isApproved) {
                        OutlinedButton(
                            onClick = onReject,
                            enabled = !isProcessing,
                            shape = ShapePill,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(imageVector = Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reject", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Delete Button
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteForever,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(item: CommunityWallpaper) {
    val (color, text, icon) = when {
        item.isApproved -> Triple(Color(0xFF81C784), "Live", Icons.Rounded.CheckCircle)
        item.isPending -> Triple(Color(0xFFFFB74D), "Pending", Icons.Rounded.HourglassEmpty)
        else -> Triple(Color(0xFFE57373), "Rejected", Icons.Rounded.Close)
    }

    Surface(
        shape = ShapePill,
        color = color.copy(alpha = 0.18f),
        contentColor = color
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(11.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = text, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}
