package com.tiktok7daniel.voceitaliana.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import com.tiktok7daniel.voceitaliana.MainViewModel
import com.tiktok7daniel.voceitaliana.Phase
import com.tiktok7daniel.voceitaliana.asr.AsrModel

private val CardShape = RoundedCornerShape(26.dp)

@Composable
fun MainScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::pick) }
    val saveTxt = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let { ctx.contentResolver.openOutputStream(it)?.use { o -> o.write(vm.text.toByteArray()) } }
    }
    val saveSrt = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-subrip")) { uri ->
        uri?.let { ctx.contentResolver.openOutputStream(it)?.use { o -> o.write(vm.srt().toByteArray()) } }
    }

    GlassBackdrop {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                title = "Voce Italiana",
                subtitle = "استخراج الكلام الإيطالي من أي ملف صوتي",
                leading = { IconOrb(Icons.Rounded.GraphicEq, active = true) },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { FileCard(vm) { picker.launch(arrayOf("audio/*", "video/*")) } }
                item { ModelCard(vm) }
                if (vm.phase !is Phase.Idle) item { StatusCard(vm) }
                if (vm.segments.isNotEmpty() || vm.phase is Phase.Done) {
                    item {
                        ResultCard(
                            vm,
                            onCopy = {
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Trascrizione", vm.text))
                                Toast.makeText(ctx, "اتنسخ", Toast.LENGTH_SHORT).show()
                            },
                            onShare = { share(ctx, vm.text) },
                            onTxt = { saveTxt.launch("${vm.baseName()}.txt") },
                            onSrt = { saveSrt.launch("${vm.baseName()}.srt") },
                        )
                    }
                }
                item { Footer() }
            }
        }
    }
}

@Composable
private fun FileCard(vm: MainViewModel, onPick: () -> Unit) {
    val f = vm.file
    Column(
        Modifier.fillMaxWidth().glass(CardShape, accent = true, alpha = 0.45f).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlowIcon(Icons.Rounded.AudioFile, if (f != null) Accent.Mint else Accent.Amber)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("الملف الصوتي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)
                Text(
                    if (f != null) f.name else "mp3 · m4a · wav · opus · ogg · mp4",
                    style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (f != null && f.durationMs > 0) {
                    Text(
                        "المدة ${MainViewModel.clock(f.durationMs / 1000f)}",
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
                tint = lerp(MaterialTheme.colorScheme.primary, Color.White, 0.55f), modifier = Modifier.size(34.dp),
            )
            Text(
                if (f == null) "اختار ملف من الموبايل" else "اختار ملف تاني",
                fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.75f),
            )
            Text(
                "أو اعمل مشاركة للصوت من واتساب لـ Voce Italiana",
                style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun ModelCard(vm: MainViewModel) {
    val installed = vm.installed[vm.model] == true
    Column(
        Modifier.fillMaxWidth().glass(CardShape).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle("الموديل", subtitle = "مجاني ومفتوح المصدر · بيشتغل من غير نت")
        AsrModel.entries.forEach { m -> ModelRow(vm, m) }
        when {
            vm.phase is Phase.Working || vm.phase is Phase.Loading || vm.phase is Phase.Downloading ->
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
            Text("اختار ملف صوتي الأول", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun ModelRow(vm: MainViewModel, m: AsrModel) {
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
                Text(m.title, fontWeight = FontWeight.Black, color = Color.White, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (installed) "متحمّل" else m.sizeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (installed) Accent.Mint else Color.White.copy(alpha = 0.5f),
                )
            }
            Text(m.tagline, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
        }
        if (installed && !vm.busy) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).clickable { vm.deleteModel(m) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Delete, "حذف", tint = Color.White.copy(alpha = 0.45f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun StatusCard(vm: MainViewModel) {
    val p = vm.phase
    data class S(val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: Color, val pulse: Boolean, val title: String, val detail: String, val fraction: Float?)
    val s = when (p) {
        is Phase.Downloading -> {
            val mb = { b: Long -> b / 1_048_576 }
            S(
                Icons.Rounded.Download, Accent.Amber, false, "بحمّل ${p.model.title}",
                if (p.total > 0) "${mb(p.done)} / ${mb(p.total)} MB — مرة واحدة بس" else "${mb(p.done)} MB",
                if (p.total > 0) p.done.toFloat() / p.total else null,
            )
        }
        Phase.Loading -> S(Icons.Rounded.HourglassTop, Accent.Amber, false, "بجهّز الموديل", "ثواني وهيبدأ", null)
        is Phase.Working -> {
            val f = vm.file
            val at = if (f != null && f.durationMs > 0) " — وصلت ${MainViewModel.clock(p.progress * f.durationMs / 1000f)} من ${MainViewModel.clock(f.durationMs / 1000f)}" else ""
            S(Icons.Rounded.GraphicEq, Accent.Ember, true, "بستخرج الكلام الإيطالي", "${(p.progress * 100).toInt()}%$at", p.progress)
        }
        is Phase.Done -> S(
            Icons.Rounded.CheckCircle, Accent.Mint, false, "خلصنا",
            "${vm.segments.size} جملة في ${MainViewModel.clock(p.seconds)} — تقدر تعدّل النص", 1f,
        )
        is Phase.Failed -> S(Icons.Rounded.Error, Accent.Crimson, false, p.title, p.detail, null)
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
                Text(s.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)
                Text(s.detail, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
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
private fun ResultCard(vm: MainViewModel, onCopy: () -> Unit, onShare: () -> Unit, onTxt: () -> Unit, onSrt: () -> Unit) {
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
                    textStyle = TextStyle(color = Color.White, fontSize = 17.sp, lineHeight = 27.sp, textDirection = TextDirection.Ltr),
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
    }
}

@Composable
private fun Footer() {
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Lock, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            "كل المعالجة على موبايلك — الملف مش بيترفع على أي سيرفر",
            style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f),
        )
    }
}

private fun share(ctx: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    ctx.startActivity(Intent.createChooser(send, "مشاركة النص"))
}
