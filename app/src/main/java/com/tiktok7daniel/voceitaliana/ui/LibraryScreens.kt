package com.tiktok7daniel.voceitaliana.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Topic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.window.Dialog
import com.tiktok7daniel.voceitaliana.library.Group
import com.tiktok7daniel.voceitaliana.library.Library
import com.tiktok7daniel.voceitaliana.library.Note
import com.tiktok7daniel.voceitaliana.library.PdfExport
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val NoteShape = RoundedCornerShape(30.dp)
private val Arabic = Locale("ar")

/** Which page of the app is showing. Kept tiny on purpose: no navigation library needed. */
sealed interface Page {
    data object Home : Page
    data object Groups : Page
    data class OneGroup(val groupId: String) : Page
    data class OneSection(val groupId: String, val sectionId: String) : Page
    data class OneNote(val groupId: String, val noteId: String) : Page
}

@Composable
fun GroupsPage(lib: Library, onBack: () -> Unit, onOpen: (Group) -> Unit) {
    var creating by remember { mutableStateOf(false) }
    PageScaffold("المجموعات", "${lib.groups.size} مجموعة", onBack) {
        item {
            NeonButton("مجموعة جديدة", Modifier.fillMaxWidth(), Icons.Rounded.CreateNewFolder) { creating = true }
        }
        if (lib.groups.isEmpty()) {
            item { EmptyHint("لسه مفيش مجموعات", "اعمل مجموعة لكل مادة، وجواها أقسام لكل فصل أو درس") }
        }
        items(lib.groups, key = { it.id }) { g ->
            FolderRow(
                Icons.Rounded.Folder, Accent.Amber, g.name,
                "${g.sections.size} قسم · ${g.notes.size} تسجيل · آخر حاجة ${dayLabel(g.lastActivity)}",
            ) { onOpen(g) }
        }
    }
    if (creating) {
        NameDialog("مجموعة جديدة", "اسم المجموعة (مثلاً اسم المادة)", "", onDismiss = { creating = false }) {
            creating = false
            onOpen(lib.createGroup(it))
        }
    }
}

@Composable
fun GroupPage(lib: Library, groupId: String, onBack: () -> Unit, onSection: (String) -> Unit, onOpen: (Note) -> Unit) {
    val g = lib.group(groupId) ?: return GoBack(onBack)
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val loose = g.notesIn("")
    PageScaffold(
        g.name, "${g.sections.size} قسم · ${g.notes.size} تسجيل", onBack,
        actions = {
            IconOrb(Icons.Rounded.Edit, onClick = { renaming = true })
            Spacer(Modifier.width(8.dp))
            IconOrb(Icons.Rounded.Delete, onClick = { deleting = true })
        },
    ) {
        if (g.notes.isNotEmpty()) {
            item {
                PdfButtons(g.name, g.name) {
                    // Sections in the order they were made, each oldest-first like a notebook.
                    g.sections.flatMap { s -> g.notesIn(s.id).sortedBy { it.created }.map { PdfExport.Entry(it, s.name) } } +
                        loose.sortedBy { it.created }.map { PdfExport.Entry(it, "") }
                }
            }
        }
        item { NeonButton("قسم جديد", Modifier.fillMaxWidth(), Icons.Rounded.CreateNewFolder) { creating = true } }
        items(g.sections, key = { it.id }) { s ->
            val notes = g.notesIn(s.id)
            FolderRow(
                Icons.Rounded.Topic, Accent.Mint, s.name,
                "${notes.size} تسجيل" + (notes.maxOfOrNull { it.created }?.let { " · آخر حاجة ${dayLabel(it)}" } ?: ""),
            ) { onSection(s.id) }
        }
        if (g.notes.isEmpty() && g.sections.isEmpty()) {
            item { EmptyHint("المجموعة فاضية", "اعمل أقسام (مثلاً الفصل الأول)، وبعد ما تستخرج الكلام دوس \"حفظ في مجموعة\"") }
        }
        if (loose.isNotEmpty()) {
            item { SectionTitle("من غير قسم", subtitle = "${loose.size} تسجيل", modifier = Modifier.padding(top = 6.dp)) }
            notesByDay(loose, onOpen)
        }
    }
    if (creating) {
        NameDialog("قسم جديد في \"${g.name}\"", "اسم القسم (مثلاً الفصل الأول)", "", onDismiss = { creating = false }) {
            creating = false
            lib.createSection(g.id, it)?.let { s -> onSection(s.id) }
        }
    }
    if (renaming) {
        NameDialog("تغيير اسم المجموعة", "اسم المجموعة", g.name, onDismiss = { renaming = false }) {
            renaming = false
            lib.renameGroup(g.id, it)
        }
    }
    if (deleting) {
        ConfirmDialog("مسح \"${g.name}\"؟", "هيتمسح معاها ${g.sections.size} قسم و${g.notes.size} تسجيل ومش هيرجعوا", onDismiss = { deleting = false }) {
            deleting = false
            lib.deleteGroup(g.id)
            onBack()
        }
    }
}

