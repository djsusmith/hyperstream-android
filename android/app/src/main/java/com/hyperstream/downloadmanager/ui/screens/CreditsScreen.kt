package com.hyperstream.downloadmanager.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

object HyperStreamCredits {
    const val HEADLINE = "Created by Ahnaf Hasan Susmith with Antigravity"
    const val CREATOR_NAME = "Ahnaf Hasan Susmith"
    const val BUILT_WITH = "Antigravity"
    const val CREATOR_EMAIL = "susdj.96@gmail.com"
    const val GITHUB_URL = "https://github.com/djsusmith/hyperstream-android"
    const val UPSTREAM_URL = "https://github.com/anefzaoui/misterdownloadmanager"

    fun mailtoUri(): String = "mailto:$CREATOR_EMAIL"
}

@Composable
fun CreditsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero App & Creator Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "HyperStream Icon",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                    Text(
                        text = "HyperStream Download Manager",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = HyperStreamCredits.HEADLINE,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = HyperStreamCredits.CREATOR_EMAIL,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        Button(
                            onClick = {
                                runCatching {
                                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(HyperStreamCredits.mailtoUri())).apply {
                                        putExtra(Intent.EXTRA_SUBJECT, "HyperStream Download Manager Feedback")
                                    }
                                    context.startActivity(intent)
                                }
                            }
                        ) {
                            Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                            Text("Email Developer")
                        }
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(HyperStreamCredits.GITHUB_URL)))
                                }
                            }
                        ) {
                            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                            Text("GitHub Repo")
                        }
                    }
                }
            }
        }

        // Developer & Antigravity Engineering Details
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Author & Android Port Engineering",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "• Creator: ${HyperStreamCredits.CREATOR_NAME}\n" +
                            "• Built With: ${HyperStreamCredits.BUILT_WITH} (Google DeepMind Advanced Agentic Coding)\n" +
                            "• Contact: ${HyperStreamCredits.CREATOR_EMAIL}\n" +
                            "• Target Architecture: ARM64 (aarch64-linux-android) • 128-Thread Segmented Engine + Shizuku + Foreground WakeLock",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Upstream Attribution Card
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Upstream Rust Engine & Open-Source License",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Ported 1-to-1 from Mister Download Manager (anefzaoui/misterdownloadmanager v0.5.2) by Ahmed Nefzaoui.\n" +
                            "Includes mdm-engine, mdm-torrent (librqbit), mdm-debrid, mdm-extract, mdm-geo, mdm-cnl, mdm-ipc, mdm-remote, and 86 community language catalogs.\n" +
                            "Licensed under GNU General Public License v3.0 or later (GPL-3.0-or-later).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(HyperStreamCredits.UPSTREAM_URL)))
                            }
                        }
                    ) {
                        Text("View Upstream Repository")
                    }
                }
            }
        }
    }
}
