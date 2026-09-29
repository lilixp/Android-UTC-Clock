package io.github.lilixp.utchamclock

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

@Composable
fun EcranSetari(setari: Setari, texte: Texte, laSchimbare: (Setari) -> Unit, laInapoi: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = laInapoi)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = laInapoi) { Text("← ${texte.inapoi}") }
            Text(texte.setari, style = MaterialTheme.typography.titleLarge)
        }
        Text(texte.atingePentruExplicatii, style = MaterialTheme.typography.bodySmall)

        // Indicativul și locatorul se verifică și se salvează împreună, cu butonul Salvează
        var indicativ by rememberSaveable { mutableStateOf(setari.indicativ) }
        var locator by rememberSaveable { mutableStateOf(setari.locator) }
        var eroare by remember { mutableStateOf<String?>(null) }
        OutlinedTextField(
            value = indicativ, onValueChange = { indicativ = it.uppercase().trim(); eroare = null },
            label = { Text(texte.indicativ) }, placeholder = { Text(texte.exempluIndicativ) }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = locator, onValueChange = { locator = it.trim(); eroare = null },
            label = { Text(texte.locator) }, placeholder = { Text(texte.exempluLocator) }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(),
        )
        eroare?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            eroare = when {
                indicativ.isNotEmpty() && !Calcule.indicativValid(indicativ) -> texte.indicativInvalid
                locator.isNotEmpty() && !Calcule.locatorValid(locator) -> texte.locatorInvalid
                else -> null
            }
            if (eroare == null) {
                locator = Calcule.normalizeazaLocator(locator)
                laSchimbare(setari.copy(indicativ = indicativ, locator = locator))
                Toast.makeText(context, texte.salvat, Toast.LENGTH_SHORT).show()
            }
        }) { Text(texte.salveaza) }

        HorizontalDivider()
        Text(texte.aranjare, style = MaterialTheme.typography.titleMedium)
        Aranjari.TOATE.forEach { id ->
            Optiune(texte.numeAranjari.getValue(id), setari.aranjare == id) { laSchimbare(setari.copy(aranjare = id)) }
        }

        HorizontalDivider()
        Text(texte.tema, style = MaterialTheme.typography.titleMedium)
        Teme.TOATE.forEach { id ->
            Optiune(texte.numeTeme.getValue(id), setari.tema == id) { laSchimbare(setari.copy(tema = id)) }
        }
        Comutator(texte.modNoapte, texte.modNoapteDescriere, setari.modNoapte) { laSchimbare(setari.copy(modNoapte = it)) }

        HorizontalDivider()
        Text(texte.afisare, style = MaterialTheme.typography.titleMedium)
        Comutator(texte.secunde, null, setari.secunde) { laSchimbare(setari.copy(secunde = it)) }
        Comutator(texte.clipire, texte.clipireDescriere, setari.clipire) { laSchimbare(setari.copy(clipire = it)) }
        Comutator(texte.vremea, texte.vremeaDescriere, setari.vremea) { laSchimbare(setari.copy(vremea = it)) }

        HorizontalDivider()
        Text(texte.ceasStatie, style = MaterialTheme.typography.titleMedium)
        Comutator(texte.ecranAprins, texte.ecranAprinsDescriere, setari.ecranAprins) {
            laSchimbare(setari.copy(ecranAprins = it))
        }
        Comutator(texte.noapteAutomata, texte.noapteAutomataDescriere, setari.noapteAutomata) {
            laSchimbare(setari.copy(noapteAutomata = it))
        }

        HorizontalDivider()
        Text(texte.limba, style = MaterialTheme.typography.titleMedium)
        listOf("auto" to texte.limbaTelefon, "ro" to "Română", "en" to "English").forEach { (cod, nume) ->
            Optiune(nume, setari.limba == cod) { laSchimbare(setari.copy(limba = cod)) }
        }

        HorizontalDivider()
        Text(texte.despre, style = MaterialTheme.typography.titleMedium)
        Text(texte.textDespre.format(BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall)
    }
}

/** Un rând cu titlu, descriere opțională și comutator în dreapta; tot rândul se poate atinge. */
@Composable
private fun Comutator(titlu: String, descriere: String?, pornit: Boolean, laSchimbare: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = pornit, onValueChange = laSchimbare, role = Role.Switch),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(titlu)
            descriere?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        Switch(checked = pornit, onCheckedChange = null)
    }
}

@Composable
private fun Optiune(text: String, selectata: Boolean, laAlegere: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selectata, onClick = laAlegere, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selectata, onClick = null)
        Text(text, Modifier.padding(start = 12.dp))
    }
}
