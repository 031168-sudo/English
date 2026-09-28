package com.english.pronunciation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A licence whose full text ships in the APK, under assets/. */
private enum class LicenseText(val title: String, val asset: String) {
    APACHE("Apache License 2.0", "licenses/apache-2.0.txt"),
    OPENBLAS("BSD 3-Clause — OpenBLAS", "licenses/openblas.txt"),
    CLAPACK("BSD — CLAPACK", "licenses/clapack.txt"),
    DICTIONARIES("Словари: CMU и OpenRussian (CC BY-SA 4.0)", "dict/LICENSES.txt")
}

private class Component(
    val name: String,
    val purpose: String,
    val copyright: String,
    val license: String,
    val link: String
)

// Everything the APK is built from that is not our own. The licences ask for
// their notices to travel with the app, which is what this screen is for.
private val COMPONENTS = listOf(
    Component(
        name = "Vosk",
        purpose = "Распознавание речи на телефоне, без интернета",
        copyright = "© Alpha Cephei Inc.",
        license = "Apache License 2.0",
        link = "github.com/alphacep/vosk-api"
    ),
    Component(
        name = "Модель vosk-model-small-en-us-0.15",
        purpose = "Модель американского английского для распознавания",
        copyright = "© Alpha Cephei Inc.",
        license = "Apache License 2.0",
        link = "alphacephei.com/vosk/models"
    ),
    Component(
        name = "Kaldi",
        purpose = "Движок распознавания внутри Vosk",
        copyright = "© Johns Hopkins University и авторы Kaldi",
        license = "Apache License 2.0",
        link = "github.com/kaldi-asr/kaldi"
    ),
    Component(
        name = "OpenFst",
        purpose = "Конечные автоматы внутри Vosk",
        copyright = "© Google Inc. и авторы OpenFst",
        license = "Apache License 2.0",
        link = "openfst.org"
    ),
    Component(
        name = "OpenBLAS",
        purpose = "Математические вычисления внутри Vosk",
        copyright = "© 2011–2014 The OpenBLAS Project",
        license = "BSD 3-Clause",
        link = "github.com/OpenMathLib/OpenBLAS"
    ),
    Component(
        name = "CLAPACK",
        purpose = "Линейная алгебра внутри Vosk",
        copyright = "© 1992–2008 The University of Tennessee",
        license = "BSD",
        link = "netlib.org/clapack"
    ),
    Component(
        name = "JNA (Java Native Access) 5.13.0",
        purpose = "Связь приложения с Vosk",
        copyright = "© Timothy Wall и авторы JNA",
        license = "Apache License 2.0 (JNA доступна также по LGPL 2.1; используется Apache 2.0)",
        link = "github.com/java-native-access/jna"
    ),
    Component(
        name = "Android Jetpack",
        purpose = "AndroidX Core, Activity, Lifecycle, Jetpack Compose, Material 3, значки Material Icons",
        copyright = "© The Android Open Source Project, Google LLC",
        license = "Apache License 2.0",
        link = "developer.android.com/jetpack"
    ),
    Component(
        name = "Kotlin и kotlinx.coroutines",
        purpose = "Язык программирования и его библиотеки",
        copyright = "© JetBrains s.r.o. и авторы Kotlin",
        license = "Apache License 2.0",
        link = "kotlinlang.org"
    ),
    Component(
        name = "CMU Pronouncing Dictionary",
        purpose = "Транскрипции в «Мои слова» (переведены в МФА)",
        copyright = "© 1993–2015 Carnegie Mellon University",
        license = "BSD-подобная лицензия CMU",
        link = "github.com/cmusphinx/cmudict"
    ),
    Component(
        name = "OpenRussian.org",
        purpose = "Переводы в «Мои слова». Изменения: словарь обращён " +
            "(английский → русский и русский → английский), отсортирован по частоте, " +
            "сокращён и очищен от грубых слов. Изменённые словари распространяются " +
            "на тех же условиях",
        copyright = "© команда OpenRussian и участники",
        license = "Creative Commons Attribution-ShareAlike 4.0 " +
            "(creativecommons.org/licenses/by-sa/4.0)",
        link = "github.com/Badestrand/russian-dictionary"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Лицензии", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = "SayWord" + (version?.let { ", версия $it" } ?: "") + ". " +
                        "Приложение построено на свободных компонентах и данных. " +
                        "Ниже — их авторы и лицензии, а затем полные тексты лицензий.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
            items(COMPONENTS) { component -> ComponentCard(component) }
            item {
                Text(
                    text = "Тексты лицензий",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 4.dp, top = 12.dp)
                )
            }
            items(LicenseText.entries) { license -> LicenseTextCard(license) }
        }
    }
}

@Composable
private fun ComponentCard(component: Component) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = component.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = component.purpose,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(text = component.copyright, style = MaterialTheme.typography.bodySmall)
            Text(
                text = "Лицензия: ${component.license}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = component.link,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A licence's full text, folded until tapped; read from assets only when opened. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LicenseTextCard(license: LicenseText) {
    val context = LocalContext.current
    var open by rememberSaveable(license.name) { mutableStateOf(false) }
    val text = remember(open) {
        if (!open) {
            null
        } else {
            runCatching {
                context.assets.open(license.asset).bufferedReader().use { it.readText() }
            }.getOrElse { "Текст лицензии не найден." }
        }
    }
    Card(
        onClick = { open = !open },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = license.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(text = if (open) "▲" else "▼", fontSize = 14.sp)
            }
            if (text != null) {
                Text(
                    text = text.trim(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
    }
}
