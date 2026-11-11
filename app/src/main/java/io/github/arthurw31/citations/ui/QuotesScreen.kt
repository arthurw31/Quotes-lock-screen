package io.github.arthurw31.citations.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.Lightbulb
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import io.github.arthurw31.citations.ContentMode
import io.github.arthurw31.citations.Frequency
import io.github.arthurw31.citations.QuoteRenderer
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("d MMM yyyy 'à' HH:mm", Locale.FRENCH)
private fun fmt(t: Long) = if (t <= 0L) "jamais" else dateFormat.format(Date(t))

@Composable
fun QuotesScreen(vm: MainViewModel, state: UiState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            val q = state.current
            CardBlock {
                Text(if (q?.isWord == true) "Mot du moment" else "Citation du moment",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                if (q == null) {
                    Text("Aucune citation pour l'instant.")
                } else if (q.isWord) {
                    Text(q.text, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Serif))
                    Text(QuoteRenderer.frenchTypography(q.author),
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic))
                    q.definition?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(QuoteRenderer.frenchTypography(it), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text(
                        "« " + QuoteRenderer.frenchTypography(q.text) + " »",
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(q.author, style = MaterialTheme.typography.bodyMedium)
                    q.source?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (q != null) {
                    Explanation(q)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RatingButtons(state.ratings[q.id] ?: 0) { vm.rate(q.id, it) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = vm::nextQuote, enabled = !state.busy) {
                            Icon(Icons.Outlined.SkipNext, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Suivante")
                        }
                    }
                }
            }
        }

        item {
            val info = state.info
            CardBlock {
                Text("Liste de citations", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                if (info != null) {
                    Text(if (info.words > 0) "${info.count} citations et ${info.words} mots anglais" else "${info.count} citations")
                    Text(
                        if (info.fromGithub) "Source : GitHub, téléchargée le ${fmt(info.lastUpdate)}"
                        else "Source : liste intégrée à l'appli",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text("Dernière vérification : ${fmt(info.lastCheck)}", style = MaterialTheme.typography.bodySmall)
                    info.lastError?.let {
                        Text("Dernier problème : $it", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = vm::sync, enabled = !state.busy) {
                    Icon(Icons.Outlined.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Vérifier maintenant")
                }
                Hint("L'appli vérifie toute seule deux fois par jour quand il y a du réseau. Sans réseau, elle garde la dernière liste.")
            }
        }

        item {
            CardBlock {
                Text("Contenu et fréquence", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Choices("Afficher", ContentMode.entries, state.contentMode, { it.label }) { vm.setContentMode(it) }
                Hint("« Les deux » alterne une citation et un mot anglais à chaque changement.")
                Choices("Fréquence", Frequency.entries, state.frequency, { it.label }) { vm.setFrequency(it) }
                Hint("Le changement se fait après minuit (ou midi), dans l'heure qui suit en général. Android peut le retarder un peu pour économiser la batterie.")
            }
        }

        item {
            CardBlock {
                Text("Tes avis pour Claude", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text("Mets un cœur ou un pouce baissé aux citations. Chaque mois, exporte tes avis et colle le texte dans ta conversation avec Claude pour recevoir une nouvelle liste.",
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    scope.launch {
                        val text = vm.exportText()
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        ctx.startActivity(Intent.createChooser(send, "Exporter mes avis"))
                    }
                }) {
                    Icon(Icons.Outlined.Share, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Exporter mes avis")
                }
                Hint("Les citations avec un pouce baissé ne seront plus affichées.")
            }
        }

        item {
            CardBlock {
                Text("Fonctionnement en arrière-plan", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                if (state.batteryUnrestricted) {
                    Text("Batterie : sans restriction. Le changement quotidien est fiable.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text("Pour que la citation change même si tu n'ouvres jamais l'appli, autorise-la à fonctionner sans restriction de batterie. Elle ne travaille que quelques secondes par jour.",
                        style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                        )
                    }) { Text("Autoriser") }
                }
                Spacer(Modifier.height(8.dp))
                Text("Pense aussi à désactiver « Suspendre l'activité si l'appli n'est pas utilisée » dans les infos de l'appli.",
                    style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                    )
                }) { Text("Ouvrir les infos de l'appli") }
            }
        }

        item {
            Text("Historique", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp))
        }
        if (state.history.isEmpty()) {
            item { Hint("Les citations affichées apparaîtront ici.") }
        } else {
            item { Hint("Touche une citation pour afficher son explication.") }
        }
        items(state.history.take(80), key = { it.id + it.shownAt }) { e ->
            var open by rememberSaveable(e.id + e.shownAt) { mutableStateOf(false) }
            val full = state.quotesById[e.id]
            Column(Modifier.clickable(enabled = full?.explanation != null || full?.example != null) { open = !open }) {
                Text(if (e.isWord) e.text + " : " + QuoteRenderer.frenchTypography(e.author)
                    else "« " + QuoteRenderer.frenchTypography(e.text) + " »",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Serif))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (e.isWord) "Mot anglais, ${fmt(e.shownAt)}" else "${e.author}, ${fmt(e.shownAt)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    RatingButtons(state.ratings[e.id] ?: 0) { vm.rate(e.id, it) }
                }
                if (open && full != null) ExplanationText(full)
                HorizontalDivider()
            }
        }

        item {
            var url by rememberSaveable(state.quotesUrl) { mutableStateOf(state.quotesUrl) }
            var open by remember { mutableStateOf(false) }
            CardBlock {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Réglages avancés", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    TextButton(onClick = { open = !open }) { Text(if (open) "Masquer" else "Afficher") }
                }
                if (open) {
                    OutlinedTextField(
                        value = url, onValueChange = { url = it },
                        label = { Text("Adresse du fichier de citations") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row {
                        TextButton(onClick = { vm.setUrl(url) }) { Text("Enregistrer") }
                        TextButton(onClick = vm::resetUrl) { Text("Adresse par défaut") }
                    }
                }
            }
        }
    }
}

/** Bouton « Comprendre » qui déplie l'explication et l'exemple. */
@Composable
private fun Explanation(q: io.github.arthurw31.citations.Quote) {
    if (q.explanation == null && q.example == null) return
    var open by rememberSaveable(q.id) { mutableStateOf(false) }
    TextButton(onClick = { open = !open }, contentPadding = PaddingValues(horizontal = 0.dp)) {
        Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.Lightbulb, null)
        Spacer(Modifier.width(6.dp))
        Text(when {
            open -> "Masquer"
            q.isWord -> "Voir un exemple"
            else -> "Comprendre cette citation"
        })
    }
    AnimatedVisibility(open) { ExplanationText(q) }
}

@Composable
private fun ExplanationText(q: io.github.arthurw31.citations.Quote) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        q.explanation?.let {
            Text("Ce que ça veut dire", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(2.dp))
            Text(QuoteRenderer.frenchTypography(it), style = MaterialTheme.typography.bodyMedium)
        }
        q.example?.let {
            if (q.explanation != null) Spacer(Modifier.height(10.dp))
            Text(if (q.isWord) "Exemple d'utilisation" else "Exemple", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(2.dp))
            Text(QuoteRenderer.frenchTypography(it), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun RatingButtons(rating: Int, onRate: (Int) -> Unit) {
    Row {
        IconButton(onClick = { onRate(1) }) {
            Icon(
                if (rating > 0) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = "J'aime",
                tint = if (rating > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = { onRate(-1) }) {
            Icon(
                if (rating < 0) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                contentDescription = "Je n'aime pas",
                tint = if (rating < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CardBlock(content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}