@Composable
fun SectionPage(lib: Library, groupId: String, sectionId: String, onBack: () -> Unit, onOpen: (Note) -> Unit) {
    val g = lib.group(groupId) ?: return GoBack(onBack)
    val s = g.section(sectionId) ?: return GoBack(onBack)
    val notes = g.notesIn(sectionId)
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    PageScaffold(
        s.name, "${g.name} · ${notes.size} تسجيل", onBack,
        actions = {
            IconOrb(Icons.Rounded.Edit, onClick = { renaming = true })
            Spacer(Modifier.width(8.dp))
            IconOrb(Icons.Rounded.Delete, onClick = { deleting = true })
        },
    ) {
        if (notes.isEmpty()) {
            item { EmptyHint("القسم فاضي", "بعد ما تستخرج الكلام دوس \"حفظ في مجموعة\" واختار \"${g.name}\" ثم \"${s.name}\"") }
        } else {
            item { PdfButtons("${g.name} - ${s.name}", "${g.name} · ${s.name}") { notes.sortedBy { it.created }.map { PdfExport.Entry(it, s.name) } } }
        }
        notesByDay(notes, onOpen)
    }
    if (renaming) {
        NameDialog("تغيير اسم القسم", "اسم القسم", s.name, onDismiss = { renaming = false }) {
            renaming = false
            lib.renameSection(g.id, s.id, it)
        }
    }
    if (deleting) {
        ConfirmDialog("مسح \"${s.name}\"؟", "هيتمسح معاه ${notes.size} تسجيل ومش هيرجعوا", onDismiss = { deleting = false }) {
            deleting = false
            lib.deleteSection(g.id, s.id)
            onBack()
        }
    }
}

@Composable
fun NotePage(lib: Library, groupId: String, noteId: String, onBack: () -> Unit, onMoved: (Note) -> Unit) {
    val n = lib.note(groupId, noteId) ?: return GoBack(onBack)
    val ctx = LocalContext.current
    var text by remember(noteId) { mutableStateOf(n.text) }
    var summary by remember(noteId) { mutableStateOf(n.summary) }
    var renaming by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val g = lib.group(groupId)
    val where = listOfNotNull(g?.name, g?.section(n.sectionId)?.name).joinToString(" › ")
    val save = { lib.updateNote(groupId, noteId, text, summary) }
    PageScaffold(
        n.title, "$where · ${dayLabel(n.created)} ${time(n.created)}",
        onBack = {
            save()
            onBack()
        },
        actions = { IconOrb(Icons.Rounded.Edit, onClick = { renaming = true }) },
    ) {
        item {
            Column(
                Modifier.fillMaxWidth().glass(NoteShape, accent = true, alpha = 0.45f).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionTitle("النص", subtitle = n.source.ifBlank { null })
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Editor(text, { text = it }, ltr = true, minHeight = 180)
                }
                SectionTitle("الملخص والمختصرات", subtitle = "اكتب اللي عاوز تذاكره من التسجيل ده")
                Editor(summary, { summary = it }, ltr = false, minHeight = 120, placeholder = "مثلاً: أهم النقط، الكلمات الجديدة، أسئلة…")
                if (text != n.text || summary != n.summary) {
                    NeonButton("حفظ التعديل", Modifier.fillMaxWidth(), Icons.Rounded.Save) { save() }
                }
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().glass(NoteShape, alpha = 0.45f).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PdfButtons(n.title, n.title) {
                    save()
                    listOf(PdfExport.Entry(n.copy(text = text, summary = summary), g?.section(n.sectionId)?.name.orEmpty()))
                }
                val both = if (summary.isBlank()) text else "$text\n\n— الملخص —\n$summary"
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("نسخ", Modifier.weight(1f), Icons.Rounded.ContentCopy) { copy(ctx, both) }
                    GhostButton("مشاركة", Modifier.weight(1f), Icons.Rounded.Share) { share(ctx, both) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("نقل", Modifier.weight(1f), Icons.Rounded.FolderOpen) { moving = true }
                    GhostButton("مسح", Modifier.weight(1f), Icons.Rounded.Delete) { deleting = true }
                }
            }
        }
    }
    if (renaming) {
        NameDialog("تغيير الاسم", "اسم التسجيل", n.title, onDismiss = { renaming = false }) {
            renaming = false
            lib.renameNote(groupId, noteId, it)
        }
    }
    if (moving) {
        SaveToGroupDialog(lib, null, onDismiss = { moving = false }, heading = "نقل \"${n.title}\" لـ") { target, sectionId, _ ->
            moving = false
            save()
            lib.moveNote(groupId, noteId, target.id, sectionId)?.let(onMoved)
        }
    }
    if (deleting) {
        ConfirmDialog("مسح \"${n.title}\"؟", "النص والملخص هيتمسحوا ومش هيرجعوا", onDismiss = { deleting = false }) {
            deleting = false
            lib.deleteNote(groupId, noteId)
            onBack()
        }
    }
}

