package com.tiktok7daniel.voceitaliana.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.tiktok7daniel.voceitaliana.Engine
import com.tiktok7daniel.voceitaliana.Phase
import com.tiktok7daniel.voceitaliana.VoceApp
import com.tiktok7daniel.voceitaliana.asr.AsrModel

private val CardShape = RoundedCornerShape(30.dp)

@Composable
fun MainScreen(vm: Engine) {
    var page by remember { mutableStateOf<Page>(Page.Home) }
    val lib = vm.library
    // Files picked for a multi-file job, waiting for the user to choose where to save them.
    var pendingBatch by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    val pickMany = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) pendingBatch = uris
    }
    val onPickMany = { if (!vm.busy) pickMany.launch(arrayOf("audio/*", "video/*")) }
    if (pendingBatch.isNotEmpty()) {
        SaveToGroupDialog(
            lib, null, onDismiss = { pendingBatch = emptyList() },
            heading = "هحفظ ${pendingBatch.size} ملف فين؟",
        ) { group, sectionId, _ ->
            vm.startBatch(pendingBatch, group.id, sectionId)
            pendingBatch = emptyList()
            page = Page.Home
        }
    }
    Box(Modifier.fillMaxSize()) {
        Pages(vm, page, onPickMany) { page = it }
        if (page == Page.Home || page == Page.Groups) {
            BottomBar(
                listOf(
                    NavItem(Icons.Rounded.Home, "الرئيسية", page == Page.Home) { page = Page.Home },
                    NavItem(Icons.Rounded.LibraryAdd, "ملفات", false, onPickMany),
                    NavItem(Icons.Rounded.Folder, "المكتبة", page == Page.Groups) { page = Page.Groups },
                ),
                Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun Pages(vm: Engine, current: Page, onPickMany: () -> Unit, go: (Page) -> Unit) {
    val lib = vm.library
    when (val p = current) {
        Page.Home -> HomePage(
            vm, onLibrary = { go(Page.Groups) }, onPickMany = onPickMany,
            onSaved = { g, sec -> go(if (sec.isEmpty()) Page.OneGroup(g) else Page.OneSection(g, sec)) },
        )
        Page.Groups -> GroupsPage(lib, onBack = { go(Page.Home) }, onOpen = { go(Page.OneGroup(it.id)) })
        is Page.OneGroup -> GroupPage(
            lib, p.groupId,
            onBack = { go(Page.Groups) },
            onSection = { go(Page.OneSection(p.groupId, it)) },
            onOpen = { go(Page.OneNote(it.groupId, it.id)) },
        )
        is Page.OneSection -> SectionPage(
            lib, p.groupId, p.sectionId,
            onBack = { go(Page.OneGroup(p.groupId)) },
            onOpen = { go(Page.OneNote(it.groupId, it.id)) },
        )
        is Page.OneNote -> NotePage(
            lib, p.groupId, p.noteId,
            // Back to wherever the note lives now (its section, or the group itself).
            onBack = {
                val section = lib.note(p.groupId, p.noteId)?.sectionId.orEmpty()
                go(if (section.isNotEmpty()) Page.OneSection(p.groupId, section) else Page.OneGroup(p.groupId))
            },
            onMoved = { go(Page.OneNote(it.groupId, it.id)) },
        )
    }
}

@Composable
private fun HomePage(vm: Engine, onLibrary: () -> Unit, onPickMany: () -> Unit, onSaved: (groupId: String, sectionId: String) -> Unit) {
    val ctx = LocalContext.current
    var saving by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::pick) }
    val saveTxt = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let { ctx.contentResolver.openOutputStream(it)?.use { o -> o.write(vm.text.toByteArray()) } }
    }
    val saveSrt = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-subrip")) { uri ->
        uri?.let { ctx.contentResolver.openOutputStream(it)?.use { o -> o.write(vm.srt().toByteArray()) } }
    }

    val crashFile = remember { (ctx.applicationContext as VoceApp).crashFile }
    var crash by remember { mutableStateOf(crashFile.takeIf { it.isFile }?.readText()) }

    // Re-checked whenever the user comes back from the system battery screen.
    var backgroundAllowed by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        backgroundAllowed = isBackgroundAllowed(ctx)
        onPauseOrDispose {}
    }

    if (saving) {
        SaveToGroupDialog(vm.library, vm.baseName(), onDismiss = { saving = false }) { group, sectionId, title ->
            saving = false
            vm.library.addNote(group.id, sectionId, title, vm.text, vm.file?.name.orEmpty())
            Toast.makeText(ctx, "اتحفظ في \"${group.name}\"", Toast.LENGTH_SHORT).show()
            onSaved(group.id, sectionId)
        }
    }

    GlassBackdrop {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                title = "أهلاً بيك 👋",
                subtitle = "Voce Italiana · الكلام الإيطالي لنص",
                leading = { GlowIcon(Icons.Rounded.GraphicEq, Mono.Black, size = 46) },
                actions = { IconOrb(Icons.Rounded.Folder, onClick = onLibrary) },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { SectionTitle("الرئيسية", subtitle = "اختار فويس، وسيب الباقي علينا") }
                item {
                    val lib = vm.library
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        StatTile(
                            Icons.Rounded.Description, "التسجيلات", "${lib.groups.sumOf { it.notes.size }}", "محفوظين في المكتبة",
                            Modifier.weight(1f), onClick = onLibrary,
                        )
                        StatTile(
                            Icons.Rounded.Folder, "المجموعات", "${lib.groups.size}", "${lib.groups.sumOf { it.sections.size }} قسم",
                            Modifier.weight(1f), onClick = onLibrary,
                        )
                    }
                }
                crash?.let { report -> item { CrashCard(report, onCopy = { copy(ctx, report) }, onDismiss = { crash = null; crashFile.delete() }) } }
                if (!backgroundAllowed) item { BackgroundCard { askBackground(ctx) } }
                item {
                    FileCard(
                        vm,
                        onPick = { picker.launch(arrayOf("audio/*", "video/*")) },
                        onPickMany = onPickMany,
                    )
                }
                if (vm.batch.isNotEmpty()) item { BatchCard(vm, onOpen = onLibrary) }
                item { ModelCard(vm) }
                if (vm.phase !is Phase.Idle) item { StatusCard(vm) }
                if (vm.segments.isNotEmpty() || vm.phase is Phase.Done) {
                    item {
                        ResultCard(
                            vm,
                            onCopy = { copy(ctx, vm.text) },
                            onShare = { share(ctx, vm.text) },
                            onTxt = { saveTxt.launch("${vm.baseName()}.txt") },
                            onSrt = { saveSrt.launch("${vm.baseName()}.srt") },
                            onSaveToGroup = { saving = true },
                        )
                    }
                }
                item { Footer() }
            }
        }
    }
}

