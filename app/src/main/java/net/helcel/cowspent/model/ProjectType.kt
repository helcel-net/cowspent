package net.helcel.cowspent.model

enum class ProjectType(val id: String) {
    LOCAL("l"), COSPEND("c"), IHATEMONEY("i");

    companion object {
        private val reverseMap: Map<String, ProjectType> = HashMap()

        init {
            for (type in entries) {
                (reverseMap as MutableMap)[type.id] = type
            }
        }

        @JvmStatic
        fun getTypeById(id: String?): ProjectType? {
            return reverseMap[id]
        }
    }
}
