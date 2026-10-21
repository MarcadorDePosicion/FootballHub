package pl.stasgroch.footballhub.data.remote

import pl.stasgroch.footballhub.data.model.*
import java.time.LocalDate

object DemoData {
    fun create(): FootballData {
        val today = LocalDate.now()
        val teams = listOf(
            Team(1, "Arsenal", "ARS", "Londyn", "Emirates Stadium", 1886, 1),
            Team(2, "Liverpool", "LIV", "Liverpool", "Anfield", 1892, 1),
            Team(3, "Manchester City", "MCI", "Manchester", "Etihad Stadium", 1880, 1),
            Team(4, "Chelsea", "CHE", "Londyn", "Stamford Bridge", 1905, 1),
            Team(5, "FC Barcelona", "BAR", "Barcelona", "Camp Nou", 1899, 2),
            Team(6, "Real Madrid", "RMA", "Madryt", "Santiago Bernabéu", 1902, 2),
            Team(7, "Atlético Madrid", "ATM", "Madryt", "Metropolitano", 1903, 2),
            Team(8, "Athletic Bilbao", "ATH", "Bilbao", "San Mamés", 1898, 2),
            Team(9, "Lech Poznań", "LPO", "Poznań", "Enea Stadion", 1922, 3),
            Team(10, "Legia Warszawa", "LEG", "Warszawa", "Stadion Wojska Polskiego", 1916, 3),
            Team(11, "Raków Częstochowa", "RAK", "Częstochowa", "Miejski Stadion Piłkarski", 1921, 3),
            Team(12, "Jagiellonia Białystok", "JAG", "Białystok", "Chorten Arena", 1920, 3)
        )
        val matches = listOf(
            Match(1,1,1,2,today.toString(),"18:30","SCHEDULED",null,null,9),
            Match(2,2,5,6,today.toString(),"21:00","SCHEDULED",null,null,9),
            Match(3,3,9,10,today.toString(),"17:30","FINISHED",2,1,12),
            Match(4,1,3,4,today.minusDays(1).toString(),"20:00","FINISHED",3,1,9),
            Match(5,2,7,8,today.minusDays(1).toString(),"19:00","FINISHED",1,1,9),
            Match(6,3,11,12,today.plusDays(1).toString(),"20:30","SCHEDULED",null,null,12)
        )
        val standings = teams.map {
            val rank = (it.id - 1) % 4
            Standing(it.leagueId, it.id, 8, 6-rank, 1, 1+rank, 19-rank*3, 6+rank*2)
        }
        return FootballData(teams, listOf(League(1,"Premier League","Anglia"), League(2,"La Liga","Hiszpania"), League(3,"Ekstraklasa","Polska")), matches, standings)
    }
}