@Composable
private fun FileCard(vm: Engine, onPick: () -> Unit, onPickMany: () -> Unit) {
    val f = vm.file
    Column(
        Modifier.fillMaxWidth().glass(CardShape, accent = true, alpha = 0.45f).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlowIcon(Icons.Rounded.AudioFile, if (f != null) Accent.Mint else Accent.Amber)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("الملف الصوتي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Ink)
                Text(
                    if (f != null) f.name else "mp3 · m4a · wav · opus · ogg · mp4",
                    style = MaterialTheme.typography.labelMedium, color = Ink.copy(alpha = 0.7f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (f != null && f.durationMs > 0) {
                    Text(
                        "المدة ${Engine.clock(f.durationMs / 1000f)}",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        val dash = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .glass(RoundedCornerShape(18.dp), alpha = 0.3f)
                .border(1.dp, dash, RoundedCornerShape(18.dp))
                .clickable(enabled = !vm.busy, onClick = onPick)
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Rounded.UploadFile, null,
                tint = lerp(MaterialTheme.colorScheme.primary, Ink, 0.55f), modifier = Modifier.size(34.dp),
            )
            Text(
                if (f == null) "اختار ملف من الموبايل" else "اختار ملف تاني",
                fontWeight = FontWeight.Bold, color = Ink.copy(alpha = 0.75f),
            )
            Text(
                "أو اعمل مشاركة للصوت من واتساب لـ Voce Italiana",
                style = MaterialTheme.typography.labelSmall, color = Ink.copy(alpha = 0.5f),
            )
        }
        GhostButton("كذا ملف مرة واحدة (تتحفظ في مجموعة)", Modifier.fillMaxWidth(), Icons.Rounded.LibraryAdd, enabled = !vm.busy, onClick = onPickMany)
    }
}

/** Checklist of a multi-file job: each file and whether it was transcribed and saved. */
@Composable
private fun BatchCard(vm: Engine, onOpen: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().glass(CardShape, alpha = 0.45f).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionTitle("الملفات", subtitle = "${vm.batch.count { it.ok == true }} من ${vm.batch.size} اتحفظوا")
        vm.batch.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when (item.ok) {
                        true -> Icons.Rounded.CheckCircle
                        false -> Icons.Rounded.Error
                        null -> Icons.Rounded.Schedule
                    },
                    null,
                    tint = when (item.ok) {
                        true -> Accent.Mint
                        false -> Accent.Crimson
                        null -> Ink.copy(alpha = 0.5f)
                    },
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.name, color = Ink, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.state, style = MaterialTheme.typography.labelSmall, color = Ink.copy(alpha = 0.6f), maxLines = 2)
                }
            }
        }
        if (!vm.busy) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonButton("افتح المجموعات", Modifier.weight(1f), Icons.Rounded.Folder, onClick = onOpen)
                GhostButton("تمام", Modifier.weight(1f), Icons.Rounded.Close, onClick = vm::clearBatch)
            }
        }
    }
}

