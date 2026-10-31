package app.teleprompter.recorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Screen { Welcome, Home, Editor, Studio, Player }
private enum class Sheet { None, NewText, TextPicker, Settings, Take }
private enum class RecState { Idle, Counting, Recording }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(st: PrompterState, engine: CameraEngine) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var screen by remember { mutableStateOf(if (st.welcomed) Screen.Home else Screen.Welcome) }
    var tab by remember { mutableStateOf(Tab.Texts) }
    var editorReturn by remember { mutableStateOf(Screen.Home) }
    var playerReturn by remember { mutableStateOf(Screen.Home) }
    var playing by remember { mutableStateOf<Uri?>(null) }
    var sheet by remember { mutableStateOf(Sheet.None) }
    var rec by remember { mutableStateOf(RecState.Idle) }
    var seconds by remember { mutableLongStateOf(0L) }
    var count by remember { mutableIntStateOf(0) }
    var countJob by remember { mutableStateOf<Job?>(null) }
    var message by remember { mutableStateOf("") }
    var takeUri by remember { mutableStateOf<Uri?>(null) }
    var takeInterrupted by remember { mutableStateOf(false) }
    var fileToNewScenario by remember { mutableStateOf(false) }
    var takes by remember { mutableStateOf<List<Take>>(emptyList()) }
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
    // The camera runs only on the shooting screen, so the rest of the app does not drain the battery.
    LaunchedEffect(screen, cameraGranted, st.frontCamera, st.quality, st.loudVoice) {
        if (screen != Screen.Studio) engine.unbind()
        else if (cameraGranted) engine.bind(st.frontCamera, st.quality, st.loudVoice) { message = it }
    }
    LaunchedEffect(screen) {
        if (screen == Screen.Studio && !cameraGranted) {
            val wanted = buildList {
                add(Manifest.permission.CAMERA)
                add(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            permissions.launch(wanted.toTypedArray())
        }
        if (screen == Screen.Home) takes = withContext(Dispatchers.IO) { queryTakes(context) }
    }
    BackHandler(enabled = rec == RecState.Idle && sheet == Sheet.None && (screen != Screen.Home || tab != Tab.Texts) && screen != Screen.Welcome) {
        when (screen) {
            Screen.Editor -> screen = editorReturn
            Screen.Player -> screen = playerReturn
            Screen.Home -> tab = Tab.Texts
            else -> screen = Screen.Home
        }
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
                if (fileToNewScenario) {
                    st.createFromFile(name, raw)
                } else {
                    st.loadFile(name, raw)
                }
            }
        }
    }

    fun importNew() {
        sheet = Sheet.None
        fileToNewScenario = true
        filePicker.launch(arrayOf("*/*"))
    }

    fun openEditor(from: Screen) {
        sheet = Sheet.None
        editorReturn = from
        screen = Screen.Editor
    }

    fun writeNew(from: Screen) {
        st.create()
        openEditor(from)
    }

    fun watch(uri: Uri?, from: Screen) {
        sheet = Sheet.None
        playing = uri
        playerReturn = from
        screen = Screen.Player
    }

    when (screen) {
        Screen.Welcome -> WelcomeScreen {
            st.welcomed = true
            st.save()
            screen = Screen.Home
        }
        Screen.Home -> Scaffold(
            containerColor = Ink,
            bottomBar = {
                BottomNav(tab, onTab = { tab = it }, onRecord = { screen = Screen.Studio })
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
                when (tab) {
                    Tab.Texts -> TextsTab(
                        st,
                        takes = takes,
                        message = message,
                        onStartVideo = { screen = Screen.Studio },
                        onOpenRecordings = { tab = Tab.Recordings },
                        onOpenTake = { watch(it.uri, Screen.Home) },
                        onNewText = { sheet = Sheet.NewText },
                        onEdit = { openEditor(Screen.Home) },
                        onShoot = { screen = Screen.Studio },
                    )
                    Tab.Recordings -> RecordingsTab(
                        takes,
                        onOpenTake = { watch(it.uri, Screen.Home) },
                        onStartVideo = { screen = Screen.Studio },
                    )
                    Tab.Settings -> SettingsTab(st)
                }
            }
        }
        Screen.Editor -> EditorScreen(
            st,
            onBack = { screen = editorReturn },
            onPickFile = {
                fileToNewScenario = false
                filePicker.launch(arrayOf("*/*"))
            },
            onUse = { screen = Screen.Studio },
        )
        Screen.Player -> playing?.let { uri -> PlayerScreen(uri, onBack = { screen = playerReturn }) }
        Screen.Studio -> Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(factory = { engine.previewView }, modifier = Modifier.fillMaxSize())

            Prompter(st, Modifier.fillMaxWidth().fillMaxHeight(st.panel / 100f))

            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(Icons.AutoMirrored.Outlined.ArrowBack, "Назад", enabled = rec == RecState.Idle) {
                        screen = Screen.Home
                    }
                    Spacer(Modifier.width(8.dp))
                    RecClock(rec == RecState.Recording, seconds)
                    Spacer(Modifier.weight(1f))
                    IconCircle(
                        Icons.Outlined.Flip,
                        "Зеркало",
                        tint = if (st.mirror) AccentSoft else Fg,
                    ) {
                        st.mirror = !st.mirror
                        st.save()
                    }
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
                StudioPanel(
                    st,
                    recording = rec == RecState.Recording,
                    idle = rec == RecState.Idle,
                    onRec = { onRecButton() },
                    onPickText = { sheet = Sheet.TextPicker },
                    onEdit = { openEditor(Screen.Studio) },
                    onSettings = { sheet = Sheet.Settings },
                )
            }

            if (rec == RecState.Counting) {
                Box(Modifier.fillMaxSize().background(Color(0x590F131A)), contentAlignment = Alignment.Center) {
                    Text("$count", color = Fg, fontSize = 140.sp, fontWeight = FontWeight.ExtraBold)
                }
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
                    Sheet.NewText -> NewTextSheet(
                        onWrite = { writeNew(Screen.Home) },
                        onImport = { importNew() },
                    )
                    Sheet.TextPicker -> TextPickerSheet(
                        st,
                        onPicked = { sheet = Sheet.None },
                        onEdit = { openEditor(Screen.Studio) },
                        onImport = { importNew() },
                        onWrite = { writeNew(Screen.Studio) },
                    )
                    Sheet.Settings -> {
                        SheetTitle("Настройки")
                        SettingsContent(st, recording = rec != RecState.Idle)
                    }
                    Sheet.Take -> TakeSheet(
                        takeUri,
                        takeInterrupted,
                        onWatch = { watch(takeUri, Screen.Studio) },
                        onAgain = { sheet = Sheet.None },
                        onHome = {
                            sheet = Sheet.None
                            tab = Tab.Texts
                            screen = Screen.Home
                        },
                    )
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
        val blocks = st.blocks.ifEmpty { listOf("Текст пуст. Нажмите «Выбрать» внизу или карандаш, чтобы его написать.") }
        val tops = remember(blocks) { mutableStateListOf<Float>().apply { repeat(blocks.size) { add(0f) } } }
        val color = PromptColors.getOrElse(st.textColor) { Fg }

        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { if (st.mirror) scaleX = -1f }
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .offset { IntOffset(0, (readY - st.pos).toInt()) }
                .padding(horizontal = 24.dp)
                .onGloballyPositioned { st.setLayout(tops.toList(), it.size.height.toFloat()) },
        ) {
            blocks.forEachIndexed { i, block ->
                val on by remember(i) { derivedStateOf { st.active == i } }
                Text(
                    block,
                    color = if (on) color else color.copy(alpha = 0.55f),
                    fontSize = st.size.sp,
                    lineHeight = (st.size * 1.3f).sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = (st.size * 0.45f).dp)
                        .onGloballyPositioned { c ->
                            if (i < tops.size) tops[i] = c.positionInParent().y
                        },
                )
            }
        }

        // The reading line: two short marks at the screen edges, so they never cover the text.
        Box(Modifier.offset { IntOffset(0, readY.toInt()) }.size(16.dp, 3.dp).background(Accent))
        Box(
            Modifier.align(Alignment.TopEnd).offset { IntOffset(0, readY.toInt()) }
                .size(16.dp, 3.dp).background(Accent),
        )
    }
}

