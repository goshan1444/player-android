package com.player.hifi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.player.hifi.core.designsystem.PlayerTheme
import com.player.hifi.ui.PlayerAppShell
import com.player.hifi.ui.PlayerViewModel

class MainActivity : ComponentActivity() {
    private var viewModelRef: PlayerViewModel? = null

    override fun onResume() {
        super.onResume()
        viewModelRef?.let { vm ->
            if (vm.hasAllFilesAccess()) {
                vm.rescanLibrary()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PlayerApp).container

        setContent {
            val vm: PlayerViewModel = viewModel(factory = PlayerViewModel.Factory(container))
            viewModelRef = vm
            val settings by vm.settings.collectAsStateWithLifecycle()

            PlayerTheme(
                themeMode = settings.themeMode,
                useAmoledDark = settings.useAmoledDark,
                useDynamicColor = settings.useDynamicColor
            ) {
                PlayerAppShell(viewModel = vm)
            }
        }
    }
}