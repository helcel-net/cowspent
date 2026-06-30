package net.helcel.cowspent.model

import java.io.Serializable

class DBAccountProject(
    var id: Long,
    var remoteId: String,
    var password: String?,
    var name: String,
    var ncUrl: String,
    var archivedTs: Long? = null
) : Serializable {

    override fun toString(): String {
        return "#DBAccountProject$id/$remoteId,$name, $ncUrl, [SECURE], archivedTs=$archivedTs"
    }
}
