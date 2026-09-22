package dev.meghsohor.iptvtv.data.remote

/**
 * Minimal RFC4180 CSV parser (quoted fields, embedded commas/newlines, "" escaped quotes).
 * iptv-org's CSVs are simple enough that a dependency for this would be overkill.
 */
internal fun parseCsv(text: String): List<Map<String, String>> {
  val rows = mutableListOf<List<String>>()
  val field = StringBuilder()
  val row = mutableListOf<String>()
  var inQuotes = false
  var i = 0
  fun endField() {
    row.add(field.toString())
    field.clear()
  }
  fun endRow() {
    endField()
    rows.add(row.toList())
    row.clear()
  }
  while (i < text.length) {
    val c = text[i]
    when {
      inQuotes -> {
        if (c == '"') {
          if (i + 1 < text.length && text[i + 1] == '"') {
            field.append('"')
            i++
          } else {
            inQuotes = false
          }
        } else {
          field.append(c)
        }
      }
      c == '"' -> inQuotes = true
      c == ',' -> endField()
      c == '\r' -> {} // skip, \n (or EOF) ends the row
      c == '\n' -> endRow()
      else -> field.append(c)
    }
    i++
  }
  if (field.isNotEmpty() || row.isNotEmpty()) endRow()

  if (rows.isEmpty()) return emptyList()
  val header = rows.first()
  return rows.drop(1).filter { it.isNotEmpty() && it.any { cell -> cell.isNotEmpty() } }.map { cells ->
    header.indices.associate { idx -> header[idx] to cells.getOrElse(idx) { "" } }
  }
}
