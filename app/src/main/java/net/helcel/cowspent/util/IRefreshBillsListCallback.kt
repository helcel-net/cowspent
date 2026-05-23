package net.helcel.cowspent.util

/**
 * Call back into the BillsListActivity and ask it to refresh the list in the UI
 */
interface IRefreshBillsListCallback {
    fun refreshLists(scrollToTop: Boolean)
}
