package com.lonerxone.siphon.ui.navigation

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.lonerxone.siphon.ui.browser.BrowserScreen
import com.lonerxone.siphon.ui.components.LinkOmnibox
import com.lonerxone.siphon.ui.components.pressable
import com.lonerxone.siphon.ui.home.HomeScreen
import com.lonerxone.siphon.ui.home.HomeViewModel
import com.lonerxone.siphon.ui.home.components.AddLinkBottomSheet
import com.lonerxone.siphon.ui.library.LibraryScreen
import com.lonerxone.siphon.ui.settings.SettingsScreen
import com.lonerxone.siphon.ui.theme.DarkBackground
import com.lonerxone.siphon.ui.theme.DarkBorder
import com.lonerxone.siphon.ui.theme.DarkElevated
import com.lonerxone.siphon.ui.theme.DarkSurface
import com.lonerxone.siphon.ui.theme.OnAccent
import com.lonerxone.siphon.ui.theme.PrimaryGradientStart
import com.lonerxone.siphon.ui.theme.SiphonPrimaryGradient
import com.lonerxone.siphon.ui.theme.TextMuted
import com.lonerxone.siphon.ui.theme.TextPrimary
import com.lonerxone.siphon.ui.theme.TextSecondary
import com.lonerxone.siphon.ui.waitlist.WaitlistScreen

private fun Modifier.blockTouches(): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.forEach { it.consume() }
        }
    }
}

/**
 * Every section is built once, the first time you open it, and then kept alive.
 * Switching sections is instant and each one keeps its scroll position and state.
 */
@Composable
fun SiphonNavHost(sharedUrl: String? = null) {
    val homeVm: HomeViewModel = hiltViewModel()
    val addSheetState by homeVm.addSheetState.collectAsState()
    val activeDownloads by homeVm.activeDownloads.collectAsState()
    val focusManager = LocalFocusManager.current

    val screens = listOf(
        Screen.Home,
        Screen.Waitlist,
        Screen.Browser,
        Screen.Library,
        Screen.Settings
    )

    var current by rememberSaveable { mutableStateOf(Screen.Home.route) }
    val visited = remember { mutableStateListOf<String>() }
    var showQuickAdd by remember { mutableStateOf(false) }

    LaunchedEffect(current) {
        if (!visited.contains(current)) visited.add(current)
    }

    fun select(route: String) {
        focusManager.clearFocus()
        current = route
    }

    LaunchedEffect(sharedUrl) {
        if (!sharedUrl.isNullOrBlank()) {
            current = Screen.Home.route
            homeVm.fetchLinkInfo(sharedUrl)
        }
    }

    BackHandler(enabled = current != Screen.Home.route) {
        select(Screen.Home.route)
    }

    Scaffold(
        containerColor = DarkBackground,
        bottomBar = {
            FloatingDock(
                screens = screens,
                selectedRoute = current,
                homeBadge = activeDownloads.size,
                onSelect = { select(it.route) },
                onAdd = { showQuickAdd = true }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            screens.forEach { screen ->
                val isActive = current == screen.route
                if (isActive || visited.contains(screen.route)) {
                    key(screen.route) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(if (isActive) 1f else 0f)
                                .then(
                                    if (isActive) {
                                        Modifier
                                    } else {
                                        Modifier
                                            .graphicsLayer { alpha = 0f }
                                            .clearAndSetSemantics { }
                                            .blockTouches()
                                    }
                                )
                        ) {
                            when (screen) {
                                Screen.Home -> HomeScreen(onOpenBrowser = { select(Screen.Browser.route) })
                                Screen.Waitlist -> WaitlistScreen()
                                Screen.Browser -> BrowserScreen(
                                    visible = isActive,
                                    onOpenDownloads = { select(Screen.Home.route) }
                                )
                                Screen.Library -> LibraryScreen()
                                Screen.Settings -> SettingsScreen()
                            }
                        }
                    }
                }
            }
        }
    }

    if (showQuickAdd) {
        QuickAddSheet(
            onDismiss = { showQuickAdd = false },
            onSubmit = { url ->
                showQuickAdd = false
                homeVm.fetchLinkInfo(url)
            }
        )
    }

    // one sheet for the whole app, so it never appears twice
    AddLinkBottomSheet(
        uiState = addSheetState,
        onDismiss = { homeVm.dismissSheet() },
        onDownloadNow = { meta, formatId, label -> homeVm.onDownloadNowClicked(meta, formatId, label) },
        onDownloadLater = { meta, formatId, label -> homeVm.downloadLater(meta, formatId, label) },
        onConfirmMobileData = { meta, formatId -> homeVm.confirmMobileDataDownload(meta, formatId) },
        onDownloadSelected = { meta, ids, later -> homeVm.onDownloadSelected(meta, ids, later) }
    )
}

@Composable
private fun FloatingDock(
    screens: List<Screen>,
    selectedRoute: String,
    homeBadge: Int,
    onSelect: (Screen) -> Unit,
    onAdd: () -> Unit
) {
    val dockShape = RoundedCornerShape(32.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(62.dp)
                .clip(dockShape)
                .background(DarkSurface)
                .border(1.dp, DarkBorder, dockShape)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            screens.forEach { screen ->
                DockItem(
                    screen = screen,
                    selected = screen.route == selectedRoute,
                    badge = if (screen == Screen.Home) homeBadge else 0,
                    onClick = { onSelect(screen) }
                )
            }
        }

        Box(
            modifier = Modifier
                .pressable(onClick = onAdd)
                .size(54.dp)
                .clip(CircleShape)
                .background(SiphonPrimaryGradient),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add a download",
                tint = OnAccent,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun DockItem(
    screen: Screen,
    selected: Boolean,
    badge: Int,
    onClick: () -> Unit
) {
    val background by animateColorAsState(
        targetValue = if (selected) DarkElevated else Color.Transparent,
        animationSpec = tween(220),
        label = "dockBackground"
    )

    Row(
        modifier = Modifier
            .pressable(onClick = onClick)
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(background)
            .padding(horizontal = if (selected) 12.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Icon(
                imageVector = screen.icon,
                contentDescription = screen.title,
                tint = if (selected) TextPrimary else TextMuted,
                modifier = Modifier.size(22.dp)
            )
            if (badge > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 7.dp, y = (-5).dp)
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(PrimaryGradientStart),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (badge > 9) "9+" else "$badge",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = OnAccent
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(tween(180)) + expandHorizontally(tween(220)),
            exit = fadeOut(tween(100)) + shrinkHorizontally(tween(160))
        ) {
            Text(
                text = screen.title,
                modifier = Modifier.padding(start = 6.dp),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddSheet(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    val context = LocalContext.current
    var url by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Add a download",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Paste a link. SIPHON finds the video, audio or images.",
                fontSize = 12.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(16.dp))

            LinkOmnibox(
                urlInput = url,
                onUrlChange = { url = it },
                onPaste = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                    if (!text.isNullOrBlank()) url = text
                },
                onInspect = {
                    if (url.isNotBlank()) onSubmit(url.trim())
                }
            )
        }
    }
}