package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.AppLanguage
import com.example.ui.MainScreen
import com.example.ui.MainViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val currentLanguage by viewModel.currentLanguage.collectAsState()
            val baseContext = LocalContext.current
            val localizedContext = AppLanguage.createLocalizedContext(baseContext, currentLanguage)

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalLayoutDirection provides currentLanguage.layoutDirection
            ) {
                MyApplicationTheme {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }
}


