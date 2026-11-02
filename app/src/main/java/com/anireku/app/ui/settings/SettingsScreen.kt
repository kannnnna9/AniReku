package com.anireku.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Pengaturan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Pembaruan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Versi terpasang: ${state.versionName}",
                    style = MaterialTheme.typography.bodyMedium,
                )

                state.available?.let { update ->
                    Text(
                        "Tersedia versi ${update.version}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    update.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                        Text(
                            notes.take(600),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.readyToInstall) {
                        Button(onClick = vm::install) { Text("Pasang sekarang") }
                    } else if (state.available != null) {
                        Button(onClick = vm::download, enabled = !state.downloading) {
                            Text(if (state.downloading) "Mengunduh…" else "Unduh update")
                        }
                    } else {
                        OutlinedButton(onClick = vm::checkForUpdates, enabled = !state.checking) {
                            Text(if (state.checking) "Memeriksa…" else "Cek pembaruan")
                        }
                    }
                    if (state.checking || state.downloading) {
                        CircularProgressIndicator(Modifier.height(20.dp))
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sumber data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Katalog & metadata dari AniList. Aplikasi ini tidak menyimpan atau meng-host video; " +
                        "tautan putar berasal dari feed yang kamu konfigurasi sendiri.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text(
                    "Tanpa iklan, tanpa SDK pelacak, tanpa akun. Kode sumber: GPL-3.0.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }

    state.message?.let { message ->
        LaunchedEffect(message) { }
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = {
                OutlinedButton(onClick = vm::dismissMessage) { Text("Tutup") }
            },
        ) { Text(message) }
    }
}
