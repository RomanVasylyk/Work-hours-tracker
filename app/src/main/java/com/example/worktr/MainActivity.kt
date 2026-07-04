package com.example.worktr

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.os.LocaleListCompat
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupWithNavController
import com.example.worktr.databinding.ActivityMainBinding
import com.example.worktr.util.LanguagePreferences
import com.example.worktr.widget.MonthSummaryWidgetProvider
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var appBarConfiguration: AppBarConfiguration
    private var updatingBottomSelection = false
    private val navController by lazy {
        (supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment).navController
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(LanguagePreferences.appLanguage(this).code)
        )
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.jobListFragment,
                R.id.monthOverviewFragment,
                R.id.statsFragment,
                R.id.invoiceArchiveFragment,
                R.id.settingsFragment
            )
        )
        binding.toolbar.setupWithNavController(navController, appBarConfiguration)
        setupBottomNavigation()
        applyWindowInsets()
        MonthSummaryWidgetProvider.enqueueRefresh(this)
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            if (updatingBottomSelection) return@setOnItemSelectedListener true
            when (item.itemId) {
                R.id.nav_jobs -> navigateTopLevel(R.id.jobListFragment)
                R.id.nav_month -> navigateTopLevel(R.id.monthOverviewFragment)
                R.id.nav_stats -> navigateTopLevel(
                    R.id.statsFragment,
                    bundleOf("jobId" to -1)
                )
                R.id.nav_invoices -> navigateTopLevel(R.id.invoiceArchiveFragment)
                R.id.nav_settings -> navigateTopLevel(R.id.settingsFragment)
                else -> return@setOnItemSelectedListener false
            }
            true
        }
        binding.bottomNav.setOnItemReselectedListener { item ->
            when (item.itemId) {
                R.id.nav_jobs -> navController.popBackStack(R.id.jobListFragment, false)
                R.id.nav_settings -> navController.popBackStack(R.id.settingsFragment, false)
            }
        }
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val selectedItemId = when (destination.id) {
                R.id.jobListFragment,
                R.id.jobDetailFragment,
                R.id.addEntryFragment -> R.id.nav_jobs
                R.id.monthOverviewFragment -> R.id.nav_month
                R.id.statsFragment -> R.id.nav_stats
                R.id.invoiceArchiveFragment -> R.id.nav_invoices
                R.id.settingsFragment,
                R.id.clientsFragment -> R.id.nav_settings
                else -> null
            }
            selectedItemId?.let {
                if (binding.bottomNav.selectedItemId != it) {
                    updatingBottomSelection = true
                    binding.bottomNav.selectedItemId = it
                    updatingBottomSelection = false
                }
            }
            binding.bottomNav.visibility = View.VISIBLE
            binding.toolbar.title = titleForDestination(destination.id)
        }
    }

    private fun navigateTopLevel(destinationId: Int, args: Bundle? = null) {
        if (navController.currentDestination?.id == destinationId) return
        if (destinationId == R.id.jobListFragment) {
            navController.popBackStack(R.id.jobListFragment, false)
            return
        }
        val options = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setPopUpTo(R.id.jobListFragment, false)
            .build()
        navController.navigate(destinationId, args, options)
    }

    private fun titleForDestination(destinationId: Int): String = when (destinationId) {
        R.id.jobListFragment -> getString(R.string.jobs_section_title)
        R.id.jobDetailFragment -> getString(R.string.job_detail_title)
        R.id.addEntryFragment -> getString(R.string.add_entry)
        R.id.monthOverviewFragment -> getString(R.string.current_month_title)
        R.id.statsFragment -> getString(R.string.stats_title)
        R.id.invoiceArchiveFragment -> getString(R.string.invoice_archive_title)
        R.id.settingsFragment -> getString(R.string.settings_title)
        R.id.clientsFragment -> getString(R.string.clients_title)
        else -> getString(R.string.app_name)
    }

    private fun applyWindowInsets() {
        val appBarTop = binding.appBar.paddingTop
        val appBarLeft = binding.appBar.paddingLeft
        val appBarRight = binding.appBar.paddingRight
        val navBottom = binding.navHost.paddingBottom
        val navLeft = binding.navHost.paddingLeft
        val navRight = binding.navHost.paddingRight
        val bottomNavLeft = binding.bottomNav.paddingLeft
        val bottomNavRight = binding.bottomNav.paddingRight
        val bottomNavBottom = binding.bottomNav.paddingBottom

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
            binding.bottomNav.updatePadding(
                left = bottomNavLeft + safeInsets.left,
                right = bottomNavRight + safeInsets.right,
                bottom = bottomNavBottom + safeInsets.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }
}
