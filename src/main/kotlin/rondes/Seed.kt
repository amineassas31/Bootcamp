package rondes

import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import rondes.db.GuardRole
import rondes.db.Guards
import rondes.db.Rooms
import rondes.service.AuthService

/**
 * Jeu de donnees de demo, insere uniquement si la base est vide (redemarrages sans duplication).
 * Les patches ne sont volontairement PAS pre-associes : l'enrollment en direct fait partie de la demo.
 */
object Seed {
    fun run() {
        transaction {
            if (Guards.selectAll().empty()) {
                Guards.insert {
                    it[badge] = "DIR01"
                    it[fullName] = "Amine Assas (Direction)"
                    it[pinHash] = AuthService.hashPin("0000")
                    it[role] = GuardRole.DIRECTION
                }
                Guards.insert {
                    it[badge] = "CP01"
                    it[fullName] = "Chef de poste"
                    it[pinHash] = AuthService.hashPin("1111")
                    it[role] = GuardRole.CHEF_DE_POSTE
                }
                Guards.insert {
                    it[badge] = "G001"
                    it[fullName] = "Gardien Martin"
                    it[pinHash] = AuthService.hashPin("2222")
                    it[role] = GuardRole.GARDIEN
                }
                Guards.insert {
                    it[badge] = "G002"
                    it[fullName] = "Gardien Dubois"
                    it[pinHash] = AuthService.hashPin("3333")
                    it[role] = GuardRole.GARDIEN
                }
            }

            if (Rooms.selectAll().empty()) {
                Rooms.insert {
                    it[name] = "Salle Egypte antique"
                    it[building] = "Batiment principal"
                    it[floor] = "RDC"
                    it[orangeThresholdMinutes] = 60
                    it[redThresholdMinutes] = 120
                }
                Rooms.insert {
                    it[name] = "Salle Renaissance"
                    it[building] = "Batiment principal"
                    it[floor] = "1er etage"
                    it[orangeThresholdMinutes] = 90
                    it[redThresholdMinutes] = 180
                }
                Rooms.insert {
                    it[name] = "Reserve sous-sol"
                    it[building] = "Batiment principal"
                    it[floor] = "Sous-sol"
                    it[orangeThresholdMinutes] = 180
                    it[redThresholdMinutes] = 360
                }
                Rooms.insert {
                    it[name] = "Galerie des sculptures"
                    it[building] = "Aile est"
                    it[floor] = "RDC"
                    it[orangeThresholdMinutes] = 60
                    it[redThresholdMinutes] = 150
                }
                Rooms.insert {
                    it[name] = "Cabinet des arts graphiques"
                    it[building] = "Aile est"
                    it[floor] = "2eme etage"
                    it[orangeThresholdMinutes] = 120
                    it[redThresholdMinutes] = 240
                }
            }
        }
    }
}
