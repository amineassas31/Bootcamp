package rondes.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    fun init() {
        Database.connect(
            url = "jdbc:h2:file:./data/rondes-nfc;AUTO_SERVER=TRUE",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.create(Rooms, Guards, Patches, Scans, Sessions)
        }
    }
}
