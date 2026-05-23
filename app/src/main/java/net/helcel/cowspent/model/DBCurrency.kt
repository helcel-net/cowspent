package net.helcel.cowspent.model

import java.io.Serializable

class DBCurrency(
    var id: Long,
    var remoteId: Long,
    var projectId: Long,
    var name: String?,
    var exchangeRate: Double,
    var state: Int
) : Serializable {

    override fun toString(): String {
        return "#DBCurrency$id/$remoteId,$name , state: $state"
    }
}
