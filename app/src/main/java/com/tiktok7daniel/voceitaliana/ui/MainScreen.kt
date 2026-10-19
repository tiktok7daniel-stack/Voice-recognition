package com.tiktok7daniel.voceitaliana.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tiktok7daniel.voceitaliana.Download
import com.tiktok7daniel.voceitaliana.Engine
import com.tiktok7daniel.voceitaliana.Job
import com.tiktok7daniel.voceitaliana.JobState
import com.tiktok7daniel.voceitaliana.VoceApp
import com.tiktok7daniel.voceitaliana.asr.AsrModel
import kotlinx.coroutines.launch

private val CardShape = RoundedCornerShape(26.dp)

@Composable
fun MainScreen(vm: Engine) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> addFiles(ctx, vm, uris) }

    // The job whose text is being saved, while the system "save as" screen is open.
    var saving by remember { mutableStateOf<Job?>(null) }
    val saveTxt = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val job = saving
        if (uri != null && job != null) ctx.contentResolver.openOutputStream(uri)?.use { it.write(job.text.toByteArray()) }
    }
    val saveSrt = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-subrip")) { uri ->
        val job = saving
        if (uri != null && job != null) ctx.contentResolver.openOutputStream(uri)?.use { it.write(vm.srt(job).toByteArray()) }
    }

    val crashFile = remember { (ctx.applicationContext as VoceApp).crashFile }
    var crash by remember { mutableStateOf(crashFile.takeIf { it.isFile }?.readText()) }
    val anyExpanded = vm.jobs.any { it.expanded }

    GlassBackdrop {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                title = "Voce Italiana",
                subtitle = "استخراج الكلام الإيطالي من أي ملف صوتي",
                leading = { IconOrb(Icons.Rounded.GraphicEq, active = true) },
                actions = {
                    // Folds every transcript into one line (or opens them all again).
                    if (vm.jobs.isNotEmpty()) {
                        IconOrb(if (anyExpanded) Icons.Rounded.UnfoldLess else Icons.Rounded.UnfoldMore) {
                            vm.setAllExpanded(!anyExpanded)
                        }
                    }
                },
            )
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = list,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    crash?.let { report ->
                        item(key = "crash") {
                            CrashCard(report, onCopy = { copy(ctx, report) }, onDismiss = { crash = null; crashFile.delete() })
                        }
                    }
                    item(key = "add") { AddCard(vm) { picker.launch(arrayOf("audio/*", "video/*")) } }
                    item(key = "model") { ModelCard(vm) }
                    vm.download?.let { d -> item(key = "download") { DownloadCard(vm, d) } }
                    items(vm.jobs, key = { it.id }) { job ->
                        JobCard(
                            vm, job,
                            onCopy = { copy(ctx, job.text) },
                            onShare = { share(ctx, job.text) },
                            onTxt = { saving = job; saveTxt.launch("${job.baseName()}.txt") },
                            onSrt = { saving = job; saveSrt.launch("${job.baseName()}.srt") },
                        )
                    }
                    item(key = "footer") { Footer() }
                }

                // Jump to the end of the list instead of scrolling by hand.
                val showJump by remember { derivedStateOf { list.canScrollForward && list.firstVisibleItemIndex > 0 } }
                AnimatedVisibility(
                    visible = showJump,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(20.dp),
                ) {
                    Box(
                        Modifier.size(54.dp)
                            .neonGlow(CircleShape, MaterialTheme.colorScheme.primary, 0.35f, 12.dp)
                            .clip(CircleShape)
                            .background(accentBrush())
                            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                            .clickable { scope.launch { list.animateScrollToItem(list.layoutInfo.totalItemsCount - 1) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.KeyboardDoubleArrowDown, "لآخر الصفحة", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

fun addFiles(ctx: Context, vm: Engine, uris: List<Uri>) {
    if (uris.isEmpty()) return
    if (vm.installed[vm.model] != true) {
        Toast.makeText(ctx, "حمّل الموديل ${vm.model.title} الأول", Toast.LENGTH_LONG).show()
        return
    }
    vm.add(uris)
}

@Composable
private fun AddCard(vm: Engine, onPick: () -> Unit) {
    val ready = vm.installed[vm.model] == true
    Column(
        Modifier.fillMaxWidth().glass(CardShape, accent = true, alpha = 0.45f).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlowIcon(Icons.Rounded.AudioFile, if (ready) Accent.Mint else Accent.Amber)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("الملفات الصوتية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = ink())
                Text(
                    "اختار ملف أو أكتر — ${Engine.MAX_PARALLEL} بيشتغلوا مع بعض في نفس الوقت",
                    style = MaterialTheme.typography.labelMedium, color = ink(0.7f),
                )
            }
        }
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .glass(RoundedCornerShape(18.dp), alpha = 0.3f)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                .clickable(onClick = onPick)
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Rounded.UploadFile, null, tint = softPrimary(), modifier = Modifier.size(34.dp))
            Text("اختار ملفات من الموبايل", fontWeight = FontWeight.Bold, color = ink(0.8f))
            Text(
                if (ready) "mp3 · m4a · wav · opus · ogg · mp4 — أو شارك الصوت من واتساب"
                else "حمّل الموديل الأول من تحت",
                style = MaterialTheme.typography.labelSmall, color = ink(0.55f),
            )
        }
    }
}

@Composable
private fun ModelCard(vm: Engine) {
    val installed = vm.installed[vm.model] == true
    // Folded by default once a model is ready, so the transcripts sit higher on screen.
    var open by rememberSaveable { mutableStateOf(!installed) }
    Column(
        Modifier.fillMaxWidth().glass(CardShape).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle(
                "الموديل",
                subtitle = if (open) "مجاني ومفتوح المصدر · بيشتغل من غير نت" else "${vm.model.title}${if (installed) " · متحمّل" else ""}",
                modifier = Modifier.weight(1f),
            )
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = ink(0.6f))
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AsrModel.entries.forEach { m -> ModelRow(vm, m) }
            }
        }
        if (!installed && !vm.downloading) {
            NeonButton("تحميل ${vm.model.title} (${vm.model.sizeLabel})", Modifier.fillMaxWidth(), Icons.Rounded.Download, onClick = vm::downloadModel)
        }
    }
}

