package com.hugo.tictactoe

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow

class GameModel : ViewModel() {
    val db = Firebase.firestore
    var localPlayerID = mutableStateOf<String?>(null)
    var playerMap = MutableStateFlow<Map<String, Player>>(emptyMap())
    var gameMap = MutableStateFlow<Map<String, Game>>(emptyMap())

    fun initGame() {

        //Listen for players
        db.collection("players").addSnapshotListener { value, error ->
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
        db.collection("games").addSnapshotListener { value, error ->
            if (error != null)
                return@addSnapshotListener
            if (value != null) {
                val updatedMap = value.documents.associate { doc ->
                    doc.id to doc.toObject(Game::class.java)!!
                }
                gameMap.value = updatedMap
            }
        }
    }
}