/**
 * Picks where something goes: a group (or a new one), then a section in it (or none, or a new
 * one). With [defaultTitle] it also asks for the recording's name; null hides that field.
 */
@Composable
fun SaveToGroupDialog(
    lib: Library,
    defaultTitle: String?,
    onDismiss: () -> Unit,
    heading: String = "حفظ في مجموعة",
    onSave: (group: Group, sectionId: String, title: String) -> Unit,
) {
    var title by remember { mutableStateOf(defaultTitle.orEmpty()) }
    var groupId by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    val group = groupId?.let { lib.group(it) }
    DialogCard(heading, onDismiss) {
        if (defaultTitle != null) Field(title, { title = it }, "اسم التسجيل")
        if (group == null) {
            Hint(if (lib.groups.isEmpty()) "اعمل أول مجموعة" else "اختار المجموعة")
            ChoiceList(lib.groups.map { Choice(it.id, it.name, "${it.sections.size} قسم · ${it.notes.size}", Icons.Rounded.Folder, Accent.Amber) }) {
                groupId = it
                newName = ""
            }
            Field(newName, { newName = it }, "أو اسم مجموعة جديدة")
            NeonButton("إنشاء المجموعة", Modifier.fillMaxWidth(), Icons.Rounded.CreateNewFolder, enabled = newName.isNotBlank()) {
                groupId = lib.createGroup(newName).id
                newName = ""
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Folder, null, tint = Accent.Amber)
                Spacer(Modifier.width(8.dp))
                Text(group.name, Modifier.weight(1f), color = Ink, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Chip("غيّر", selected = false) { groupId = null }
            }
            Hint("اختار القسم")
            val choices = listOf(Choice("", "من غير قسم", "${group.notesIn("").size}", Icons.Rounded.Inbox, Ink.copy(alpha = 0.6f))) +
                group.sections.map { Choice(it.id, it.name, "${group.notesIn(it.id).size}", Icons.Rounded.Topic, Accent.Mint) }
            ChoiceList(choices) { onSave(group, it, title) }
            Field(newName, { newName = it }, "أو اسم قسم جديد")
            NeonButton("إنشاء القسم وحفظ", Modifier.fillMaxWidth(), Icons.Rounded.CreateNewFolder, enabled = newName.isNotBlank()) {
                lib.createSection(group.id, newName)?.let { onSave(group, it.id, title) }
            }
        }
    }
}

/** Save to a file the user picks, or share straight to WhatsApp / Drive / email. */
@Composable
private fun PdfButtons(fileName: String, docTitle: String, entries: () -> List<PdfExport.Entry>) {
    val ctx = LocalContext.current
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            val ok = runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { PdfExport.write(it, docTitle, entries()) } }.isSuccess
            Toast.makeText(ctx, if (ok) "اتحفظ PDF" else "مش قادر أحفظ الـ PDF", Toast.LENGTH_SHORT).show()
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NeonButton("حفظ PDF", Modifier.weight(1f), Icons.Rounded.PictureAsPdf) { saver.launch(PdfExport.safeName(fileName)) }
        GhostButton("مشاركة PDF", Modifier.weight(1f), Icons.Rounded.Share) {
            runCatching { PdfExport.share(ctx, fileName, docTitle, entries()) }
                .onFailure { Toast.makeText(ctx, "مش قادر أعمل الـ PDF", Toast.LENGTH_SHORT).show() }
        }
    }
}

/** One section per day, newest day first. */
private fun LazyListScope.notesByDay(notes: List<Note>, onOpen: (Note) -> Unit) {
    notes.groupBy { dayKey(it.created) }.forEach { (key, dayNotes) ->
        item(key = "day-$key") {
            SectionTitle(dayLabel(dayNotes.first().created), subtitle = fullDate(dayNotes.first().created), modifier = Modifier.padding(top = 6.dp))
        }
        items(dayNotes, key = { it.id }) { n -> NoteRow(n) { onOpen(n) } }
    }
}

