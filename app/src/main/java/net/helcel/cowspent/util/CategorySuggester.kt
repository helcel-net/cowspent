package net.helcel.cowspent.util

import net.helcel.cowspent.model.DBBill

/**
 * Guesses a bill's category from the names of bills that already have one.
 *
 * The bill-labelling screen shows the top guesses and lets the user pick; the bill editor can
 * apply one by itself, so it asks [confidentMatch] instead, which only answers when the history
 * clearly agrees.
 */
object CategorySuggester {

    /** Below this length a name matches too many others by containment ("a", "bus", ...). */
    private const val MIN_CONTAINMENT_LENGTH = 4

    /** Share of the matching bills the leading category needs before it is applied unasked. */
    private const val CONFIDENT_SHARE = 0.75

    /** Matches by containment are looser, so one of them alone is not enough. */
    private const val MIN_LOOSE_MATCHES = 2

    private fun normalize(name: String) = name.lowercase().trim()

    private fun isExactMatch(name: String, other: String) = other == name

    private fun isLooseMatch(name: String, other: String) =
        (name.length >= MIN_CONTAINMENT_LENGTH && other.contains(name)) ||
                (other.length >= MIN_CONTAINMENT_LENGTH && name.contains(other))

    fun rank(what: String, categorizedBills: List<DBBill>): List<Pair<Long, Int>> {
        val name = normalize(what)
        if (name.isEmpty()) return emptyList()
        return categorizedBills
            .filter { val other = normalize(it.what); isExactMatch(name, other) || isLooseMatch(name, other) }
            .groupingBy { it.categoryId }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
    }

    fun confidentMatch(
        what: String,
        categorizedBills: List<DBBill>,
        allowedCategoryIds: Set<Long>
    ): Long? {
        val name = normalize(what)
        if (name.isEmpty()) return null
        val candidates = categorizedBills.filter { it.categoryId in allowedCategoryIds }

        val exact = candidates.filter { isExactMatch(name, normalize(it.what)) }
        if (exact.isNotEmpty()) return dominant(exact, minCount = 1)

        val loose = candidates.filter { isLooseMatch(name, normalize(it.what)) }
        return dominant(loose, minCount = MIN_LOOSE_MATCHES)
    }

    private fun dominant(bills: List<DBBill>, minCount: Int): Long? {
        if (bills.size < minCount) return null
        val (categoryId, count) = bills.groupingBy { it.categoryId }.eachCount().maxByOrNull { it.value }
            ?: return null
        return if (count >= minCount && count >= bills.size * CONFIDENT_SHARE) categoryId else null
    }
}
