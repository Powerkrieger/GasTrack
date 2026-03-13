package com.example.gastrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.gastrack.data.FuelRepository
import com.example.gastrack.location.LocationHelper
import com.example.gastrack.ui.AddEntryScreen
import com.example.gastrack.ui.DetailScreen
import com.example.gastrack.ui.HistoryScreen
import com.example.gastrack.ui.Screen
import com.example.gastrack.ui.StatsScreen
import com.example.gastrack.ui.theme.GasTrackTheme

class MainActivity : ComponentActivity() {

    private lateinit var repository: FuelRepository
    private lateinit var locationHelper: LocationHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = FuelRepository(applicationContext)
        locationHelper = LocationHelper(applicationContext)
        enableEdgeToEdge()
        setContent {
            GasTrackTheme {
                GasTrackApp(repository = repository, locationHelper = locationHelper)
            }
        }
    }
}

@Composable
fun GasTrackApp(repository: FuelRepository, locationHelper: LocationHelper) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.AddEntry) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    when (val screen = currentScreen) {
        is Screen.Detail -> {
            Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                DetailScreen(
                    entryId = screen.entryId,
                    repository = repository,
                    onBack = {
                        currentScreen = Screen.History
                        selectedTab = 1
                    },
                    modifier = Modifier.padding(padding)
                )
            }
        }
        else -> {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0; currentScreen = Screen.AddEntry },
                            label = { Text("Add") },
                            icon = {}
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1; currentScreen = Screen.History },
                            label = { Text("History") },
                            icon = {}
                        )
                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2; currentScreen = Screen.Stats },
                            label = { Text("Stats") },
                            icon = {}
                        )
                    }
                }
            ) { padding ->
                when (screen) {
                    is Screen.AddEntry -> AddEntryScreen(
                        repository = repository,
                        locationHelper = locationHelper,
                        modifier = Modifier.padding(padding)
                    )
                    is Screen.History -> HistoryScreen(
                        repository = repository,
                        onEntryClick = { entry -> currentScreen = Screen.Detail(entry.id) },
                        modifier = Modifier.padding(padding)
                    )
                    is Screen.Stats -> StatsScreen(
                        repository = repository,
                        modifier = Modifier.padding(padding)
                    )
                    else -> {}
                }
            }
        }
    }
}
