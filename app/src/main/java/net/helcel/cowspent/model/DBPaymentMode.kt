package net.helcel.cowspent.model

import java.io.Serializable

class DBPaymentMode(
    var id: Long,
    var remoteId: Long,
    var projectId: Long,
    var name: String?,
    var icon: String,
    var color: String
) : Serializable {

    override fun toString(): String {
        return "#DBPaymentMode$id/$remoteId,$name"
    }
}