@Composable
private fun StudioPanel(
    st: PrompterState,
    recording: Boolean,
    idle: Boolean,
    onRec: () -> Unit,
    onPickText: () -> Unit,
    onEdit: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Chip).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp)).background(CardRaised)
                .clickable(enabled = idle, onClick = onPickText).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.AutoMirrored.Outlined.Article, null, tint = AccentSoft)
            Text(
                st.current.displayTitle,
                color = Fg,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("Выбрать", color = AccentSoft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        val value = if (st.timed) st.tempo else st.wpm
        val range = if (st.timed) 50..200 else 50..300
        Text(
            if (st.timed) "Темп $value%" else "$value сл/мин",
            color = AccentSoft,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { st.setSpeed(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
        )
        if (st.cues.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                FilterChip(
                    selected = !st.timed,
                    enabled = idle,
                    onClick = { st.chooseTimecodes(false) },
                    label = { Text("По скорости") },
                )
                FilterChip(
                    selected = st.timed,
                    enabled = idle,
                    onClick = { st.chooseTimecodes(true) },
                    label = { Text("По таймкодам") },
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconCircle(Icons.Outlined.Edit, "Изменить текст", enabled = idle, onClick = onEdit)
            IconCircle(Icons.Outlined.Cameraswitch, "Сменить камеру", enabled = idle) {
                st.frontCamera = !st.frontCamera
                st.save()
            }
            RecButton(recording, onRec)
            IconCircle(Icons.Outlined.SkipPrevious, "К началу текста") {
                st.running = false
                st.rewind()
            }
            IconCircle(
                if (st.running) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                if (st.running) "Пауза" else "Запустить текст",
            ) { st.togglePlay() }
            IconCircle(Icons.Outlined.Tune, "Настройки", onClick = onSettings)
        }
    }
}

@Composable
private fun IconCircle(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    tint: Color = Fg,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(CardRaised).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = if (enabled) tint else Muted)
    }
}

@Composable
private fun RecClock(recording: Boolean, seconds: Long) {
    Row(
        Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(Chip).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (recording) Tally else AccentSoft))
        Text(
            if (recording) "REC ${fmt(seconds)}" else "Готово",
            color = Fg,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun RecButton(recording: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(68.dp).clip(CircleShape).border(3.dp, Fg, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (recording) Box(Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)).background(Tally))
        else Box(Modifier.size(52.dp).clip(CircleShape).background(Tally))
    }
}

@Composable
private fun TakeSheet(
    uri: Uri?,
    interrupted: Boolean,
    onWatch: () -> Unit,
    onAgain: () -> Unit,
    onHome: () -> Unit,
) {
    val context = LocalContext.current
    SheetTitle("Дубль готов")
    Note("Видео уже в галерее, в папке Movies/Teleprompter.")
    if (interrupted) Note("Запись прервалась раньше времени. Сохранено то, что успело записаться.")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = onWatch, modifier = Modifier.weight(1f)) { Text("Посмотреть") }
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
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(onClick = onAgain, modifier = Modifier.weight(1f)) { Text("Ещё дубль") }
        TextButton(onClick = onHome, modifier = Modifier.weight(1f)) { Text("На главную") }
    }
}
