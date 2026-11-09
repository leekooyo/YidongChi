package cn.yidongchi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.yidongchi.app.ui.YidongApp

class MainActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {super.onCreate(savedInstanceState);enableEdgeToEdge();setContent {YidongApp(viewModel())}}
}
