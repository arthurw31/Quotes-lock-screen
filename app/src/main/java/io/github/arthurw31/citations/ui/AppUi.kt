package io.github.arthurw31.citations.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.arthurw31.citations.Appearance
import io.github.arthurw31.citations.ColorChoice
import io.github.arthurw31.citations.FontChoice
import io.github.arthurw31.citations.TextAlignChoice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitationsAppUi(vm: MainViewModel) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Citations") },
                actions = {
                    if (state.busy) {
                        CircularProgressIndicator(Modifier.size(22.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(16.dp))
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Outlined.Palette, null) }, label = { Text("Apparence") }
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Outlined.FormatQuote, null) }, label = { Text("Citations") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (tab == 0) AppearanceScreen(vm, state, preview) else QuotesScreen(vm, state)
        }
    }
}

@Composable
private fun AppearanceScreen(vm: MainViewModel, state: UiState, preview: android.graphics.Bitmap?) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.pickPhoto(uri)
    }
    var showClock by rememberSaveable { mutableStateOf(true) }
    val a = state.appearance
    fun set(block: Appearance.() -> Appearance) = vm.updateAppearance(a.block())

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PreviewCard(preview, state.screenRatio, showClock)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Icon(Icons.Outlined.Image, null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.hasPhoto) "Changer de photo" else "Choisir ma photo")
            }
            Button(onClick = vm::applyNow, enabled = state.hasPhoto && !state.busy) {
                Text("Appliquer")
            }
        }
        if (!state.hasPhoto) {
            Hint("Commence par choisir la photo que tu utilises comme fond d'écran. L'écran d'accueil ne sera pas modifié.")
        } else {
            Hint("Les réglages sont enregistrés tout de suite. Appuie sur Appliquer pour mettre à jour l'écran de verrouillage.")
        }

        Section("Texte")
        LabeledSlider("Position verticale", a.position, 0.08f..0.94f, "Haut", "Bas") { v -> set { copy(position = v) } }
        LabeledSlider("Taille", a.size, 0f..1f, "Petit", "Grand") { v -> set { copy(size = v) } }
        LabeledSlider("Largeur", a.width, 0f..1f, "Étroit", "Large") { v -> set { copy(width = v) } }
        Choices("Alignement", TextAlignChoice.entries, a.align, { it.label }) { v -> set { copy(align = v) } }
        Choices("Police", FontChoice.entries, a.font, { it.label }) { v -> set { copy(font = v) } }
        Choices("Couleur", ColorChoice.entries, a.color, { it.label }) { v -> set { copy(color = v) } }
        Hint("Auto choisit un texte clair ou foncé selon la luminosité de la photo derrière la citation.")

        Section("Lisibilité")
        LabeledSlider("Voile derrière le texte", a.scrim, 0f..1f, "Aucun", "Marqué") { v -> set { copy(scrim = v) } }
        SwitchRow("Ombre douce", a.shadow) { v -> set { copy(shadow = v) } }
        SwitchRow("Afficher l'auteur", a.showAuthor) { v -> set { copy(showAuthor = v) } }
        SwitchRow("Simuler l'horloge dans l'aperçu", showClock) { showClock = it }
        Hint("L'horloge du Pixel occupe le haut de l'écran et les notifications s'affichent en dessous. Le bas de l'écran (au-dessus des raccourcis) est généralement la zone la plus tranquille.")
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PreviewCard(preview: android.graphics.Bitmap?, ratio: Float, showClock: Boolean) {
    BoxWithConstraints(
        Modifier.fillMaxWidth(0.66f).aspectRatio(ratio).clip(RoundedCornerShape(28.dp))
    ) {
        if (preview != null) {
            Image(preview.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        }
        if (showClock) {
            val density = LocalDensity.current
            val big = with(density) { (maxWidth * 0.24f).toSp() }
            val small = with(density) { (maxWidth * 0.045f).toSp() }
            Column(
                Modifier.fillMaxWidth().padding(top = maxHeight * 0.07f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.FRENCH).format(java.util.Date()), color = Color.White.copy(alpha = 0.55f), fontSize = small)
                Text("12", color = Color.White.copy(alpha = 0.45f), fontSize = big, lineHeight = big, fontWeight = FontWeight.Light)
                Text("34", color = Color.White.copy(alpha = 0.45f), fontSize = big, lineHeight = big, fontWeight = FontWeight.Light)
            }
        }
    }
}

@Composable
fun Section(title: String) {
    Spacer(Modifier.height(20.dp))
    Text(
        title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
fun Hint(text: String) {
    Text(
        text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Start
    )
}

@Composable
private fun LabeledSlider(
    label: String, value: Float, range: ClosedFloatingPointRange<Float>,
    startLabel: String, endLabel: String, onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(startLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(endLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> Choices(label: String, options: List<T>, selected: T, name: (T) -> String, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(name(option)) })
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
