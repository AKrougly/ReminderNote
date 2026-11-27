package com.example.remindernote

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.remindernote.databinding.ActivityMainBinding
import com.google.android.material.tabs.TabLayout
import java.time.LocalDate

/** Аналог главного экрана, но только с карточками в статусе «Завершено» (без кнопки добавления). */
class ArchiveActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val adapter = ReminderAdapter { reminder ->
        startActivity(Intent(this, CardActivity::class.java).putExtra(CardActivity.EXTRA_ID, reminder.id))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.title_archive)

        binding.fab.visibility = View.GONE
        binding.empty.setText(R.string.empty_archive)
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
    }

    override fun onResume() {
        super.onResume()
        ReminderRepository.recalcAll(this, LocalDate.now())
        refresh()
    }

    private fun refresh() {
        val selected = binding.tabs.selectedTabPosition.coerceAtLeast(0)
        val items = ReminderRepository.getAll(this).toItems(LocalDate.now(), selected, archive = true)
        adapter.submitList(items)
        binding.empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
