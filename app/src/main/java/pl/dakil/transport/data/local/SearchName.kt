package pl.dakil.transport.data.local

import java.text.Normalizer

/**
 * The form place names are matched in: lowercase, unaccented, single-spaced.
 *
 * Folding happens on the way *into* the cache (stored as `cached_place.searchName`) as well as
 * on the query, so `"zabkowska"` finds `"Ząbkowska"` and `"muenchen"` is at least reachable
 * from `"München"` via `"munchen"`. Doing it at write time is what keeps the lookup a plain
 * indexed `LIKE` rather than a full scan that folds every row on every keystroke.
 *
 * IMPORTANT: changing this function changes the meaning of every stored `searchName`. Any edit
 * needs a Room migration that recomputes the column, or previously cached places silently stop
 * matching queries they used to match.
 */
fun foldForSearch(text: String): String {
    // NFD splits "ą" into "a" + a combining ogonek, which the mark filter then drops. It does
    // not decompose the Polish "ł" (it is a distinct letter, not an accented l), so that pair —
    // and the German "ß" — are mapped by hand.
    val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
    val builder = StringBuilder(decomposed.length)
    var lastWasSpace = true
    for (character in decomposed) {
        when {
            Character.getType(character) == Character.NON_SPACING_MARK.toInt() -> Unit
            character.isWhitespace() -> if (!lastWasSpace) {
                builder.append(' ')
                lastWasSpace = true
            }
            else -> {
                builder.append(
                    when (character) {
                        'ł' -> "l"
                        'Ł' -> "l"
                        'ß' -> "ss"
                        'ø' -> "o"
                        'Ø' -> "o"
                        'æ' -> "ae"
                        'Æ' -> "ae"
                        'đ' -> "d"
                        'Đ' -> "d"
                        else -> character.lowercaseChar().toString()
                    },
                )
                lastWasSpace = false
            }
        }
    }
    return builder.toString().trim()
}
