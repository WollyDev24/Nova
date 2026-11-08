package com.livewall.app.ui.screens

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.livewall.app.ui.components.FloatingNavigationBar
import com.livewall.app.ui.components.NavDestination
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.livewall.app.data.CommunityRepository
import com.livewall.app.data.CustomWallpaperManager
import com.livewall.app.data.PlaybackTrigger
import com.livewall.app.data.WallpaperPreferences
import com.livewall.app.model.CommunityWallpaper
import com.livewall.app.model.WallpaperItem
import com.livewall.app.service.LiveWallpaperService
import com.livewall.app.service.WallpaperColorHelper
import com.livewall.app.ui.components.CollectionBannerCard
import com.livewall.app.ui.components.TriggerSelector
import com.livewall.app.ui.theme.LiveWallThemeState
import com.livewall.app.ui.theme.ShapeCard
import com.livewall.app.ui.theme.ShapePill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

data class CollectionItem(
    val id: String,
    val name: String,
    val subtitle: String,
    val coverUri: String,
    val items: List<WallpaperItem>
)

enum class MainNavTab(val label: String) {
    CURATED("Curated OEM"),
    COMMUNITY("Community Hub")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    prefs: WallpaperPreferences,
    themeState: LiveWallThemeState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val communityRepo = remember { CommunityRepository(context) }

    val navDestinations = remember {
        listOf(
            NavDestination(
                label = "Wallpapers",
                selectedIcon = Icons.Rounded.Wallpaper,
                unselectedIcon = Icons.Outlined.Wallpaper
            ),
            NavDestination(
                label = "Community",
                selectedIcon = Icons.Rounded.Explore,
                unselectedIcon = Icons.Outlined.Explore
            )
        )
    }

    var currentTab by remember { mutableStateOf(MainNavTab.CURATED) }
    var showUploadSheet by remember { mutableStateOf(false) }
    var isSearchOpen by remember { mutableStateOf(false) }
    var communitySearchQuery by remember { mutableStateOf("") }

    LaunchedEffect(currentTab) {
        if (currentTab != MainNavTab.COMMUNITY) {
            isSearchOpen = false
            communitySearchQuery = ""
        }
    }

    BackHandler(enabled = isSearchOpen) {
        isSearchOpen = false
        communitySearchQuery = ""
    }

    var activeWallpaperId by remember { mutableStateOf(prefs.selectedWallpaperId) }
    var activeTrigger by remember { mutableStateOf(prefs.trigger) }
    var previewWallpaper by remember { mutableStateOf<WallpaperItem?>(null) }
    var selectedCollection by remember { mutableStateOf<CollectionItem?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }

    val customWallpapers = remember {
        mutableStateListOf<WallpaperItem>().apply { addAll(prefs.getCustomWallpapers()) }
    }

    val allWallpapers = remember(customWallpapers.size) {
        WallpaperItem.BUNDLED_WALLPAPERS + customWallpapers
    }

    val activeWallpaper = remember(activeWallpaperId, allWallpapers) {
        allWallpapers.find { it.id == activeWallpaperId } ?: WallpaperItem.DEFAULT_WALLPAPER
    }

    // Collections grouped per user request: Banner cards with their respective name (e.g. "ColorOS", "Xiaomi 18 Pro Max")
    val collections = remember(allWallpapers) {
        listOf(
            CollectionItem(
                id = "coloros",
                name = "ColorOS",
                subtitle = "Fluid Silk Wave Flow Animation",
                coverUri = "file:///android_asset/thumbnails/ColorOS/ColorOS17.webp",
                items = allWallpapers.filter { it.collection == "ColorOS" }
            ),
            CollectionItem(
                id = "xiaomi_18_pro_max",
                name = "Xiaomi 18 Pro Max",
                subtitle = "5 Geometric Refraction Super Wallpapers",
                coverUri = "file:///android_asset/thumbnails/Xiaomi-18-Pro-Max/wallpaper_WH.webp",
                items = allWallpapers.filter { it.collection == "Xiaomi 18 Pro Max" }
            ),
            CollectionItem(
                id = "xiaomi_18_pro",
                name = "Xiaomi 18 Pro",
                subtitle = "5 Dynamic Light Flare Super Wallpapers",
                coverUri = "file:///android_asset/thumbnails/Xiaomi-18-Pro/Light01.webp",
                items = allWallpapers.filter { it.collection == "Xiaomi 18 Pro" }
            ),
            CollectionItem(
                id = "custom",
                name = "Custom Videos",
                subtitle = if (customWallpapers.isEmpty()) "Import your own MP4 videos" else "${customWallpapers.size} custom video wallpapers",
                coverUri = if (customWallpapers.isNotEmpty()) customWallpapers[0].thumbnailUri else "file:///android_asset/thumbnails/ColorOS/ColorOS17.webp",
                items = customWallpapers
            )
        )
    }

