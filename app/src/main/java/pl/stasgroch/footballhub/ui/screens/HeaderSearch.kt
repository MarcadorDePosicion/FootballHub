package pl.stasgroch.footballhub.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

@Composable fun HeaderSearch(query: String, update: (String) -> Unit, search: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    Surface(modifier = Modifier.padding(end = 8.dp), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ keyboard?.hide(); search() }) { Icon(HubIcons.Search, "Wyszukaj drużynę") }
            BasicTextField(query, update, Modifier.weight(1f), singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); search() }),
                decorationBox = { field -> Box {
                    if (query.isEmpty()) Text("Szukaj drużyny", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    field()
                } })
        }
    }
}
