package app.teleprompter.recorder

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Ink)) {
        Image(
            painterResource(R.drawable.hero),
            "Автор снимает видео на телефон со штатива",
            Modifier.fillMaxWidth().aspectRatio(1f),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f)
                .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Ink)),
        )
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.Bottom),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LogoMark()
                Text("Teleprompter", color = Fg, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
            Text("Говорите свободно. Снимайте быстрее. Записывайте уверенные видео с одного дубля.", color = Muted, fontSize = 17.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag("Субтитры")
                Tag("Зеркало")
                Tag("4K")
            }
            Spacer(Modifier.height(6.dp))
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(18.dp)) {
                Text("Начать бесплатно", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Tag(label: String) {
    Text(
        label,
        color = Fg,
        fontSize = 14.sp,
        modifier = Modifier.clip(CircleShape).background(CardRaised).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
fun LogoMark(size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(Accent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Videocam, null, tint = OnAccent, modifier = Modifier.size(size * 0.6f))
    }
}

enum class Tab { Texts, Recordings, Settings }

@Composable
fun BottomNav(tab: Tab, onTab: (Tab) -> Unit, onRecord: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().background(CardColor).navigationBarsPadding().height(68.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(Icons.AutoMirrored.Outlined.Article, "Тексты", tab == Tab.Texts) { onTab(Tab.Texts) }
            NavItem(Icons.Outlined.Videocam, "Записи", tab == Tab.Recordings) { onTab(Tab.Recordings) }
            Spacer(Modifier.width(76.dp))
            // Twice as wide, so the round button stays in the middle of the bar.
            NavItem(Icons.Outlined.Settings, "Настройки", tab == Tab.Settings, weight = 2f) { onTab(Tab.Settings) }
        }
        Box(
            Modifier.align(Alignment.TopCenter).offset(y = (-18).dp).size(64.dp).clip(CircleShape)
                .background(Accent).clickable(onClick = onRecord),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Add, "Записать видео", tint = OnAccent, modifier = Modifier.size(34.dp))
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    weight: Float = 1f,
    onClick: () -> Unit,
) {
    val color = if (selected) AccentSoft else Muted
    Column(
        Modifier.weight(weight).fillMaxHeight().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
        Text(label, color = color, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
fun TextsTab(
    st: PrompterState,
    takes: List<Take>,
    message: String,
    onStartVideo: () -> Unit,
    onOpenRecordings: () -> Unit,
    onOpenTake: (Take) -> Unit,
    onNewText: () -> Unit,
    onEdit: () -> Unit,
    onShoot: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val found = st.scenarios.filter { it.matches(query) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LogoMark(36.dp)
            Text("Teleprompter", color = Fg, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Поиск по текстам") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardColor)
                .border(1.dp, Line, RoundedCornerShape(16.dp)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("ГОТОВО К ЗАПИСИ", color = AccentSoft, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Текстов: ${st.scenarios.size}", color = Fg, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Button(onClick = onStartVideo, shape = RoundedCornerShape(12.dp)) { Text("Начать видео", fontWeight = FontWeight.Bold) }
        }
        if (message.isNotEmpty()) Text(message, color = Tally, fontSize = 15.sp)

        SectionHeader("Видео", takes.size, "Все", onOpenRecordings)
        if (takes.isEmpty()) {
            Note("Здесь появятся снятые видео.")
        } else {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                takes.take(10).forEach { TakeCard(it, Modifier.size(112.dp, 168.dp), onOpenTake) }
            }
        }

        SectionHeader("Все тексты", st.scenarios.size, "Новый", onNewText)
        if (found.isEmpty()) Note("Ничего не нашлось.")
        ScenarioCards(st, found, onPick = onEdit, onEdit = onEdit, onShoot = onShoot)

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardColor).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.Lightbulb, null, tint = AccentSoft)
            Column {
                Text("Совет", color = Fg, fontWeight = FontWeight.SemiBold)
                Note("Держите телефон на уровне глаз, чтобы взгляд не уходил вниз при чтении.")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionHeader(title: String, count: Int, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Fg, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(
            "$count",
            color = Muted,
            fontSize = 13.sp,
            modifier = Modifier.clip(CircleShape).background(CardRaised).padding(horizontal = 8.dp, vertical = 2.dp),
        )
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onAction) { Text(action, color = AccentSoft, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
fun ScenarioCards(
    st: PrompterState,
    items: List<Scenario>,
    onPick: () -> Unit,
    onEdit: () -> Unit,
    onShoot: () -> Unit,
) {
    var deleting by remember { mutableStateOf<Scenario?>(null) }

    items.forEach { s ->
        ScenarioCard(
            s,
            wpm = st.wpm,
            onPick = {
                st.select(s.id)
                onPick()
            },
            onEdit = {
                st.select(s.id)
                onEdit()
            },
            onShoot = {
                st.select(s.id)
                onShoot()
            },
            onDelete = { deleting = s },
        )
    }

    deleting?.let { s ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Удалить «${s.displayTitle}»?") },
            text = { Text("Текст исчезнет насовсем. Снятые видео останутся в галерее.") },
            confirmButton = {
                TextButton(onClick = {
                    st.delete(s.id)
                    deleting = null
                }) { Text("Удалить", color = Tally) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun ScenarioCard(
    s: Scenario,
    wpm: Int,
    onPick: () -> Unit,
    onEdit: () -> Unit,
    onShoot: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardColor)
            .clickable(onClick = onPick).padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(AccentSoft))
            Spacer(Modifier.width(6.dp))
            Text(
                if (s.cues.isNotEmpty()) "СУБТИТРЫ" else "ТЕКСТ",
                color = AccentSoft,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Box {
                Box(Modifier.size(36.dp).clip(CircleShape).clickable { menu = true }, Alignment.Center) {
                    Icon(Icons.Outlined.MoreVert, "Действия с текстом", tint = Muted)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Изменить") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                        onClick = {
                            menu = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Удалить") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                        onClick = {
                            menu = false
                            onDelete()
                        },
                    )
                }
            }
        }
        Text(
            s.displayTitle,
            color = Fg,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val preview = s.preview()
        if (preview.isNotEmpty()) {
            Text(preview, color = Muted, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Meta(Icons.Outlined.Schedule, fmt(s.readingSeconds(wpm)))
            if (s.cues.isNotEmpty()) Meta(Icons.Outlined.Subtitles, "${s.cues.size} реплик")
            else Meta(Icons.AutoMirrored.Outlined.Article, "${s.wordCount()} слов")
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(AccentDim).clickable(onClick = onShoot),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Videocam, "Снимать с этим текстом", tint = AccentSoft)
            }
        }
    }
}

@Composable
private fun Meta(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, tint = Muted, modifier = Modifier.size(16.dp))
        Text(label, color = Muted, fontSize = 13.sp)
    }
}

@Composable
fun TakeCard(take: Take, modifier: Modifier, onOpen: (Take) -> Unit) {
    val context = LocalContext.current
    val thumbnail by produceState<ImageBitmap?>(null, take.uri) {
        value = withContext(Dispatchers.IO) { loadThumbnail(context, take)?.asImageBitmap() }
    }
    Box(
        modifier.clip(RoundedCornerShape(14.dp)).background(CardColor).clickable { onOpen(take) },
    ) {
        thumbnail?.let {
            Image(it, take.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Text(
            fmt(take.durationMs / 1000),
            color = Fg,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                .clip(RoundedCornerShape(6.dp)).background(Chip).padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun RecordingsTab(takes: List<Take>, onOpenTake: (Take) -> Unit, onStartVideo: () -> Unit) {
    val dates = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle("Записи")
        if (takes.isEmpty()) {
            Note("Снятых видео пока нет.")
            Button(onClick = onStartVideo, shape = RoundedCornerShape(12.dp)) { Text("Начать видео") }
        }
        takes.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { take ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TakeCard(take, Modifier.fillMaxWidth().aspectRatio(0.75f), onOpenTake)
                        Note(dates.format(Date(take.addedAtMs)))
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ScreenTitle(title: String) {
    Text(title, color = Fg, fontSize = 26.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun SettingsTab(st: PrompterState) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle("Настройки")
        SettingsContent(st, recording = false)
        val context = LocalContext.current
        val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
        Note("Teleprompter $version")
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title.uppercase(), color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardColor).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun SettingsContent(st: PrompterState, recording: Boolean) {
    SettingsSection("Текст") {
        LabeledSlider("Размер", st.size, 24..84, 2, " пт") { st.size = it; st.save() }
        Text("Цвет", color = Fg)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PromptColors.forEachIndexed { i, color ->
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(color)
                        .border(3.dp, if (i == st.textColor) Accent else Line, CircleShape)
                        .clickable {
                            st.textColor = i
                            st.save()
                        },
                )
            }
        }
        LabeledSlider("Скорость чтения", st.wpm, 50..300, 10, " сл/мин") { st.wpm = it; st.save() }
    }
    SettingsSection("Экран суфлёра") {
        LabeledSlider("Высота панели", st.panel, 25..85, 5, "%") { st.panel = it; st.save() }
        LabeledSlider("Затемнение под текстом", st.dim, 0..95, 5, "%") { st.dim = it; st.save() }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Зеркало для суфлёра со стеклом", color = Fg, modifier = Modifier.weight(1f))
            Switch(checked = st.mirror, onCheckedChange = { st.mirror = it; st.save() })
        }
    }
    SettingsSection("Съёмка") {
        Options("Обратный отсчёт", listOf(0 to "Нет", 3 to "3 с", 5 to "5 с", 10 to "10 с"), st.countdown) {
            st.countdown = it
            st.save()
        }
        Options("Качество видео", listOf(720 to "720p", 1080 to "1080p", 2160 to "4K"), st.quality, enabled = !recording) {
            st.quality = it
            st.save()
        }
        Note("Если камера не умеет выбранное качество, приложение снимет в ближайшем доступном.")
        Options("Звук", listOf(true to "Громкий голос", false to "Как в камере"), st.loudVoice, enabled = !recording) {
            st.loudVoice = it
            st.save()
        }
        Note("«Громкий голос» подстраивает громкость речи и приглушает шум. «Как в камере» записывает всё вокруг без обработки.")
    }
}

@Composable
private fun LabeledSlider(label: String, value: Int, range: IntRange, step: Int, suffix: String, onChange: (Int) -> Unit) {
    Column {
        Row {
            Text(label, color = Fg, modifier = Modifier.weight(1f))
            Text("$value$suffix", color = AccentSoft, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / step - 1,
        )
    }
}

@Composable
private fun <T> Options(label: String, options: List<Pair<T, String>>, selected: T, enabled: Boolean = true, onSelect: (T) -> Unit) {
    Text(label, color = Fg)
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (v, name) ->
            FilterChip(selected = v == selected, enabled = enabled, onClick = { onSelect(v) }, label = { Text(name) })
        }
    }
}

/** Opened from the recording screen, so picking a text just closes it and leaves the camera running. */
@Composable
fun TextPickerSheet(
    st: PrompterState,
    onPicked: () -> Unit,
    onEdit: () -> Unit,
    onImport: () -> Unit,
    onWrite: () -> Unit,
) {
    SheetTitle("Текст для записи")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onWrite,
            modifier = Modifier.weight(1f).height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Outlined.Edit, null)
            Spacer(Modifier.width(6.dp))
            Text("Написать")
        }
        OutlinedButton(
            onClick = onImport,
            modifier = Modifier.weight(1f).height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Outlined.UploadFile, null)
            Spacer(Modifier.width(6.dp))
            Text("Загрузить")
        }
    }
    Note("Или выберите сохранённый:")
    ScenarioCards(st, st.scenarios, onPick = onPicked, onEdit = onEdit, onShoot = onPicked)
}

@Composable
private fun BackRow(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), Alignment.Center) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Назад", tint = Fg)
        }
        Spacer(Modifier.width(6.dp))
        Text(title, color = Fg, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun EditorScreen(st: PrompterState, onBack: () -> Unit, onPickFile: () -> Unit, onUse: () -> Unit) {
    val hasCues = st.cues.isNotEmpty()
    var value by remember(st.currentId, st.fileName) {
        mutableStateOf(if (hasCues) st.cues.joinToString("\n") { it.text } else st.text)
    }

    Column(
        Modifier.fillMaxSize().background(Ink).safeDrawingPadding().padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BackRow("Редактор", onBack)
        OutlinedTextField(
            value = st.current.title,
            onValueChange = st::rename,
            label = { Text("Название") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = value,
            onValueChange = {
                value = it
                st.setPlainText(it)
            },
            label = { Text("Текст для чтения") },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 360.dp),
        )
        OutlinedButton(onClick = onPickFile, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Outlined.UploadFile, null)
            Spacer(Modifier.width(8.dp))
            Text("Загрузить из файла")
        }
        if (st.fileName.isNotEmpty()) {
            Note("Загружен файл ${st.fileName}" + if (hasCues) ": реплик ${st.cues.size}." else ".")
        }
        if (hasCues) Note("Если изменить текст здесь, таймкоды отключатся.")
        Note(
            if (st.timed) "Длительность ${fmt(st.duration.toLong())}. Текст идёт по времени из субтитров, начиная с первой реплики."
            else "Слов ${st.words}, при ${st.wpm} сл/мин это примерно ${fmt(st.duration.toLong())}.",
        )
        Button(
            onClick = onUse,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp),
        ) { Text("Снимать с этим текстом", fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun NewTextSheet(onWrite: () -> Unit, onImport: () -> Unit) {
    SheetTitle("Новый текст")
    Button(onClick = onWrite, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
        Text("Написать")
    }
    OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
        Text("Загрузить файл: SRT, VTT или TXT")
    }
}