@Composable
private fun ModelCard(vm: Engine) {
    val installed = vm.installed[vm.model] == true
    Column(
        Modifier.fillMaxWidth().glass(CardShape).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle("الموديل", subtitle = "مجاني ومفتوح المصدر · بيشتغل من غير نت")
        AsrModel.entries.forEach { m -> ModelRow(vm, m) }
        when {
            vm.phase is Phase.Working || vm.phase is Phase.Loading || vm.phase is Phase.Downloading || vm.phase is Phase.Batch ->
                GhostButton("إيقاف", Modifier.fillMaxWidth(), Icons.Rounded.Close, onClick = vm::cancel)
            !installed ->
                NeonButton("تحميل ${vm.model.title} (${vm.model.sizeLabel})", Modifier.fillMaxWidth(), Icons.Rounded.Download, onClick = vm::download)
            else ->
                NeonButton(
                    "ابدأ استخراج الكلام", Modifier.fillMaxWidth(), Icons.Rounded.PlayArrow,
                    enabled = vm.file != null, onClick = vm::start,
                )
        }
        if (installed && vm.file == null && !vm.busy) {
            Text("اختار ملف صوتي الأول", style = MaterialTheme.typography.labelMedium, color = Ink.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun ModelRow(vm: Engine, m: AsrModel) {
    val selected = vm.model == m
    val installed = vm.installed[m] == true
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth()
            .glass(shape, accent = selected, alpha = if (selected) 0.5f else 0.3f)
            .clickable(enabled = !vm.busy) { vm.select(m) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconOrb(if (installed) Icons.Rounded.CheckCircle else Icons.Rounded.Memory, active = selected, size = 38)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.title, fontWeight = FontWeight.Black, color = Ink, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (installed) "متحمّل" else m.sizeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (installed) Accent.Mint else Ink.copy(alpha = 0.5f),
                )
            }
            Text(m.tagline, style = MaterialTheme.typography.labelSmall, color = Ink.copy(alpha = 0.7f))
        }
        if (installed && !vm.busy) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).clickable { vm.deleteModel(m) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Delete, "حذف", tint = Ink.copy(alpha = 0.45f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun StatusCard(vm: Engine) {
    val p = vm.phase
    data class S(val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: Color, val pulse: Boolean, val title: String, val detail: String, val fraction: Float?)
    val s = when (p) {
        is Phase.Downloading -> {
            val mb = { b: Long -> b / 1_048_576 }
            S(
                Icons.Rounded.Download, Accent.Amber, false, "بحمّل ${p.model.title}",
                (if (p.total > 0) "${mb(p.done)} / ${mb(p.total)} MB — مرة واحدة بس" else "${mb(p.done)} MB") +
                    "\nتقدر تقفل البرنامج — التحميل هيكمّل في الخلفية",
                if (p.total > 0) p.done.toFloat() / p.total else null,
            )
        }
        Phase.Importing -> S(Icons.Rounded.UploadFile, Accent.Amber, false, "بجهّز الملف", "بنسخه جوه التطبيق عشان يكمّل حتى لو قفلته", null)
        Phase.Loading -> S(Icons.Rounded.HourglassTop, Accent.Amber, false, "بجهّز الموديل", "ثواني وهيبدأ", null)
        is Phase.Working -> {
            val f = vm.file
            val at = if (f != null && f.durationMs > 0) " — وصلت ${Engine.clock(p.progress * f.durationMs / 1000f)} من ${Engine.clock(f.durationMs / 1000f)}" else ""
            S(
                Icons.Rounded.GraphicEq, Accent.Ember, true, "بستخرج الكلام الإيطالي",
                "${(p.progress * 100).toInt()}%$at\nتقدر تقفل البرنامج — هيكمّل في الخلفية ويبعتلك إشعار لما يخلص", p.progress,
            )
        }
        is Phase.Done -> S(
            Icons.Rounded.CheckCircle, Accent.Mint, false, "خلصنا",
            "${vm.segments.size} جملة في ${Engine.clock(p.seconds)} — تقدر تعدّل النص", 1f,
        )
        is Phase.Failed -> S(Icons.Rounded.Error, Accent.Crimson, false, p.title, p.detail, null)
        is Phase.Batch -> S(
            Icons.Rounded.GraphicEq, Accent.Ember, true, "بستخرج ${p.index + 1} من ${p.total}",
            "${p.name} — ${(p.progress * 100).toInt()}%\nكل ملف بيتحفظ لوحده أول ما يخلص", (p.index + p.progress) / p.total,
        )
        is Phase.BatchDone -> S(
            Icons.Rounded.CheckCircle, Accent.Mint, false, "خلصنا ${p.saved} ملف",
            if (p.failed > 0) "${p.failed} ماخلصوش — شوف القايمة" else "كلهم اتحفظوا في المجموعة", 1f,
        )
        Phase.Idle -> return
    }
    Column(
        Modifier.fillMaxWidth().glass(CardShape).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlowIcon(s.icon, s.color, pulse = s.pulse)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(s.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Ink)
                Text(s.detail, style = MaterialTheme.typography.labelMedium, color = Ink.copy(alpha = 0.7f))
            }
        }
        if (s.fraction != null) GlowBar(s.fraction) else if (p !is Phase.Failed) IndeterminateGlowBar()
    }
}

@Composable
private fun IndeterminateGlowBar() {
    val t = rememberInfiniteTransition(label = "bar")
    val v = t.animateFloat(
        initialValue = 0.05f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "barValue",
    )
    GlowBar(v.value)
}

@Composable
private fun ResultCard(vm: Engine, onCopy: () -> Unit, onShare: () -> Unit, onTxt: () -> Unit, onSrt: () -> Unit, onSaveToGroup: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().glass(CardShape, accent = true, alpha = 0.45f).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle("النص", subtitle = "Italiano") {
            Chip("توقيتات", selected = vm.showTimestamps, onClick = vm::toggleTimestamps)
        }
        Box(
            Modifier.fillMaxWidth()
                .heightIn(min = 160.dp)
                .clip(RoundedCornerShape(18.dp))
                .glass(RoundedCornerShape(18.dp), alpha = 0.25f)
                .padding(14.dp),
        ) {
            // Italian is left-to-right even though the UI is Arabic.
            androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                BasicTextField(
                    value = vm.text,
                    onValueChange = { vm.text = it },
                    readOnly = vm.busy,
                    textStyle = TextStyle(color = Ink, fontSize = 17.sp, lineHeight = 27.sp, textDirection = TextDirection.Ltr),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        val enabled = vm.text.isNotBlank()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("نسخ", Modifier.weight(1f), Icons.Rounded.ContentCopy, enabled, onCopy)
            GhostButton("مشاركة", Modifier.weight(1f), Icons.Rounded.Share, enabled, onShare)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("حفظ TXT", Modifier.weight(1f), Icons.Rounded.Description, enabled && !vm.busy, onTxt)
            GhostButton("ترجمة SRT", Modifier.weight(1f), Icons.Rounded.Subtitles, enabled && !vm.busy, onSrt)
        }
        NeonButton("حفظ في مجموعة", Modifier.fillMaxWidth(), Icons.Rounded.CreateNewFolder, enabled && !vm.busy, onSaveToGroup)
    }
}

/**
 * Phones (Xiaomi, Samsung, Oppo…) kill background apps to save battery even while they show a
 * notification. Being exempt from battery optimisation is what keeps long jobs alive.
 */
@Composable
private fun BackgroundCard(onAllow: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().glass(CardShape, alpha = 0.45f).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlowIcon(Icons.Rounded.BatteryAlert, Accent.Amber)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("خليه يشتغل في الخلفية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Ink)
                Text(
                    "الموبايل ممكن يقفل البرنامج عشان يوفّر البطارية. اسمح له مرة واحدة والتحميل والاستخراج هيكمّلوا حتى لو قفلت الشاشة",
                    style = MaterialTheme.typography.labelMedium, color = Ink.copy(alpha = 0.7f),
                )
            }
        }
        NeonButton("اسمح بالشغل في الخلفية", Modifier.fillMaxWidth(), Icons.Rounded.BatteryAlert, onClick = onAllow)
    }
}

