package com.hugo.tictactoe

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow

data class Player(
    var name: String = ""
)

data class Game(
    var gameBoard: List<Int> = List(9) { 0 }, // 0: empty, 1: player 1, 2: player 2
    var gameState: String = "invite", // "invite", "player1_turn", "player2_turn", "player1_won", "player2_won", "draw"
    var player1Id: String = "",
    var player2Id: String = ""
)

const val rows = 3
const val cols = 3


class GameModel : ViewModel() {
    val db = Firebase.firestore
    var localPlayerID = mutableStateOf<String?>(null)
    var playerMap = MutableStateFlow<Map<String, Player>>(emptyMap())
    var gameMap = MutableStateFlow<Map<String, Game>>(emptyMap())

    fun initGame() {

        //Listen for players
        db.collection("players")
            .addSnapshotListener { value, error ->
                if (error != null)
                    return@addSnapshotListener
                if (value != null) {
                    val updatedMap = value.documents.associate { doc ->
                        doc.id to doc.toObject(Player::class.java)!!
                    }
                    playerMap.value = updatedMap
                }
            }
        //Listen for games
        db.collection("games")
            .addSnapshotListener { value, error ->
                if (error != null) {
                    Log.e("FirestoreError", "Error listening to games: $error")
                    return@addSnapshotListener
                }
                if (value != null) {
                    val updatedMap = value.documents.associate { doc ->
                        doc.id to doc.toObject(Game::class.java)!!
                    }
                    gameMap.value = updatedMap
                    Log.d("FirestoreUpdate", "Game map updated: $updatedMap")
                }
            }
    }

    private fun checkWinner(board: List<Int>): Int {
        // Check rows
        for (i in 0..2) {
            if (board[i * 3] != 0 && board[i * 3] == board[i * 3 + 1]
                && board[i * 3] == board[i * 3 + 2]
            ) {
                return board[i * 3]
            }
        }
        //Check columns
        for (i in 0..2) {
            if (board[i] != 0 && board[i] == board[i + 3]
                && board[i] == board[i + 6]
            ) {
                return board[i]
            }
        }
        //Check diagonals
        if (board[0] != 0 && board[0] == board[4] && board[0] == board[8]) {
            return board[0]
        }
        if (board[2] != 0 && board[2] == board[4] && board[2] == board[6]) {
            return board[2]
        }
        //Check draw
        if (!board.contains(0)) {
            return 3
        }
        return 0 // No winner yet
    }

    fun checkGameState(gameId: String?, cell: Int) {
        if (cell < 0 || cell >= 9)
            return // Invalid cell index, return immediately


        if (gameId.isNullOrEmpty() || localPlayerID.value.isNullOrEmpty())
            return

        val game: Game? = gameMap.value[gameId]
        if (game != null) {

            val myTurn =
                game.gameState == "player1_turn" &&
                        game.player1Id == localPlayerID.value ||
                        game.gameState == "player2_turn" &&
                        game.player2Id == localPlayerID.value
            if (!myTurn) return

            val list: MutableList<Int> = game.gameBoard.toMutableList()

            if (game.gameState == "player1_turn") {
                if (list[cell] == 0)
                    list[cell] = 1
            } else if (game.gameState == "player2_turn") {
                if (list[cell] == 0)
                    list[cell] = 2
            }

           var state = if (game.gameState == "player1_turn") {
                "player2_turn"
            } else "player1_turn"

            val winner = checkWinner(list.toList())
            when (winner) {
                1 -> {
                    state = "player1_won"
                }
                2 -> {
                    state = "player2_won"
                }
                3 -> {
                    state = "draw"
                }
            }

            db.collection("games").document(gameId)
                .update(
                    "gameBoard", list,
                    "gameState", state
                )
        }

    }


}