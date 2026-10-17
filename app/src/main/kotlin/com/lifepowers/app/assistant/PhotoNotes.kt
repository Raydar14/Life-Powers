package com.lifepowers.app.assistant

import com.lifepowers.app.data.personal.AssistantDao
import com.lifepowers.app.data.personal.PhotoNoteEntity
import javax.inject.Inject
import javax.inject.Singleton

/** The assistant's questions about a photo, and the person's answers, one per line. */
data class PhotoNote(val id: Long, val photoId: Long, val atUtcMs: Long, val questions: List<String>, val answers: List<String>)

/** Reads and updates photo notes. Questions are written by the repository when they arrive. */
@Singleton
class PhotoNotes @Inject constructor(private val dao: AssistantDao) {

    suspend fun latest(photoId: Long): PhotoNote? = dao.notesFor(photoId).firstOrNull()?.toNote()

    /** Stores answers against the newest note for the photo, aligned to its questions. */
    suspend fun saveAnswers(photoId: Long, answers: List<String>): Boolean {
        val row = dao.notesFor(photoId).firstOrNull() ?: return false
        dao.updatePhotoNote(row.copy(answers = joinLines(answers)))
        return true
    }

    companion object {
        /** One item per line; line breaks inside an item become spaces so the lines stay aligned. */
        fun joinLines(items: List<String>): String = items.joinToString("\n") { it.replace(Regex("\\s*\\n\\s*"), " ").trim() }

        fun splitLines(text: String): List<String> = if (text.isEmpty()) emptyList() else text.split("\n")

        /** "Q: ... A: ..." lines for each answered question, or an empty string when none is answered. */
        fun qaBlock(questions: List<String>, answers: List<String>): String = questions
            .mapIndexedNotNull { i, q ->
                val a = answers.getOrNull(i)?.replace(Regex("\\s*\\n\\s*"), " ")?.trim().orEmpty()
                if (a.isEmpty()) null else "Q: ${q.trim()}\nA: $a"
            }
            .joinToString("\n")

        /** Appends [block] to the entry text after a blank line. */
        fun appendBlock(text: String, block: String): String = when {
            block.isBlank() -> text
            text.isBlank() -> block
            else -> text.trimEnd() + "\n\n" + block
        }

        internal fun PhotoNoteEntity.toNote() = PhotoNote(
            id = id,
            photoId = photoId,
            atUtcMs = atUtc,
            questions = splitLines(questions),
            answers = splitLines(answers),
        )
    }
}
