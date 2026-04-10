package com.mochame.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProofOfLifeScreen(viewModel: ProofOfLifeViewModel) {
    // Collecting flows from the Database (via the ViewModel)
    val categories by viewModel.categories.collectAsState()
    val dailyContext by viewModel.dailyContext.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Mocha Me: Kernel Test", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    scrolledContainerColor = Color.Unspecified,
                    navigationIconContentColor = Color.Unspecified,
                    titleContentColor = Color.Unspecified,
                    actionIconContentColor = Color.Unspecified
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                viewModel.addTestData()
                viewModel.initializeDailyContext(6.5,3)
            }) {
                Icon(Icons.Default.Add, contentDescription = "Inject Data")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // --- SECTION 1: THE DENOMINATOR (Bio Context) ---
            Text("Biological Denominator", style = MaterialTheme.typography.labelLarge)
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1.0f)) {
                        Text("Recovery Status", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = dailyContext?.let { "${it.sleepHours} hours sleep (Epoch: ${it.epochDay})" }
                                ?: "No context for today found in Room.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Icon(Icons.Default.Refresh, contentDescription = null)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- SECTION 2: THE NUMERATOR (Categories) ---
            Text("Active Telemetry Keys", style = MaterialTheme.typography.labelLarge)

            if (categories.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Database empty. Hit + to write to SQLite.", color = Color.Gray)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories, key = { it.id }) { category ->
                        CategoryCard(category.name, category.hexColor)
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryCard(name: String, hexString: String) {
    val color = remember(hexString) {
        try {
            // 1. Remove the '#' if present
            val cleanedHex = hexString.removePrefix("#")

            // 2. Parse the hex string to a Long
            val colorLong = cleanedHex.toLong(16)

            // 3. Handle Alpha: If the string is 6 chars (RRGGBB), add FF for full opacity
            if (cleanedHex.length == 6) {
                Color(colorLong or 0xFF000000L)
            } else {
                Color(colorLong)
            }
        } catch (e: Exception) {
            Color.Gray // Fallback for invalid hex strings
        }
    }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = name, style = MaterialTheme.typography.bodyLarge)
        }
    }
}