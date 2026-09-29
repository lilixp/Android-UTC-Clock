package io.github.lilixp.utchamclock

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Aplicatie() }
    }
}

@Composable
private fun Aplicatie() {
    val context = LocalContext.current
    var setari by remember { mutableStateOf(Setari.citeste(context)) }
    var laSetari by rememberSaveable { mutableStateOf(false) }
    val tema = Teme.activa(setari)
    val texte = Texte.pentru(setari.limba)

    // Ceasul: se actualizează exact la începutul fiecărei secunde
    var acum by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            acum = Instant.now()
            delay(1000 - acum.toEpochMilli() % 1000)
        }
    }

    // Datele din internet: la pornire și apoi la fiecare 30 de minute
    var solar by remember { mutableStateOf(DateSolare()) }
    var benzi by remember { mutableStateOf<Benzi?>(null) }
    var eroareCeas by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            coroutineScope {
                launch { solar = Retea.descarcaSolar(solar) }
                launch { benzi = Retea.descarcaBenzi() }
                launch { eroareCeas = Retea.masoaraEroareCeas() }
            }
            delay(Retea.INTERVAL_MS)
        }
    }

    // Bara de sus a telefonului: iconițe închise pe temele luminoase, deschise pe cele întunecate
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val fereastra = (view.context as Activity).window
            WindowCompat.getInsetsController(fereastra, view).apply {
                isAppearanceLightStatusBars = tema.luminoasa
                isAppearanceLightNavigationBars = tema.luminoasa
            }
        }
    }

    TemaAplicatie(tema) {
        Box(Modifier.safeDrawingPadding()) {
            if (laSetari) {
                EcranSetari(setari, texte,
                    laSchimbare = { setari = it; Setari.salveaza(context, it) },
                    laInapoi = { laSetari = false })
            } else {
                EcranCeas(StareCeas(acum, solar, benzi, eroareCeas), setari, tema, texte,
                    laSetari = { laSetari = true })
            }
        }
    }
}

/** Fundalul temei, iar componentele standard (câmpuri, butoane) primesc culorile ei. */
@Composable
fun TemaAplicatie(tema: Tema, continut: @Composable () -> Unit) {
    val culori = if (tema.luminoasa) lightColorScheme(
        primary = tema.utc.aprins, onPrimary = tema.fundal, background = tema.fundal, surface = tema.fundal,
        onBackground = tema.utc.aprins, onSurface = tema.utc.aprins, onSurfaceVariant = tema.info,
        outline = tema.info, surfaceContainerHigh = tema.fundal,
    ) else darkColorScheme(
        primary = tema.utc.aprins, onPrimary = tema.fundal, background = tema.fundal, surface = tema.fundal,
        onBackground = tema.utc.aprins, onSurface = tema.utc.aprins, onSurfaceVariant = tema.info,
        outline = tema.info, surfaceContainerHigh = tema.fundal,
    )
    MaterialTheme(colorScheme = culori) {
        Surface(Modifier.fillMaxSize(), color = tema.fundal, content = continut)
    }
}
