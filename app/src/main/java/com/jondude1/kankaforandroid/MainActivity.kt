package com.jondude1.kankaforandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.jondude1.kankaforandroid.ui.AppVm
import com.jondude1.kankaforandroid.ui.KankaTheme
import com.jondude1.kankaforandroid.ui.Root

class MainActivity : ComponentActivity() {
    private val vm: AppVm by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KankaTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App()
                }
            }
        }
    }

    @Composable
    private fun App() {
        BackHandler(enabled = vm.stack.size > 1) { vm.pop() }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (vm.prefs.loggedIn) vm.checkForUpdate(force = false) }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) { Root(vm) }
    }
}
