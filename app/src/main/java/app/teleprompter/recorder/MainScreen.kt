package app.teleprompter.recorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val Ink = Color(0xFF0C0E12)
private val Fg = Color(0xFFF4F2EC)
private val Muted = Color(0xFFA3A9B5)
private val Tally = Color(0xFFFF3B30)
private val CueColor = Color(0xFFFFC24A)
private val Chip = Color(0xD9171A21)

private enum class Sheet { None, Script, Settings, Take }
private enum class RecState { Idle, Counting, Recording }

private fun fmt(sec: Long): String = String.format(Locale.ROOT, "%02d:%02d", sec / 60, sec % 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(st: PrompterState, engine: CameraEngine) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sheet by remember { mutableStateOf(Sheet.None) }
    var rec by remember { mutableStateOf(RecState.Idle) }
    var seconds by remember { mutableLongStateOf(0L) }
    var count by remember { mutableIntStateOf(0) }
    var countJob by remember { mutableStateOf<Job?>(null) }
    var message by remember { mutableStateOf("") }
    var takeUri by remember { mutableStateOf<Uri?>(null) }
    var takeInterrupted by remember { mutableStateOf(false) }
    var cameraGranted by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        cameraGranted = r[Manifest.permission.CAMERA] == true
        message = when {
            !cameraGranted -> "Нет доступа к камере. Разрешите его в настройках приложения."
            r[Manifest.permission.RECORD_AUDIO] != true -> "Нет доступа к микрофону: видео запишется без звука."
            else -> ""
        }
    }
    LaunchedEffect(Unit) {
        val wanted = buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        permissions.launch(wanted.toTypedArray())
    }
    LaunchedEffect(cameraGranted, st.frontCamera, st.quality) {
        if (cameraGranted) engine.bind(st.frontCamera, st.quality) { message = it }
    }

    // Uses real frame time, so dropped frames do not make the text lag behind its timecodes.
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            st.tick(((now - last) / 1e9f).coerceAtMost(1f))
            last = now
        }
    }

    fun beginRecording() {
        if (st.atEnd) st.rewind()
        val ok = engine.start(
            onProgress = { seconds = it },
            onFinished = { uri, interrupted ->
                rec = RecState.Idle
                st.running = false
                seconds = 0
                st.rewind()
                takeUri = uri
                takeInterrupted = interrupted
                if (uri != null) sheet = Sheet.Take
                else message = "Запись оказалась пустой. Попробуйте ещё раз."
            },
        )
        if (ok) {
            rec = RecState.Recording
            st.running = true
        } else {
            message = "Камера ещё не готова. Попробуйте через секунду."
        }
    }

    fun onRecButton() {
        when (rec) {
            RecState.Recording -> engine.stop()
            RecState.Counting -> {
                countJob?.cancel()
                rec = RecState.Idle
            }
            RecState.Idle -> {
                if (!cameraGranted) {
                    message = "Сначала разрешите доступ к камере в настройках приложения."
                    return
                }
                st.running = false
                if (st.countdown == 0) {
                    beginRecording()
                    return
                }
                rec = RecState.Counting
                countJob = scope.launch {
                    for (i in st.countdown downTo 1) {
                        count = i
                        delay(1000)
                    }
                    beginRecording()
                }
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "файл"
            if (Regex("""\.(docx?|pdf|rtf)$""", RegexOption.IGNORE_CASE).containsMatchIn(name)) {
                message = "Этот формат не читается. Сохраните файл как TXT или вставьте текст вручную."
                return@launch
            }
            val raw = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { decodeText(it.readBytes()) }
            }
            if (raw.isNullOrBlank()) message = "Файл пустой." else {
                message = ""
                st.loadFile(name, raw)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { engine.previewView }, modifier = Modifier.fillMaxSize())

        Prompter(st, Modifier.fillMaxWidth().fillMaxHeight(st.panel / 100f))

        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Pill("Текст") { sheet = Sheet.Script }
                Spacer(Modifier.weight(1f))
                RecClock(rec == RecState.Recording, seconds)
                Spacer(Modifier.weight(1f))
                Pill("Настройки") { sheet = Sheet.Settings }
            }
            Spacer(Modifier.weight(1f))
            if (message.isNotEmpty()) {
                Text(
                    message,
                    color = Fg,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(10.dp)).background(Chip).padding(12.dp),
                )
            }
            SpeedPill(st, Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f)) {
                    RoundButton("⟲", enabled = rec == RecState.Idle) {
                        st.frontCamera = !st.frontCamera
                        st.save()
                    }
                }
                RecButton(rec == RecState.Recording) { onRecButton() }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                    RoundButton("⏮") {
                        st.running = false
                        st.rewind()
                    }
                    RoundButton(if (st.running) "⏸" else "▶") { st.togglePlay() }
                }
            }
        }

        if (rec == RecState.Counting) {
            Box(Modifier.fillMaxSize().background(Color(0x590C0E12)), contentAlignment = Alignment.Center) {
                Text("$count", color = Fg, fontSize = 140.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }

    if (sheet != Sheet.None) {
        ModalBottomSheet(onDismissRequest = { sheet = Sheet.None }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (sheet) {
                    Sheet.Script -> ScriptSheet(st) { filePicker.launch(arrayOf("*/*")) }
                    Sheet.Settings -> SettingsSheet(st, recording = rec != RecState.Idle)
                    Sheet.Take -> TakeSheet(takeUri, takeInterrupted) { sheet = Sheet.None }
                    Sheet.None -> Unit
                }
            }
        }
    }
}

