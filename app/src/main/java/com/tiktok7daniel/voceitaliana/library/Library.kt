package com.tiktok7daniel.voceitaliana.library

import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Note(
    val id: String,
    val groupId: String,
    /** "" when the note sits directly in the group, outside any section. */
    val sectionId: String,
    val title: String,
    val text: String,
    /** The user's own summary / study notes for this recording. */
    val summary: String,
    val created: Long,
    val source: String,
)

data class Section(val id: String, val name: String, val created: Long)

data class Group(val id: String, val name: String, val created: Long, val sections: List<Section>, val notes: List<Note>) {
    val lastActivity get() = notes.maxOfOrNull { it.created } ?: created

    fun section(id: String) = sections.firstOrNull { it.id == id }

    fun notesIn(sectionId: String) = notes.filter { it.sectionId == sectionId }
}

/**
 * The user's saved transcripts: groups (e.g. a subject) hold sections (e.g. a chapter) and
 * notes. Everything stays on the phone: one folder per group (`group.json` with its sections,
 * plus one JSON file per note) under [root].
 */
class Library(private val root: File) {
    /** Newest activity first; observed by the UI. */
    val groups = mutableStateListOf<Group>()

    init {
        reload()
    }

    fun group(id: String) = groups.firstOrNull { it.id == id }

    fun note(groupId: String, noteId: String) = group(groupId)?.notes?.firstOrNull { it.id == noteId }

    fun createGroup(name: String): Group {
        val g = Group(newId(), name.trim().ifBlank { "مجموعة جديدة" }, System.currentTimeMillis(), emptyList(), emptyList())
        writeGroup(g)
        reload()
        return g
    }

    fun renameGroup(id: String, name: String) {
        val g = group(id) ?: return
        if (name.isBlank()) return
        writeGroup(g.copy(name = name.trim()))
        reload()
    }

    fun deleteGroup(id: String) {
        File(root, id).deleteRecursively()
        reload()
    }

    fun createSection(groupId: String, name: String): Section? {
        val g = group(groupId) ?: return null
        val s = Section(newId(), name.trim().ifBlank { "قسم جديد" }, System.currentTimeMillis())
        writeGroup(g.copy(sections = g.sections + s))
        reload()
        return s
    }

    fun renameSection(groupId: String, sectionId: String, name: String) {
        val g = group(groupId) ?: return
        if (name.isBlank()) return
        writeGroup(g.copy(sections = g.sections.map { if (it.id == sectionId) it.copy(name = name.trim()) else it }))
        reload()
    }

    /** Deletes the section together with the notes in it. */
    fun deleteSection(groupId: String, sectionId: String) {
        val g = group(groupId) ?: return
        g.notesIn(sectionId).forEach { File(File(root, groupId), "${it.id}.json").delete() }
        writeGroup(g.copy(sections = g.sections.filter { it.id != sectionId }))
        reload()
    }

    fun addNote(groupId: String, sectionId: String, title: String, text: String, source: String): Note? {
        if (group(groupId) == null) return null
        val n = Note(newId(), groupId, sectionId, title.trim().ifBlank { "تسجيل" }, text, "", System.currentTimeMillis(), source)
        writeNote(n)
        reload()
        return n
    }

    fun renameNote(groupId: String, noteId: String, title: String) {
        val n = note(groupId, noteId) ?: return
        if (title.isBlank()) return
        writeNote(n.copy(title = title.trim()))
        reload()
    }

    fun updateNote(groupId: String, noteId: String, text: String, summary: String) {
        val n = note(groupId, noteId) ?: return
        if (n.text == text && n.summary == summary) return
        writeNote(n.copy(text = text, summary = summary))
        reload()
    }

    fun moveNote(groupId: String, noteId: String, toGroupId: String, toSectionId: String): Note? {
        val n = note(groupId, noteId) ?: return null
        if (group(toGroupId) == null) return n
        val moved = n.copy(groupId = toGroupId, sectionId = toSectionId)
        writeNote(moved)
        if (groupId != toGroupId) File(File(root, groupId), "$noteId.json").delete()
        reload()
        return moved
    }

    fun deleteNote(groupId: String, noteId: String) {
        File(File(root, groupId), "$noteId.json").delete()
        reload()
    }

    private fun reload() {
        val loaded = root.listFiles { f -> f.isDirectory }.orEmpty().mapNotNull { dir ->
            runCatching {
                val g = JSONObject(File(dir, GROUP_FILE).readText())
                val sections = g.optJSONArray("sections")?.let { a ->
                    (0 until a.length()).map { i ->
                        val o = a.getJSONObject(i)
                        Section(o.getString("id"), o.optString("name"), o.optLong("created"))
                    }
                }.orEmpty()
                val known = sections.map { it.id }.toSet()
                val notes = dir.listFiles { f -> f.name.endsWith(".json") && f.name != GROUP_FILE }.orEmpty().mapNotNull { f ->
                    runCatching {
                        val o = JSONObject(f.readText())
                        Note(
                            id = f.name.removeSuffix(".json"),
                            groupId = dir.name,
                            // A note whose section was removed elsewhere falls back to the group itself.
                            sectionId = o.optString("section").takeIf { it in known }.orEmpty(),
                            title = o.optString("title"),
                            text = o.optString("text"),
                            summary = o.optString("summary"),
                            created = o.optLong("created"),
                            source = o.optString("source"),
                        )
                    }.getOrNull()
                }.sortedByDescending { it.created }
                Group(dir.name, g.optString("name"), g.optLong("created"), sections.sortedBy { it.created }, notes)
            }.getOrNull()
        }.sortedByDescending { it.lastActivity }
        groups.clear()
        groups.addAll(loaded)
    }

    private fun writeGroup(g: Group) {
        val dir = File(root, g.id).apply { mkdirs() }
        val sections = JSONArray()
        g.sections.forEach { sections.put(JSONObject().put("id", it.id).put("name", it.name).put("created", it.created)) }
        atomicWrite(File(dir, GROUP_FILE), JSONObject().put("name", g.name).put("created", g.created).put("sections", sections).toString())
    }

    private fun writeNote(n: Note) {
        val dir = File(root, n.groupId).apply { mkdirs() }
        val o = JSONObject()
            .put("title", n.title).put("text", n.text).put("summary", n.summary)
            .put("section", n.sectionId).put("created", n.created).put("source", n.source)
        atomicWrite(File(dir, "${n.id}.json"), o.toString())
    }

    /** Writes to a temp file first so a crash mid-write never corrupts a saved transcript. */
    private fun atomicWrite(target: File, content: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(target)) {
            target.writeText(content)
            tmp.delete()
        }
    }

    private fun newId() = UUID.randomUUID().toString().replace("-", "").take(12)

    companion object {
        private const val GROUP_FILE = "group.json"
    }
}
