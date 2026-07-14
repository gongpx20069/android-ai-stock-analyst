package com.gongpx.aistockanalyst

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.gongpx.aistockanalyst.designsystem.theme.AiStockAnalystTheme
import com.gongpx.aistockanalyst.ui.AnalystApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AiStockAnalystTheme {
                AnalystApp()
            }
        }
    }
}