@Composable
private fun ModelRow(vm: Engine, m: AsrModel) {
    val selected = vm.model == m
    val installed = vm.installed[m] == true
    Row(
        Modifier.fillMaxWidth()
            .glass(RoundedCornerShape(18.dp), accent = selected, alpha = if (selected) 0.5f else 0.3f)
            .clickable(enabled = !vm.downloading) { vm.select(m) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconOrb(if (installed) Icons.Rounded.CheckCircle else Icons.Rounded.Memory, active = selected, size = 38)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.title, fontWeight = FontWeight.Black, color = ink(), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (installed) "متحمّل" else m.sizeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (installed) Accent.Mint else ink(0.5f),
                )
            }
            Text(m.tagline, style = MaterialTheme.typography.labelSmall, color = ink(0.7f))
        }
        if (installed && !vm.busy) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).clickable { vm.deleteModel(m) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Delete, "حذف", tint = ink(0.45f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun DownloadCard(vm: Engine, d: Download) {
    Column(
        Modifier.fillMaxWidth().glass(CardShape).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (d) {
            is Download.Running -> {
                val mb = { b: Long -> b / 1_048_576 }
                StatusHeader(
                    Icons.Rounded.Download, Accent.Amber, false, "بحمّل ${d.model.title}",
                    (if (d.total > 0) "${mb(d.done)} / ${mb(d.total)} MB" else "${mb(d.done)} MB") +
                        " — مرة واحدة بس\nتقدر تقفل البرنامج، التحميل هيكمّل في الخلفية",
                )
                if (d.total > 0) GlowBar(d.done.toFloat() / d.total) else IndeterminateGlowBar()
                GhostButton("إيقاف التحميل", Modifier.fillMaxWidth(), Icons.Rounded.Close, onClick = vm::cancelDownload)
            }
            is Download.Failed -> {
                StatusHeader(Icons.Rounded.Error, Accent.Crimson, false, "التحميل وقف", d.detail)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeonButton("كمّل التحميل", Modifier.weight(1f), Icons.Rounded.Download, onClick = vm::downloadModel)
                    GhostButton("إخفاء", Modifier.weight(1f), Icons.Rounded.Close, onClick = vm::dismissDownloadError)
                }
            }
        }
    }
}

@Composable
private fun StatusHeader(icon: ImageVector, color: Color, pulse: Boolean, title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GlowIcon(icon, color, pulse = pulse)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = ink())
            Text(detail, style = MaterialTheme.typography.labelMedium, color = ink(0.7f))
        }
    }
}