private fun isBackgroundAllowed(ctx: Context): Boolean =
    (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)

private fun askBackground(ctx: Context) {
    val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
    try {
        ctx.startActivity(direct)
    } catch (e: Exception) {
        // Some phones hide that dialog; the full list still lets the user pick the app.
        runCatching { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }
}

@Composable
private fun Footer() {
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Lock, null, tint = Ink.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            "كل المعالجة على موبايلك — الملف مش بيترفع على أي سيرفر",
            style = MaterialTheme.typography.labelSmall, color = Ink.copy(alpha = 0.5f),
        )
    }
}

internal fun copy(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Voce Italiana", text))
    Toast.makeText(ctx, "اتنسخ", Toast.LENGTH_SHORT).show()
}

/** Shown after a crash so the report can be sent to the developer. */
@Composable
private fun CrashCard(report: String, onCopy: () -> Unit, onDismiss: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().glass(CardShape, accent = true, alpha = 0.45f).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlowIcon(Icons.Rounded.Error, Accent.Crimson)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("البرنامج قفل المرة اللي فاتت", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Ink)
                Text("انسخ التقرير وابعته عشان أصلّح المشكلة", style = MaterialTheme.typography.labelMedium, color = Ink.copy(alpha = 0.7f))
            }
        }
        Text(
            report.lineSequence().take(8).joinToString("\n"),
            style = MaterialTheme.typography.labelSmall, color = Ink.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).glass(RoundedCornerShape(14.dp), alpha = 0.25f).padding(10.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeonButton("نسخ التقرير", Modifier.weight(1f), Icons.Rounded.ContentCopy, onClick = onCopy)
            GhostButton("إخفاء", Modifier.weight(1f), Icons.Rounded.Close, onClick = onDismiss)
        }
    }
}

internal fun share(ctx: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    ctx.startActivity(Intent.createChooser(send, "مشاركة النص"))
}
