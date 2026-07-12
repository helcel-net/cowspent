package net.helcel.cowspent.model

import java.io.Serializable

class DBCategory(
    var id: Long,
    var remoteId: Long,
    var projectId: Long,
    var name: String?,
    var icon: String,
    var color: String,
    var state: Int = DBBill.STATE_OK
) : Serializable {

    override fun toString(): String {
        return "#DBCategory$id/$remoteId,$name"
    }
}
