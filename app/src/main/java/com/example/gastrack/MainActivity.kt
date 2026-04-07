package com.example.gastrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.gastrack.data.FuelRepository
import com.example.gastrack.location.LocationHelper
import com.example.gastrack.network.SyncService
import com.example.gastrack.ui.AddEntryScreen
import com.example.gastrack.ui.DetailScreen
import com.example.gastrack.ui.HistoryScreen
import com.example.gastrack.ui.SettingsScreen
import com.example.gastrack.ui.StatsScreen
import com.example.gastrack.ui.theme.GasTrackTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var repository: FuelRepository
    private lateinit var locationHelper: LocationHelper
    private lateinit var syncService: SyncService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = FuelRepository(applicationContext)
        locationHelper = LocationHelper(applicationContext)
        syncService = SyncService(applicationContext, repository)
        enableEdgeToEdge()
        setContent {
            GasTrackTheme {
                GasTrackApp(
                    repository = repository,
                    locationHelper = locationHelper,
                    syncService = syncService
                )
            }
        }
    }
}

@Composable
fun GasTrackApp(
    repository: FuelRepository,
    locationHelper: LocationHelper,
    syncService: SyncService,
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })
    var detailEntryId by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var historyRefreshTrigger by remember { mutableStateOf(0) }

    when {
        showSettings -> Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
            SettingsScreen(
                syncService = syncService,
                repository = repository,
                onBack = { showSettings = false },
                onImportDone = { historyRefreshTrigger++ },
                modifier = Modifier.padding(padding)
            )
        }
        detailEntryId != null -> Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
            DetailScreen(
                entryId = detailEntryId!!,
                repository = repository,
                onBack = { detailEntryId = null },
                modifier = Modifier.padding(padding)
            )
        }
        else -> Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar {
                    listOf("Add", "Stats", "History").forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            label = { Text(title) },
                            icon = {}
                        )
                    }
                }
            }
        ) { padding ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) { page ->
                when (page) {
                    0 -> AddEntryScreen(
                        repository = repository,
                        locationHelper = locationHelper,
                        syncService = syncService,
                        modifier = Modifier.fillMaxSize()
                    )
                    1 -> StatsScreen(
                        repository = repository,
                        modifier = Modifier.fillMaxSize()
                    )
                    else -> HistoryScreen(
                        repository = repository,
                        onEntryClick = { entry -> detailEntryId = entry.id },
                        onSettingsClick = { showSettings = true },
                        refreshTrigger = historyRefreshTrigger,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