@Composable
private fun NoteRow(n: Note, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(NoteShape).glass(NoteShape).clickable(onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(n.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(time(n.created), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Text(
            n.text.ifBlank { "(فاضي)" },
            style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
            color = Ink.copy(alpha = 0.65f), maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        if (n.summary.isNotBlank()) {
            Text(
                "📝 ${n.summary}", style = MaterialTheme.typography.bodySmall,
                color = Accent.Amber.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FolderRow(icon: ImageVector, color: Color, title: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(NoteShape).glass(NoteShape).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlowIcon(icon, color, size = 46)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.labelMedium, color = Ink.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Editor(value: String, onChange: (String) -> Unit, ltr: Boolean, minHeight: Int, placeholder: String = "") {
    val shape = RoundedCornerShape(18.dp)
    Box(Modifier.fillMaxWidth().heightIn(min = minHeight.dp).clip(shape).glass(shape, alpha = 0.25f).padding(14.dp)) {
        if (value.isEmpty() && placeholder.isNotEmpty()) {
            Text(placeholder, color = Ink.copy(alpha = 0.35f), fontSize = 15.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = TextStyle(
                color = Ink, fontSize = 17.sp, lineHeight = 27.sp,
                textDirection = if (ltr) TextDirection.Ltr else TextDirection.Content,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private data class Choice(val id: String, val name: String, val count: String, val icon: ImageVector, val tint: Color)

@Composable
private fun ChoiceList(choices: List<Choice>, onPick: (String) -> Unit) {
    if (choices.isEmpty()) return
    val shape = RoundedCornerShape(18.dp)
    Column(Modifier.heightIn(max = 260.dp)) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(choices, key = { "c-" + it.id }) { c ->
                Row(
                    Modifier.fillMaxWidth().clip(shape).glass(shape, alpha = 0.35f).clickable { onPick(c.id) }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(c.icon, null, tint = c.tint)
                    Spacer(Modifier.width(10.dp))
                    Text(c.name, Modifier.weight(1f), color = Ink, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(c.count, style = MaterialTheme.typography.labelMedium, color = Ink.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = Ink.copy(alpha = 0.7f))
}

@Composable
private fun NameDialog(title: String, label: String, initial: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    DialogCard(title, onDismiss) {
        Field(name, { name = it }, label)
        NeonButton("تمام", Modifier.fillMaxWidth(), Icons.Rounded.Save, enabled = name.isNotBlank()) { onDone(name) }
    }
}

@Composable
private fun ConfirmDialog(title: String, detail: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    DialogCard(title, onDismiss) {
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = Ink.copy(alpha = 0.75f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("إلغاء", Modifier.weight(1f), Icons.Rounded.Close, onClick = onDismiss)
            NeonButton("مسح", Modifier.weight(1f), Icons.Rounded.Delete, onClick = onConfirm)
        }
    }
}

@Composable
private fun DialogCard(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().glass(NoteShape, accent = true, alpha = 0.92f).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Ink)
            content()
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        label = { Text(label) }, modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun EmptyHint(title: String, detail: String) {
    Column(
        Modifier.fillMaxWidth().glass(NoteShape, alpha = 0.35f).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, fontWeight = FontWeight.Bold, color = Ink.copy(alpha = 0.85f))
        Text(detail, style = MaterialTheme.typography.labelMedium, color = Ink.copy(alpha = 0.55f))
    }
}

@Composable
private fun PageScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    GlassBackdrop {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                title = title, subtitle = subtitle,
                leading = { IconOrb(Icons.AutoMirrored.Rounded.ArrowBack, onClick = onBack) },
                actions = actions,
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                // Room for the floating bottom bar.
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

/** The page's item was deleted or moved away: leave it (outside composition). */
@Composable
private fun GoBack(onBack: () -> Unit) {
    LaunchedEffect(Unit) { onBack() }
}

private fun dayKey(t: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(t))

private fun fullDate(t: Long): String = SimpleDateFormat("EEEE d MMMM yyyy", Arabic).format(Date(t))

private fun time(t: Long): String = SimpleDateFormat("h:mm a", Arabic).format(Date(t))

/** "النهارده" / "امبارح" for recent days, otherwise the date. */
private fun dayLabel(t: Long): String {
    val day = Calendar.getInstance().apply { timeInMillis = t }
    val today = Calendar.getInstance()
    val sameDay = { a: Calendar, b: Calendar -> a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR) }
    if (sameDay(day, today)) return "النهارده"
    today.add(Calendar.DAY_OF_YEAR, -1)
    if (sameDay(day, today)) return "امبارح"
    return SimpleDateFormat("d MMMM yyyy", Arabic).format(Date(t))
}
