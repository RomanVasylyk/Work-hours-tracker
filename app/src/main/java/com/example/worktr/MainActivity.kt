package com.example.worktr

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.worktr.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val navController by lazy {
        (supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment).navController
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setupWithNavController(navController)
        applyWindowInsets()
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    private fun applyWindowInsets() {
        val appBarTop = binding.appBar.paddingTop
        val appBarLeft = binding.appBar.paddingLeft
        val appBarRight = binding.appBar.paddingRight
        val navBottom = binding.navHost.paddingBottom
        val navLeft = binding.navHost.paddingLeft
        val navRight = binding.navHost.paddingRight

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val safeInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            binding.appBar.updatePadding(
                left = appBarLeft + safeInsets.left,
                top = appBarTop + safeInsets.top,
                right = appBarRight + safeInsets.right
            )
            binding.navHost.updatePadding(
                left = navLeft + safeInsets.left,
                right = navRight + safeInsets.right,
                bottom = navBottom + safeInsets.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }
}
