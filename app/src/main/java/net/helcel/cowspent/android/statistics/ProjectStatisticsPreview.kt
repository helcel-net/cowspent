package net.helcel.cowspent.android.statistics

import net.helcel.cowspent.model.DBBill
import net.helcel.cowspent.model.DBBillOwer
import net.helcel.cowspent.model.DBMember
import net.helcel.cowspent.model.DBProject
import net.helcel.cowspent.model.ProjectType

object StatisticsMockData {
    val project = DBProject(
        id = 1L,
        remoteId = "vacation",
        password = "",
        name = "Vacation 2024",
        serverUrl = null,
        email = null,
        lastPayerId = 1L,
        type = ProjectType.LOCAL,
        lastSyncedTimestamp = null,
        currencyName = "EUR",
        isDeletionDisabled = false,
        myAccessLevel = 4,
        bearerToken = null,
        archivedTs = null
    )

    val members = listOf(
        DBMember(1L, 0, 1L, "Alice", true, 1.0, 0, 255, 100, 100, null, null),
        DBMember(2L, 0, 1L, "Bob", true, 1.0, 0, 100, 255, 100, null, null),
        DBMember(3L, 0, 1L, "Charlie", true, 1.0, 0, 100, 100, 255, null, null)
    )

    val bills = listOf(
        DBBill(1L, 0, 1L, 1L, 120.5, System.currentTimeMillis() / 1000 - 86400 * 10, "Hotel", 0, null, null, -13, "", -1).apply {
            billOwers = listOf(DBBillOwer(1L, 1L, 1L), DBBillOwer(2L, 1L, 2L), DBBillOwer(3L, 1L, 3L))
        },
        DBBill(2L, 0, 1L, 2L, 50.0, System.currentTimeMillis() / 1000 - 86400 * 8, "Groceries", 0, null, null, -1, "", -2).apply {
            billOwers = listOf(DBBillOwer(4L, 2L, 1L), DBBillOwer(5L, 2L, 2L), DBBillOwer(6L, 2L, 3L))
        },
        DBBill(3L, 0, 1L, 3L, 1.0, System.currentTimeMillis() / 1000 - 86400 * 5, "Gas", 0, null, null, -14, "", -1).apply {
            billOwers = listOf(DBBillOwer(7L, 3L, 1L), DBBillOwer(8L, 3L, 2L), DBBillOwer(9L, 3L, 3L))
        },
        DBBill(4L, 0, 1L, 1L, 30.0, System.currentTimeMillis() / 1000 - 86400 * 2, "Dinner", 0, null, null, -12, "", -2).apply {
            billOwers = listOf(DBBillOwer(10L, 4L, 1L), DBBillOwer(11L, 4L, 2L))
        }
    )
}

