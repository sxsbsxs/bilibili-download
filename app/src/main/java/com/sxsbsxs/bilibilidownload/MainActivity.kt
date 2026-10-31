package com.sxsbsxs.bilibilidownload

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.sxsbsxs.bilibilidownload.databinding.ActivityMainBinding
import com.sxsbsxs.bilibilidownload.feature.home.HomeScreen

class MainActivity : ComponentActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.composeContainer.setContent {
            HomeScreen(applicationContext)
        }
    }
}
