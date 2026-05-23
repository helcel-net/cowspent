package net.helcel.cowspent.util
import android.database.sqlite.SQLiteDatabase

fun evalMath(expression: String): Double {
    var result = 0.0
    var db: SQLiteDatabase? = null
    try {
        // Opens a temporary, in-memory system database block
        db = SQLiteDatabase.create(null)
        val cursor = db.rawQuery("SELECT ($expression);", null)
        if (cursor.moveToFirst()) {
            result = cursor.getDouble(0)
        }
        cursor.close()
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        db?.close()
    }
    return result
}