package com.tiktok7daniel.voceitaliana.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val NoteShape = RoundedCornerShape(26.dp)
private val Arabic = Locale("ar")

/** Which page of the app is showing. Kept tiny on purpose: no navigation library needed. */
sealed interface Page {
    data object Home : Page
    data object Groups : Page
    data class OneGroup(val groupId: String) : Page
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
            item { EmptyHint("لسه مفيش مجموعات", "اعمل مجموعة، وبعد ما يطلع النص دوس \"حفظ في مجموعة\"") }
        }
        items(lib.groups, key = { it.id }) { g ->
            Row(
                Modifier.fillMaxWidth().clip(NoteShape).glass(NoteShape).clickable { onOpen(g) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlowIcon(Icons.Rounded.Folder, Accent.Amber, size = 46)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(g.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${g.notes.size} تسجيل · آخر حاجة ${dayLabel(g.lastActivity)}",
                        style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
    if (creating) {
        NameDialog("مجموعة جديدة", "اسم المجموعة", "", onDismiss = { creating = false }) {
            creating = false
            onOpen(lib.createGroup(it))
        }
    }
}

@Composable
fun GroupPage(lib: Library, groupId: String, onBack: () -> Unit, onOpen: (Note) -> Unit) {
    val g = lib.group(groupId) ?: return GoBack(onBack)
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    PageScaffold(
        g.name, "${g.notes.size} تسجيل", onBack,
        actions = {
            IconOrb(Icons.Rounded.Edit, onClick = { renaming = true })
            Spacer(Modifier.width(8.dp))
            IconOrb(Icons.Rounded.Delete, onClick = { deleting = true })
        },
    ) {
        if (g.notes.isEmpty()) {
            item { EmptyHint("المجموعة فاضية", "بعد ما تستخرج الكلام من فويس، دوس \"حفظ في مجموعة\" واختار \"${g.name}\"") }
        }
        // One section per day, newest day first.
        g.notes.groupBy { dayKey(it.created) }.forEach { (_, dayNotes) ->
            item(key = "day-${dayKey(dayNotes.first().created)}") {
                SectionTitle(dayLabel(dayNotes.first().created), subtitle = fullDate(dayNotes.first().created), modifier = Modifier.padding(top = 6.dp))
            }
            items(dayNotes, key = { it.id }) { n ->
                Column(
                    Modifier.fillMaxWidth().clip(NoteShape).glass(NoteShape).clickable { onOpen(n) }.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(n.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(time(n.created), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        n.text.ifBlank { "(فاضي)" },
                        style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                        color = Color.White.copy(alpha = 0.65f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    if (renaming) {
        NameDialog("تغيير اسم المجموعة", "اسم المجموعة", g.name, onDismiss = { renaming = false }) {
            renaming = false
            lib.renameGroup(g.id, it)
        }
    }
    if (deleting) {
        ConfirmDialog("مسح \"${g.name}\"؟", "هيتمسح معاها ${g.notes.size} تسجيل ومش هترجع", onDismiss = { deleting = false }) {
            deleting = false
            lib.deleteGroup(g.id)
            onBack()
        }
    }
}

@Composable
fun NotePage(lib: Library, groupId: String, noteId: String, onBack: () -> Unit, onMoved: (Note) -> Unit) {
    val n = lib.note(groupId, noteId) ?: return GoBack(onBack)
    val ctx = LocalContext.current
    var text by remember(noteId) { mutableStateOf(n.text) }
    var renaming by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val groupName = lib.group(groupId)?.name.orEmpty()
    PageScaffold(
        n.title, "$groupName · ${fullDate(n.created)} ${time(n.created)}",
        onBack = {
            lib.updateText(groupId, noteId, text)
            onBack()
        },
        actions = { IconOrb(Icons.Rounded.Edit, onClick = { renaming = true }) },
    ) {
        item {
            Column(
                Modifier.fillMaxWidth().glass(NoteShape, accent = true, alpha = 0.45f).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (n.source.isNotBlank()) {
                    Text("من: ${n.source}", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 17.sp, lineHeight = 27.sp, textDirection = TextDirection.Ltr),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp)
                            .clip(RoundedCornerShape(18.dp)).glass(RoundedCornerShape(18.dp), alpha = 0.25f).padding(14.dp),
                    )
                }
                if (text != n.text) {
                    NeonButton("حفظ التعديل", Modifier.fillMaxWidth(), Icons.Rounded.Save) { lib.updateText(groupId, noteId, text) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("نسخ", Modifier.weight(1f), Icons.Rounded.ContentCopy) { copy(ctx, text) }
                    GhostButton("مشاركة", Modifier.weight(1f), Icons.Rounded.Share) { share(ctx, text) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("نقل لمجموعة", Modifier.weight(1f), Icons.Rounded.FolderOpen) { moving = true }
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
        GroupPickerDialog(lib, "نقل لمجموعة", exclude = groupId, onDismiss = { moving = false }) { target ->
            moving = false
            lib.updateText(groupId, noteId, text)
            lib.moveNote(groupId, noteId, target.id)?.let(onMoved)
        }
    }
    if (deleting) {
        ConfirmDialog("مسح \"${n.title}\"؟", "النص هيتمسح ومش هيرجع", onDismiss = { deleting = false }) {
            deleting = false
            lib.deleteNote(groupId, noteId)
            onBack()
        }
    }
}

/** "Save to group" sheet shown under a fresh transcript: name it, then pick or create a group. */
@Composable
fun SaveToGroupDialog(lib: Library, defaultTitle: String, onDismiss: () -> Unit, onSave: (Group, String) -> Unit) {
    var title by remember { mutableStateOf(defaultTitle) }
    var newGroup by remember { mutableStateOf("") }
    DialogCard("حفظ في مجموعة", onDismiss) {
        Field(title, { title = it }, "اسم التسجيل")
        if (lib.groups.isNotEmpty()) {
            Text("اختار المجموعة", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.7f))
            Column(Modifier.heightIn(max = 240.dp)) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(lib.groups, key = { it.id }) { g -> GroupChoice(g) { onSave(g, title) } }
                }
            }
        }
        Text(if (lib.groups.isEmpty()) "اعمل أول مجموعة" else "أو مجموعة جديدة", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.7f))
        Field(newGroup, { newGroup = it }, "اسم المجموعة الجديدة")
        NeonButton("إنشاء وحفظ", Modifier.fillMaxWidth(), Icons.Rounded.CreateNewFolder, enabled = newGroup.isNotBlank()) {
            onSave(lib.createGroup(newGroup), title)
        }
    }
}

@Composable
private fun GroupPickerDialog(lib: Library, title: String, exclude: String, onDismiss: () -> Unit, onPick: (Group) -> Unit) {
    var newGroup by remember { mutableStateOf("") }
    DialogCard(title, onDismiss) {
        val others = lib.groups.filter { it.id != exclude }
        if (others.isNotEmpty()) {
            Column(Modifier.heightIn(max = 280.dp)) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(others, key = { it.id }) { g -> GroupChoice(g) { onPick(g) } }
                }
            }
        }
        Field(newGroup, { newGroup = it }, "أو اسم مجموعة جديدة")
        NeonButton("إنشاء ونقل", Modifier.fillMaxWidth(), Icons.Rounded.CreateNewFolder, enabled = newGroup.isNotBlank()) {
            onPick(lib.createGroup(newGroup))
        }
    }
}

@Composable
private fun GroupChoice(g: Group, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).glass(shape, alpha = 0.35f).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(Icons.Rounded.Folder, null, tint = Accent.Amber)
        Spacer(Modifier.width(10.dp))
        Text(g.name, Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${g.notes.size}", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.5f))
    }
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
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f))
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
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)
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
        Text(title, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f))
        Text(detail, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.55f))
    }
}

@Composable
private fun PageScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

/** The page's item was deleted or moved away: leave it (outside composition). */
@Composable
private fun GoBack(onBack: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }
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
