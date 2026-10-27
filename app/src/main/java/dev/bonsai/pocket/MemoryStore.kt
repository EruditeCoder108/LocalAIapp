package dev.bonsai.pocket

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class MemoryFact(val id: Long, val text: String, val pinned: Boolean)

class MemoryStore(context: Context, name: String = "memory.db") : SQLiteOpenHelper(context, name, null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE facts (id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL UNIQUE, pinned INTEGER NOT NULL DEFAULT 0)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun all(): List<MemoryFact> = readableDatabase.query("facts", arrayOf("id", "text", "pinned"), null, null, null, null, "pinned DESC, id DESC").use { rows ->
        buildList { while (rows.moveToNext()) add(MemoryFact(rows.getLong(0), rows.getString(1), rows.getInt(2) == 1)) }
    }
    fun add(text: String, pinned: Boolean) {
        val clean = text.trim()
        require(clean.length in 1..500) { "Keep each memory between 1 and 500 characters." }
        val existing = all()
        check(existing.size < 100 || existing.any { it.text == clean }) { "Memory is full. Remove an old fact first." }
        check(!pinned || existing.count { it.pinned && it.text != clean } < 3) { "You can pin up to three memories." }
        val values = ContentValues().apply { put("text", clean); put("pinned", if (pinned) 1 else 0) }
        writableDatabase.insertWithOnConflict("facts", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }
    fun remove(id: Long) { writableDatabase.delete("facts", "id=?", arrayOf(id.toString())) }
    fun pin(id: Long, pinned: Boolean) {
        check(!pinned || all().count { it.pinned && it.id != id } < 3) { "You can pin up to three memories." }
        writableDatabase.update("facts", ContentValues().apply { put("pinned", if (pinned) 1 else 0) }, "id=?", arrayOf(id.toString()))
    }
    companion object {
        private val words = Regex("[\\p{L}\\p{N}]{2,}")
        private val common = setOf("the","and","that","this","with","have","what","which","where","when","how","does","can","could","would","should","please","tell","about","you","your","are","was","were","for","from","into","is","it","my","me","to","of","in","on","an","as","be","do")
        fun relevant(facts: List<MemoryFact>, query: String): List<MemoryFact> {
            val tokens = words.findAll(query.lowercase()).map { it.value }.filterNot {it in common}.toSet()
            return (facts.filter { it.pinned }.take(3) + facts.filterNot { it.pinned }
                .map { fact -> fact to words.findAll(fact.text.lowercase()).map { it.value }.toSet().intersect(tokens).size }
                .filter { it.second > 0 }.sortedByDescending { it.second }.take(3).map { it.first }).distinctBy { it.id }
        }
        fun rememberCommand(text: String): String? = Regex("(?is)^remember(?:\\s+that\\s+|\\s+this\\s*:\\s*|\\s*:\\s*)(.+)$")
            .matchEntire(text.trim())?.groupValues?.get(1)?.trim()
    }
}
