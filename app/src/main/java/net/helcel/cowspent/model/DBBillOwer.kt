package net.helcel.cowspent.model

import java.io.Serializable

class DBBillOwer(
    var id: Long,
    var billId: Long,
    var memberId: Long
) : Serializable {

    override fun toString(): String {
        return "#DBBillOwer$id/$billId,$memberId"
    }
}
