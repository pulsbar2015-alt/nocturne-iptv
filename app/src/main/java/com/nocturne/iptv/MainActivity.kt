package com.nocturne.iptv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nocturne.iptv.data.Channel
import com.nocturne.iptv.player.PlayerActivity
import com.nocturne.iptv.ui.NocturneViewModel
import com.nocturne.iptv.ui.components.HorrorOverlay
import com.nocturne.iptv.ui.screens.AddPlaylistScreen
import com.nocturne.iptv.ui.screens.HomeScreen
import com.nocturne.iptv.ui.screens.IntroScreen
import com.nocturne.iptv.ui.screens.SettingsScreen
import com.nocturne.iptv.ui.screens.UrlHandlerScreen
import com.nocturne.iptv.ui.theme.NocturnePalette
import com.nocturne.iptv.ui.theme.NocturneTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NocturneTheme {
                NocturneRoot()
            }
        }
    }
}

@Composable
private fun NocturneRoot() {
    val context = LocalContext.current
    val app = context.applicationContext as NocturneApp
    val viewModel: NocturneViewModel = viewModel()
    val state by viewModel.state.collectAsState()

    // The intro plays once per launch, not on every configuration change.
    var introDone by rememberSaveable { mutableStateOf(false) }

    val navController = rememberNavController()

    fun play(channel: Channel) {
        // The browsing list becomes the zap queue.
        app.playbackSession.start(state.channels, state.channels.indexOf(channel))
        context.startActivity(Intent(context, PlayerActivity::class.java))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NocturnePalette.Abyss)
    ) {
        NavHost(
            navController = navController,
            startDestination = "home"
        ) {
            composable("home") {
                HomeScreen(
                    state = state,
                    onPlay = { play(it) },
                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                    onAddPlaylist = { navController.navigate("add") },
                    onLoadDefault = { viewModel.importDefault() },
                    onOpenGuide = { navController.navigate("settings") },
                    onOpenSettings = { navController.navigate("settings") },
                    onUrl = { navController.navigate("url") }
                )
            }

            composable("add") {
                AddPlaylistScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onImportUrl = { url, label, epg -> viewModel.importFromUrl(url, label, epg) },
                    onImportText = { text, label, epg -> viewModel.importFromText(text, label, epg) },
                    onImportUri = { uri, label, epg -> viewModel.importFromUri(uri, label, epg) },
                    onOpenPlaylist = { source ->
                        viewModel.openPlaylist(source)
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = false }
                        }
                    },
                    onRemovePlaylist = { viewModel.removePlaylist(it) },
                    onClearError = { viewModel.clearError() }
                )
            }

            composable("settings") {
                SettingsScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onAddPlaylist = {
                        navController.navigate("add") {
                            popUpTo("settings") { inclusive = true }
                        }
                    },
                    onLoadEpg = { viewModel.loadEpg(it) },
                    onOpenGuide = {
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = false }
                        }
                    }
                )
            }

            composable("url") {
                UrlHandlerScreen(onBack = { navController.popBackStack() })
            }
        }

        HorrorOverlay(
            modifier = Modifier.fillMaxSize(),
            vignetteStrength = 0.5f,
            scanlineAlpha = 0.10f
        )

        if (!introDone) {
            IntroScreen(onFinished = { introDone = true })
        }
    }
}