/** One audio file. Tap its header to fold it to a single line or open it again. */
@Composable
private fun JobCard(vm: Engine, job: Job, onCopy: () -> Unit, onShare: () -> Unit, onTxt: () -> Unit, onSrt: () -> Unit) {
    val st = job.state
    val (icon, color) = when (st) {
        is JobState.Working -> Icons.Rounded.GraphicEq to Accent.Ember
        is JobState.Done -> Icons.Rounded.CheckCircle to Accent.Mint
        is JobState.Failed -> Icons.Rounded.Error to Accent.Crimson
        JobState.Cancelled -> Icons.Rounded.Stop to Accent.Crimson
        else -> Icons.Rounded.HourglassTop to Accent.Amber
    }
    val status = when (st) {
        JobState.Importing -> "بجهّز الملف"
        JobState.Waiting -> "مستني دوره — بيشتغل ${Engine.MAX_PARALLEL} في نفس الوقت"
        JobState.Loading -> "بجهّز الموديل"
        is JobState.Working -> {
            val at = if (job.durationMs > 0) " — ${Engine.clock(st.progress * job.durationMs / 1000f)} من ${Engine.clock(job.durationMs / 1000f)}" else ""
            "${(st.progress * 100).toInt()}%$at"
        }
        is JobState.Done -> "${job.segments.size} جملة · ${job.model.title} · في ${Engine.clock(st.seconds)}"
        is JobState.Failed -> "${st.title}: ${st.detail}"
        JobState.Cancelled -> "اتوقف"
    }

    Column(
        Modifier.fillMaxWidth().glass(CardShape, accent = job.expanded, alpha = 0.45f).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { job.expanded = !job.expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlowIcon(icon, color, size = 46, pulse = st is JobState.Working)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(job.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = ink(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(status, style = MaterialTheme.typography.labelSmall, color = ink(0.7f), maxLines = if (job.expanded) 3 else 1, overflow = TextOverflow.Ellipsis)
                if (!job.expanded && job.text.isNotBlank()) {
                    Text(job.text, style = MaterialTheme.typography.labelSmall, color = ink(0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(if (job.expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = ink(0.6f))
        }
        if (st is JobState.Working) GlowBar(st.progress)
        else if (job.busy) IndeterminateGlowBar()

        AnimatedVisibility(job.expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (job.text.isNotEmpty() || st is JobState.Done) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Italiano", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Chip("توقيتات", selected = vm.showTimestamps, onClick = vm::toggleTimestamps)
                    }
                    Box(
                        Modifier.fillMaxWidth()
                            .heightIn(min = 120.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .glass(RoundedCornerShape(18.dp), alpha = 0.25f)
                            .padding(14.dp),
                    ) {
                        // Italian is left-to-right even though the UI is Arabic.
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            BasicTextField(
                                value = job.text,
                                onValueChange = { job.text = it },
                                readOnly = job.busy,
                                textStyle = TextStyle(color = ink(), fontSize = 17.sp, lineHeight = 27.sp, textDirection = TextDirection.Ltr),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    val enabled = job.text.isNotBlank()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton("نسخ", Modifier.weight(1f), Icons.Rounded.ContentCopy, enabled, onCopy)
                        GhostButton("مشاركة", Modifier.weight(1f), Icons.Rounded.Share, enabled, onShare)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton("حفظ TXT", Modifier.weight(1f), Icons.Rounded.Description, enabled && !job.busy, onTxt)
                        GhostButton("ترجمة SRT", Modifier.weight(1f), Icons.Rounded.Subtitles, enabled && !job.busy, onSrt)
                    }
                }
                if (job.busy) {
                    GhostButton("إيقاف", Modifier.fillMaxWidth(), Icons.Rounded.Stop) { vm.cancel(job) }
                } else {
                    GhostButton("حذف من القائمة", Modifier.fillMaxWidth(), Icons.Rounded.Delete) { vm.remove(job) }
                }
            }
        }
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
private fun Footer() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Lock, null, tint = ink(0.5f), modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            "كل المعالجة على موبايلك — الملفات مش بتترفع على أي سيرفر",
            style = MaterialTheme.typography.labelSmall, color = ink(0.5f),
        )
    }
}

private fun copy(ctx: Context, text: String) {
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
        StatusHeader(Icons.Rounded.Error, Accent.Crimson, false, "البرنامج قفل المرة اللي فاتت", "انسخ التقرير وابعته عشان أصلّح المشكلة")
        Text(
            report.lineSequence().take(8).joinToString("\n"),
            style = MaterialTheme.typography.labelSmall, color = ink(0.6f),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).glass(RoundedCornerShape(14.dp), alpha = 0.25f).padding(10.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeonButton("نسخ التقرير", Modifier.weight(1f), Icons.Rounded.ContentCopy, onClick = onCopy)
            GhostButton("إخفاء", Modifier.weight(1f), Icons.Rounded.Close, onClick = onDismiss)
        }
    }
}

private fun share(ctx: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    ctx.startActivity(Intent.createChooser(send, "مشاركة النص"))
}
