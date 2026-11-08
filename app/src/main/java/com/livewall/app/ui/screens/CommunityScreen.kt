package com.livewall.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livewall.app.data.CommunityRepository
import com.livewall.app.model.CommunityWallpaper
import androidx.compose.ui.graphics.vector.ImageVector
import com.livewall.app.ui.components.CommunityWallpaperCard
import com.livewall.app.ui.theme.ShapeCard
import com.livewall.app.ui.theme.ShapePill
import kotlinx.coroutines.launch

enum class CommunityFeedFilter(
    val label: String,
    val icon: ImageVector
) {
    TRENDING("Trending", Icons.Rounded.LocalFireDepartment),
    LATEST("Newest", Icons.Rounded.AutoAwesome)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CommunityScreen(
    repository: CommunityRepository,
    onWallpaperSelected: (CommunityWallpaper) -> Unit,
    onOpenUpload: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    searchQuery: String = ""
) {
    val scope = rememberCoroutineScope()

    var activeFilter by remember { mutableStateOf(CommunityFeedFilter.TRENDING) }
    var isLoading by remember { mutableStateOf(false) }
    var wallpapers by remember { mutableStateOf<List<CommunityWallpaper>>(emptyList()) }
    val likedMap = remember { mutableStateMapOf<String, Boolean>() }

    fun loadWallpapers() {
        isLoading = true
        scope.launch {
            val result = if (activeFilter == CommunityFeedFilter.TRENDING) {
                repository.getTrendingWallpapers()
            } else {
                repository.getRecentWallpapers()
            }

            result.onSuccess { list ->
                wallpapers = if (list.isNotEmpty()) {
                    list
                } else {
                    // Seeded fallback demonstration cards if Firestore collection is newly created
                    DEMO_COMMUNITY_WALLPAPERS
                }
                isLoading = false
            }.onFailure {
                // If offline or Firebase credentials pending, show high quality demo cards
                wallpapers = DEMO_COMMUNITY_WALLPAPERS
                isLoading = false
            }
        }
    }

    LaunchedEffect(activeFilter) {
        loadWallpapers()
    }

    val filteredList = remember(wallpapers, searchQuery) {
        if (searchQuery.isBlank()) {
            wallpapers
        } else {
            wallpapers.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.authorName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Filter Chips Row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CommunityFeedFilter.entries.forEach { filter ->
                    val selected = activeFilter == filter
                    ElevatedFilterChip(
                        selected = selected,
                        onClick = { activeFilter = filter },
                        label = { Text(filter.label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
                        leadingIcon = {
                            Icon(
                                imageVector = filter.icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        shape = ShapePill,
                        colors = FilterChipDefaults.elevatedFilterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = { loadWallpapers() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh feed",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Loading Indicator
        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                }
            }
        }

        // Wallpapers 2-column Grid
        if (!isLoading && filteredList.isNotEmpty()) {
            val chunked = filteredList.chunked(2)
            items(chunked.size) { rowIndex ->
                val rowWallpapers = chunked[rowIndex]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowWallpapers.forEach { item ->
                        val isLiked = likedMap[item.id] ?: false
                        Box(modifier = Modifier.weight(1f)) {
                            CommunityWallpaperCard(
                                wallpaper = item,
                                isLiked = isLiked,
                                onLikeClick = {
                                    val nowLiked = !isLiked
                                    likedMap[item.id] = nowLiked
                                    scope.launch {
                                        repository.toggleLike(item.id)
                                    }
                                },
                                onClick = { onWallpaperSelected(item) }
                            )
                        }
                    }
                    if (rowWallpapers.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Empty state
        if (!isLoading && filteredList.isEmpty()) {
            item {
                Surface(
                    shape = ShapeCard,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Explore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = "No Wallpapers Found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Be the first creator to publish a live wallpaper to the community!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = onOpenUpload,
                            shape = ShapePill
                        ) {
                            Icon(imageVector = Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Upload Your Wallpaper", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// Demo community wallpapers to populate the feed immediately out of the box
private val DEMO_COMMUNITY_WALLPAPERS = listOf(
    CommunityWallpaper(
        id = "demo_aurora_drift",
        title = "Northern Borealis",
        authorName = "NovaDesign",
        videoUrl = "asset:///wallpapers/Xiaomi-18-Pro-Max/wallpaper_CY.mp4",
        thumbnailUrl = "file:///android_asset/thumbnails/Xiaomi-18-Pro-Max/wallpaper_CY.webp",
        primaryColorHex = "#9C27B0",
        secondaryColorHex = "#E1BEE7",
        tertiaryColorHex = "#BA68C8",
        durationSec = 2.0f,
        likesCount = 428,
        downloadsCount = 1890
    ),
    CommunityWallpaper(
        id = "demo_silk_wave",
        title = "Silk Nebula Flow",
        authorName = "SuperHyper",
        videoUrl = "asset:///wallpapers/ColorOS/ColorOS17.mp4",
        thumbnailUrl = "file:///android_asset/thumbnails/ColorOS/ColorOS17.webp",
        primaryColorHex = "#1E88E5",
        secondaryColorHex = "#BBDEFB",
        tertiaryColorHex = "#64B5F6",
        durationSec = 3.0f,
        likesCount = 612,
        downloadsCount = 2410
    ),
    CommunityWallpaper(
        id = "demo_quartz_prism",
        title = "Prism Light Refract",
        authorName = "KiroLabs",
        videoUrl = "asset:///wallpapers/Xiaomi-18-Pro/Light01.mp4",
        thumbnailUrl = "file:///android_asset/thumbnails/Xiaomi-18-Pro/Light01.webp",
        primaryColorHex = "#FFB300",
        secondaryColorHex = "#FFE082",
        tertiaryColorHex = "#FFCA28",
        durationSec = 3.0f,
        likesCount = 380,
        downloadsCount = 1205
    ),
    CommunityWallpaper(
        id = "demo_obsidian_depths",
        title = "Obsidian Amber Core",
        authorName = "ZenithOS",
        videoUrl = "asset:///wallpapers/Xiaomi-18-Pro-Max/wallpaper_BK.mp4",
        thumbnailUrl = "file:///android_asset/thumbnails/Xiaomi-18-Pro-Max/wallpaper_BK.webp",
        primaryColorHex = "#E65100",
        secondaryColorHex = "#FFE0B2",
        tertiaryColorHex = "#FFB74D",
        durationSec = 2.0f,
        likesCount = 514,
        downloadsCount = 1750
    )
)