@Composable
private fun Prompter(st: PrompterState, modifier: Modifier) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier
            .background(Ink.copy(alpha = st.dim / 100f))
            .clipToBounds()
            .pointerInput(Unit) { detectTapGestures { st.togglePlay() } }
            .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> st.dragBy(dy) } },
    ) {
        val readY = with(density) { maxHeight.toPx() * 0.3f }
        val blocks = st.blocks
        val tops = remember(blocks) { mutableStateListOf<Float>().apply { repeat(blocks.size) { add(0f) } } }

        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { if (st.mirror) scaleX = -1f }
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .offset { IntOffset(0, (readY - st.pos).toInt()) }
                .padding(start = 32.dp, end = 24.dp)
                .onGloballyPositioned { st.setLayout(tops.toList(), it.size.height.toFloat()) },
        ) {
            blocks.forEachIndexed { i, block ->
                val on by remember(i) { derivedStateOf { st.active == i } }
                Text(
                    block,
                    color = if (on) Fg else Fg.copy(alpha = 0.62f),
                    fontSize = st.size.sp,
                    lineHeight = (st.size * 1.3f).sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(bottom = (st.size * 0.45f).dp)
                        .onGloballyPositioned { c ->
                            if (i < tops.size) tops[i] = c.positionInParent().y
                        },
                )
            }
        }

        Box(
            Modifier
                .offset { IntOffset(8.dp.roundToPx(), readY.toInt()) }
                .size(14.dp, 20.dp)
                .drawBehind {
                    val p = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, size.height / 2)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(p, Tally)
                },
        )
    }
}

@Composable
private fun Pill(label: String, onClick: () -> Unit) {
    Box(
        Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(Chip)
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RecClock(recording: Boolean, seconds: Long) {
    Row(
        Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(Chip).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (recording) Tally else Muted))
        Text(fmt(seconds), color = Fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SpeedPill(st: PrompterState, modifier: Modifier) {
    Row(
        modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(Chip),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp, 40.dp).clickable { st.nudgeSpeed(-1) }, Alignment.Center) {
            Text("−", color = Fg, fontSize = 22.sp)
        }
        Text(
            if (st.timed) "темп ${st.tempo}%" else "${st.wpm} сл/мин",
            color = Fg,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(104.dp),
        )
        Box(Modifier.size(44.dp, 40.dp).clickable { st.nudgeSpeed(1) }, Alignment.Center) {
            Text("+", color = Fg, fontSize = 22.sp)
        }
    }
}

@Composable
private fun RoundButton(icon: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(CircleShape).background(Chip).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(icon, color = if (enabled) Fg else Muted, fontSize = 22.sp)
    }
}