    // Video picker launcher for importing custom MP4 videos
    val pickVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isImporting = true
            scope.launch(Dispatchers.IO) {
                val item = CustomWallpaperManager.importVideo(context, uri)
                withContext(Dispatchers.Main) {
                    isImporting = false
                    if (item != null) {
                        prefs.addCustomWallpaper(item)
                        customWallpapers.add(0, item)
                        previewWallpaper = item
                        Toast.makeText(context, "Custom video imported!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to import video", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun applyLiveWallpaper(item: WallpaperItem) {
        if (item.videoUri.startsWith("http://") || item.videoUri.startsWith("https://")) {
            Toast.makeText(context, "Downloading community wallpaper...", Toast.LENGTH_SHORT).show()
            scope.launch(Dispatchers.IO) {
                try {
                    val communityDir = File(context.filesDir, "community_videos").apply { mkdirs() }
                    val destinationFile = File(communityDir, "${item.id}.mp4")
                    if (!destinationFile.exists() || destinationFile.length() == 0L) {
                        val connection = URL(item.videoUri).openConnection()
                        connection.connectTimeout = 15_000
                        connection.readTimeout = 30_000
                        connection.getInputStream().use { input ->
                            FileOutputStream(destinationFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    val localItem = item.copy(videoUri = destinationFile.absolutePath)
                    withContext(Dispatchers.Main) {
                        applyLiveWallpaper(localItem)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            return
        }

        prefs.selectWallpaper(item)
        activeWallpaperId = item.id
        // Sync static bitmap in background for Samsung One UI & system Color Palette
        scope.launch(Dispatchers.IO) {
            WallpaperColorHelper.syncStaticBitmapForSamsungOneUI(context, item)
        }
        try {
            val serviceClass = WallpaperColorHelper.getServiceClassForWallpaper(item.id)
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(context, serviceClass)
                )
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallbackIntent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
                context.startActivity(fallbackIntent)
            } catch (_: Exception) {
                Toast.makeText(context, "Selected \"${item.title}\"", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchOpen && currentTab == MainNavTab.COMMUNITY) {
                        val focusRequester = remember { FocusRequester() }
                        LaunchedEffect(Unit) {
                            focusRequester.requestFocus()
                        }
                        TextField(
                            value = communitySearchQuery,
                            onValueChange = { communitySearchQuery = it },
                            placeholder = {
                                Text(
                                    "Search community wallpapers...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "LiveWall",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = ShapePill,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Text(
                                    text = if (currentTab == MainNavTab.CURATED) "Super Wallpapers" else "Community Hub",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (isSearchOpen && currentTab == MainNavTab.COMMUNITY) {
                        IconButton(
                            onClick = {
                                isSearchOpen = false
                                communitySearchQuery = ""
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Close search",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                actions = {
                    if (isSearchOpen && currentTab == MainNavTab.COMMUNITY) {
                        if (communitySearchQuery.isNotEmpty()) {
                            IconButton(onClick = { communitySearchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        if (currentTab == MainNavTab.CURATED) {
                            FilledTonalIconButton(
                                onClick = {
                                    pickVideoLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                },
                                shape = CircleShape
                            ) {
                                Icon(imageVector = Icons.Rounded.Add, contentDescription = "Import Video")
                            }
                        } else {
                            FilledTonalIconButton(
                                onClick = { isSearchOpen = true },
                                shape = CircleShape
                            ) {
                                Icon(imageVector = Icons.Rounded.Search, contentDescription = "Search Community Wallpapers")
                            }
                            FilledTonalIconButton(
                                onClick = { showUploadSheet = true },
                                shape = CircleShape
                            ) {
                                Icon(imageVector = Icons.Rounded.CloudUpload, contentDescription = "Publish to Community")
                            }
                        }

                        FilledTonalIconButton(
                            onClick = { showSettings = true },
                            shape = CircleShape
                        ) {
                            Icon(imageVector = Icons.Rounded.Settings, contentDescription = "Settings")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            FloatingNavigationBar(
                destinations = navDestinations,
                currentTab = currentTab.ordinal,
                onSelect = { index ->
                    currentTab = MainNavTab.entries[index]
                }
            )
        }
    ) { innerPadding ->
        if (currentTab == MainNavTab.CURATED) {
            // Clean, uncluttered Home layout with Collections Banner Cards
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Active Wallpaper Showcase
                item {
                    HeroWallpaperCard(
                        wallpaper = activeWallpaper,
                        trigger = activeTrigger,
                        onPreviewClick = { previewWallpaper = activeWallpaper }
                    )
                }

                // Section Header: Collections
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Collections",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${collections.size} Categories",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Stacked Collection Banner Cards (e.g. "ColorOS", "Xiaomi 18 Pro Max", etc.)
                items(collections.size, key = { collections[it].id }) { index ->
                    val col = collections[index]
                    CollectionBannerCard(
                        collectionName = col.name,
                        subtitle = col.subtitle,
                        coverImageUri = col.coverUri,
                        wallpaperCount = col.items.size,
                        containsActive = col.items.any { it.id == activeWallpaperId },
                        onClick = { selectedCollection = col }
                    )
                }
            }
        } else {
            // Community Hub Feed
            CommunityScreen(
                repository = communityRepo,
                onWallpaperSelected = { communityItem ->
                    previewWallpaper = communityItem.toWallpaperItem()
                },
                onOpenUpload = { showUploadSheet = true },
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 12.dp
                ),
                searchQuery = communitySearchQuery
            )
        }
    }

    // Modal Collection Sheet: Displays wallpapers for the tapped collection banner
    selectedCollection?.let { col ->
        CollectionSheet(
            collectionName = col.name,
            wallpapers = col.items,
            activeWallpaperId = activeWallpaperId,
            onDismiss = { selectedCollection = null },
            onWallpaperSelected = { item ->
                previewWallpaper = item
            }
        )
    }

    // Modal Preview Bottom Sheet (Supports horizontal swipe scrolling between wallpapers)
    previewWallpaper?.let { item ->
        val previewList = selectedCollection?.items ?: allWallpapers
        PreviewSheet(
            initialWallpaper = item,
            wallpapers = previewList,
            prefs = prefs,
            onDismiss = { previewWallpaper = null },
            onApplied = { appliedItem ->
                activeWallpaperId = appliedItem.id
                activeTrigger = prefs.trigger
                previewWallpaper = null
                selectedCollection = null
            }
        )
    }

    // Modal Settings Bottom Sheet
    if (showSettings) {
        SettingsSheet(
            prefs = prefs,
            themeState = themeState,
            onDismiss = {
                showSettings = false
                activeTrigger = prefs.trigger
            }
        )
    }

    // Modal Upload to Community Sheet
    if (showUploadSheet) {
        UploadWallpaperSheet(
            repository = communityRepo,
            onDismiss = { showUploadSheet = false },
            onUploaded = { newWallpaper ->
                showUploadSheet = false
                previewWallpaper = newWallpaper.toWallpaperItem()
            }
        )
    }

    // Loading overlay when importing custom video
    if (isImporting) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = ShapeCard,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(32.dp)
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Text("Importing & optimizing video...", fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun HeroWallpaperCard(
    wallpaper: WallpaperItem,
    trigger: PlaybackTrigger,
    onPreviewClick: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCard),
        shape = ShapeCard,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            // Background image with dark gradient
            AsyncImage(
                model = wallpaper.thumbnailUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.85f),
                                Color.Black.copy(alpha = 0.55f),
                                Color.Black.copy(alpha = 0.30f)
                            )
                        )
                    )
            )

            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = ShapePill,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(imageVector = Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text(
                                text = "ACTIVE SELECTION",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        shape = ShapePill,
                        color = Color.Black.copy(alpha = 0.6f),
                        contentColor = Color.White
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (trigger == PlaybackTrigger.SCREEN_ON) Icons.Rounded.WbSunny else Icons.Rounded.LockOpen,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = trigger.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Bottom title & preview button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = wallpaper.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Text(
                            text = "${wallpaper.collection} • ${wallpaper.durationSec}s animation",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }

                    FilledTonalButton(
                        onClick = onPreviewClick,
                        shape = ShapePill,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color.White.copy(alpha = 0.25f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Preview", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
