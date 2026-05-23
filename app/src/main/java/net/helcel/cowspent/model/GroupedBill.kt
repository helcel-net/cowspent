package net.helcel.cowspent.model

import java.io.Serializable

class GroupedBill(
    val sourceBills: List<DBBill>
) : DBBill(
    sourceBills.first().id,
    sourceBills.first().remoteId,
    sourceBills.first().projectId,
    sourceBills.first().payerId,
    sourceBills.sumOf { it.amount },
    sourceBills.first().timestamp,
    sourceBills.first().what,
    sourceBills.first().state,
    sourceBills.first().repeat,
    sourceBills.first().paymentMode,
    sourceBills.first().categoryRemoteId,
    sourceBills.first().comment,
    sourceBills.first().paymentModeRemoteId
), Serializable {
    init {
        this.formattedWhat = sourceBills.first().formattedWhat
        this.billOwers = sourceBills.flatMap { it.billOwers }
    }
}