@Composable
private fun RecButton(recording: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(76.dp).clip(CircleShape).border(4.dp, Fg, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (recording) Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(Tally))
        else Box(Modifier.size(58.dp).clip(CircleShape).background(Tally))
    }
}

@Composable
private fun SheetTitle(title: String) {
    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Fg)
}

@Composable
private fun Note(text: String) {
    Text(text, fontSize = 14.sp, color = Muted)
}

@Composable
private fun ScriptSheet(st: PrompterState, onPickFile: () -> Unit) {
    val hasCues = st.cues.isNotEmpty()
    var value by remember { mutableStateOf(if (hasCues) st.cues.joinToString("\n") { it.text } else st.text) }

    SheetTitle("Текст и субтитры")
    Button(onClick = onPickFile, modifier = Modifier.fillMaxWidth()) { Text("Загрузить файл: SRT, VTT или TXT") }
    if (st.fileName.isNotEmpty()) {
        Note("Загружен файл ${st.fileName}" + if (hasCues) ": реплик ${st.cues.size}." else ".")
    }
    OutlinedTextField(
        value = value,
        onValueChange = {
            value = it
            st.setPlainText(it)
        },
        label = { Text("Текст для чтения") },
        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 280.dp),
    )
    if (hasCues) Note("Если изменить текст здесь, таймкоды отключатся.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = st.timed,
            enabled = hasCues,
            onClick = { st.chooseTimecodes(true) },
            label = { Text("По таймкодам") },
        )
        FilterChip(
            selected = !st.timed,
            onClick = { st.chooseTimecodes(false) },
            label = { Text("По скорости чтения") },
        )
    }
    Note(
        if (st.timed) "Хронометраж ${fmt(st.duration.toLong())}. Текст идёт по времени из субтитров, начиная с первой реплики."
        else "Слов ${st.words}, при ${st.wpm} сл/мин это примерно ${fmt(st.duration.toLong())}.",
    )
}

@Composable
private fun LabeledSlider(label: String, value: Int, range: IntRange, step: Int, suffix: String, onChange: (Int) -> Unit) {
    Column {
        Row {
            Text(label, color = Fg, modifier = Modifier.weight(1f))
            Text("$value$suffix", color = Muted)
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (v, name) ->
            FilterChip(selected = v == selected, enabled = enabled, onClick = { onSelect(v) }, label = { Text(name) })
        }
    }
}

@Composable
private fun SettingsSheet(st: PrompterState, recording: Boolean) {
    SheetTitle("Настройки")
    LabeledSlider("Размер текста", st.size, 24..84, 2, " пт") { st.size = it; st.save() }
    LabeledSlider("Высота панели", st.panel, 25..85, 5, "%") { st.panel = it; st.save() }
    LabeledSlider("Затемнение под текстом", st.dim, 0..95, 5, "%") { st.dim = it; st.save() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Отзеркалить текст (для суфлёра со стеклом)", color = Fg, modifier = Modifier.weight(1f))
        Checkbox(checked = st.mirror, onCheckedChange = { st.mirror = it; st.save() })
    }
    Options("Обратный отсчёт", listOf(0 to "Нет", 3 to "3 с", 5 to "5 с", 10 to "10 с"), st.countdown) {
        st.countdown = it
        st.save()
    }
    Options("Качество видео", listOf(720 to "720p", 1080 to "1080p", 2160 to "4K"), st.quality, enabled = !recording) {
        st.quality = it
        st.save()
    }
    Note("Если камера не умеет выбранное качество, приложение снимет в ближайшем доступном.")
}

@Composable
private fun TakeSheet(uri: Uri?, interrupted: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    SheetTitle("Дубль готов")
    Note("Видео уже в галерее, в папке Movies/Teleprompter.")
    if (interrupted) Note("Запись прервалась раньше времени. Сохранено то, что успело записаться.")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                )
            },
            modifier = Modifier.weight(1f),
        ) { Text("Посмотреть") }
        OutlinedButton(
            onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("video/mp4")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(send, "Поделиться видео"))
            },
            modifier = Modifier.weight(1f),
        ) { Text("Поделиться") }
    }
    TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Записать ещё") }
}
