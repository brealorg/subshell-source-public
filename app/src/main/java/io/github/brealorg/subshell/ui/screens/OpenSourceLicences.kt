package io.github.brealorg.subshell.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.brealorg.subshell.about.OPEN_SOURCE_LIBRARIES
import io.github.brealorg.subshell.about.OpenSourceLicence
import io.github.brealorg.subshell.about.readLicenceText
import io.github.brealorg.subshell.ui.components.AdaptiveFullScreenDialog
import io.github.brealorg.subshell.ui.symbols.Symbols

/*
 * ISSUE39_LICENCES_AND_SOURCE_IN_THE_APP_V1 (#39): GPLv3 §5(d) asks an
 * interactive program to show its legal notices, and the Apache-2.0
 * libraries ask for their licence to travel with them. Help & about says
 * subshell is GPL-3.0-or-later with no warranty, links the source, and lists
 * every library in the release APK with its licence; the licence texts are
 * bundled, so this works offline. The list lives in about/.
 */

@Composable
internal fun OpenSourceLicencesDialog(onDismissRequest: () -> Unit) {
    var shown by remember { mutableStateOf<OpenSourceLicence?>(null) }
    val licence = shown
    if (licence != null) {
        LicenceTextDialog(licence, onBack = { shown = null })
        return
    }
    AdaptiveFullScreenDialog(title = "Open-source licences", onDismissRequest = onDismissRequest) {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item("subshell") {
                LicenceRow(
                    name = "subshell",
                    detail = OpenSourceLicence.GPL_3_0_OR_LATER.title,
                    onClick = { shown = OpenSourceLicence.GPL_3_0_OR_LATER },
                )
                HorizontalDivider()
            }
            items(OPEN_SOURCE_LIBRARIES, key = { it.artifact }) { library ->
                LicenceRow(
                    name = library.name,
                    detail = "${library.artifact} · ${library.licence.title}",
                    onClick = { shown = library.licence },
                )
            }
        }
    }
}

@Composable
private fun LicenceRow(name: String, detail: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(name, style = MaterialTheme.typography.bodyLarge)
        Text(
            detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LicenceTextDialog(licence: OpenSourceLicence, onBack: () -> Unit) {
    val context = LocalContext.current
    val text by produceState("", licence) { value = readLicenceText(context, licence) }
    AdaptiveFullScreenDialog(
        title = licence.title,
        onDismissRequest = onBack,
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Symbols.ArrowBack, contentDescription = "Back") }
        },
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item("text") {
                Text(
                    text,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
