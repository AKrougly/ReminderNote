package com.example.remindernote

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.remindernote.databinding.ActivityMainBinding
import com.google.android.material.tabs.TabLayout
import java.time.LocalDate

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val adapter = ReminderAdapter { openCard(it.id) }

    private val cardLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null && data.getBooleanExtra(CardActivity.EXTRA_ADDED, false)) {
            // После добавления показываем вкладку, в которую попала новая карточка.
            val id = data.getLongExtra(CardActivity.EXTRA_ID, -1L)
            val reminder = ReminderRepository.get(this, id)
            val bucket = reminder?.schedule(LocalDate.now())?.bucket
            val position = if (bucket == null) 0 else bucket.ordinal + 1
            binding.tabs.getTabAt(position)?.select()
        }
        refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        setSupportActionBar(binding.toolbar)

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        listOf(
            R.string.tab_all, R.string.tab_today, R.string.tab_week, R.string.tab_month, R.string.tab_year
        ).forEach { binding.tabs.addTab(binding.tabs.newTab().setText(it)) }

        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = refresh()
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        // Одна кнопка «+» видна на каждой вкладке.
        binding.fab.setOnClickListener {
            cardLauncher.launch(Intent(this, CardActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        ReminderRepository.recalcAll(this, LocalDate.now())
        refresh()
    }

    private fun openCard(id: Long) {
        cardLauncher.launch(Intent(this, CardActivity::class.java).putExtra(CardActivity.EXTRA_ID, id))
    }

    private fun refresh() {
        val selected = binding.tabs.selectedTabPosition.coerceAtLeast(0)
        val items = ReminderRepository.getAll(this).toItems(LocalDate.now(), selected, archive = false)
        adapter.submitList(items)
        binding.empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_refresh -> {
            val changed = ReminderRepository.recalcAll(this, LocalDate.now())
            refresh()
            Toast.makeText(this, getString(R.string.refreshed, changed), Toast.LENGTH_SHORT).show()
            true
        }
        R.id.action_archive -> {
            startActivity(Intent(this, ArchiveActivity::class.java))
            true
        }
        R.id.action_settings -> {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }
        R.id.action_about -> {
            startActivity(Intent(this, AboutActivity::class.java))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
