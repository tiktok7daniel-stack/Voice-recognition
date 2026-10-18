package com.tiktok7daniel.voceitaliana.library

import androidx.compose.runtime.mutableStateListOf
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Note(
    val id: String,
    val groupId: String,
    val title: String,
    val text: String,
    val created: Long,
    val source: String,
)

data class Group(val id: String, val name: String, val created: Long, val notes: List<Note>) {
    val lastActivity get() = notes.maxOfOrNull { it.created } ?: created
}

/**
 * The user's saved transcripts, organised in named groups. Everything stays on the phone:
 * one folder per group (`group.json` + one JSON file per note) under [root].
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
        val g = Group(newId(), name.trim().ifBlank { "مجموعة جديدة" }, System.currentTimeMillis(), emptyList())
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

    fun addNote(groupId: String, title: String, text: String, source: String): Note? {
        if (group(groupId) == null) return null
        val n = Note(newId(), groupId, title.trim().ifBlank { "تسجيل" }, text, System.currentTimeMillis(), source)
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

    fun updateText(groupId: String, noteId: String, text: String) {
        val n = note(groupId, noteId) ?: return
        if (n.text == text) return
        writeNote(n.copy(text = text))
        reload()
    }

    fun moveNote(groupId: String, noteId: String, toGroupId: String): Note? {
        val n = note(groupId, noteId) ?: return null
        if (groupId == toGroupId || group(toGroupId) == null) return n
        val moved = n.copy(groupId = toGroupId)
        writeNote(moved)
        File(File(root, groupId), "$noteId.json").delete()
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
                val notes = dir.listFiles { f -> f.name.endsWith(".json") && f.name != GROUP_FILE }.orEmpty().mapNotNull { f ->
                    runCatching {
                        val o = JSONObject(f.readText())
                        Note(
                            id = f.name.removeSuffix(".json"),
                            groupId = dir.name,
                            title = o.optString("title"),
                            text = o.optString("text"),
                            created = o.optLong("created"),
                            source = o.optString("source"),
                        )
                    }.getOrNull()
                }.sortedByDescending { it.created }
                Group(dir.name, g.optString("name"), g.optLong("created"), notes)
            }.getOrNull()
        }.sortedByDescending { it.lastActivity }
        groups.clear()
        groups.addAll(loaded)
    }

    private fun writeGroup(g: Group) {
        val dir = File(root, g.id).apply { mkdirs() }
        atomicWrite(File(dir, GROUP_FILE), JSONObject().put("name", g.name).put("created", g.created).toString())
    }

    private fun writeNote(n: Note) {
        val dir = File(root, n.groupId).apply { mkdirs() }
        val o = JSONObject().put("title", n.title).put("text", n.text).put("created", n.created).put("source", n.source)
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
