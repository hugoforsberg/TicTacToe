package com.hugo.tictactoe

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.material3.MaterialTheme.typography as typography1


data class Player(
    var name: String = ""
)

data class Game(
    var gameboard: List<Int> = List(9) { 0 }, // 0: empty, 1: player 1, 2: player 2
    var gameState: String = "invite", // "invite", "player1_turn", "player2_turn", "player1_won", "player2_won", "draw"
    var player1Id: String = "",
    var player2Id: String = ""
)

@Composable
fun TicTacToe() {
    val navController = rememberNavController()
    val model = GameModel()
    model.initGame()

    // val players by
    // playerMap.asStateFlow().collectAsStateWithLifecycle()

    // val games by
    // gameMap.asStateFlow().collectAsStateWithLifecycle()

    NavHost(navController, startDestination = "player") {
        composable("player") {
            NewPlayerScreen(navController, model)
        }
        composable("lobby") {
            LobbyScreen(navController, model)
        }
        composable("game/{gameId}") { backStackEntry ->
            val gameId =
                backStackEntry.arguments?.getString("gameId")
            GameScreen(navController, model, gameId)
        }
    }
}

@Composable
fun NewPlayerScreen(navController: NavController, model: GameModel) {
    val sharedPreferences =
        LocalContext.current.getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)

    //Check for playerID in SharedPreferences
    LaunchedEffect(Unit) {
        model.localPlayerID.value =
            sharedPreferences.getString("playerId", null)
        if (model.localPlayerID.value != null) {
            navController.navigate("lobby")
        }
    }
    if (model.localPlayerID.value == null) {

        var playerName by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Welcome to TicTacToe!")

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = playerName,
                onValueChange = { playerName = it },
                label = { Text("Player Name") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (playerName.isNotBlank()) {
                        //Create new player in firestore
                        val newPlayer = Player(name = playerName)
                        model.db.collection("players").add(newPlayer)
                            .addOnSuccessListener { documentRef ->
                                val newPlayerID = documentRef.id
                                //Save playerID in sharedPreferences
                                sharedPreferences.edit().putString(
                                    "playerId", newPlayerID
                                ).apply()
                                //Update local variable and navigate to lobby
                                model.localPlayerID.value = newPlayerID
                                navController.navigate("lobby")
                            }.addOnFailureListener { error ->
                                Log.e("AddPlayer", "Error creating player: ${error.message}")
                            }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Create Player")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LobbyScreen(navController: NavController, model: GameModel) {
    val players by model.playerMap.asStateFlow().collectAsStateWithLifecycle()
    val games by model.gameMap.asStateFlow().collectAsStateWithLifecycle()

    LaunchedEffect(games) {
        games.forEach { (gameId, game) ->
            // TODO: Popup with accept invite?
            if ((game.player1Id == model.localPlayerID.value || game.player2Id == model.localPlayerID.value) && game.gameState == "player1_turn") {
                navController.navigate("game/${gameId}")
            }
        }
    }

    var playerName = "Unknown?"
    players[model.localPlayerID.value]?.let {
        playerName = it.name
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("TicTacToe - $playerName") }) }
    ) { innerPadding ->
        //All players except yourself
        val otherPlayers = players.entries.filter { it.key != model.localPlayerID.value }

        if (otherPlayers.isEmpty()) { //No players are online
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center

            ) {
                Text(
                    text = "No players online",
                    style = typography1.headlineMedium
                )
            }

        } else {
            LazyColumn(modifier = Modifier.padding(innerPadding)) {
                items(otherPlayers) { (documentId, player) ->
                    ListItem(
                        headlineContent = {
                            Text("Player Name: ${player.name}")
                        },
                        supportingContent = {
                            Text("Status: ...")
                        },
                        trailingContent = {
                            var hasGame = false
                            games.forEach { (gameId, game) ->
                                if (game.player1Id == model.localPlayerID.value && game.gameState == "invite") {
                                    Text("Waiting for response...")
                                    hasGame = true
                                } else if (game.player2Id == model.localPlayerID.value && game.gameState == "invite") {
                                    Row {
                                        Button(
                                            onClick = {
                                                model.db.collection("games").document(gameId)
                                                    .update("gameState", "player1_turn")
                                                    .addOnSuccessListener {
                                                        navController.navigate("game/${gameId}")
                                                    }
                                                    .addOnFailureListener {
                                                        Log.e(
                                                            "Error",
                                                            "Error updating game: $gameId"
                                                        )
                                                    }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF4CAF50), // Grön
                                                contentColor = Color.White              // Textfärg
                                            )
                                            //TODO : edit button size
                                        ) {
                                            Text("Accept invite")
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                model.db.collection("games").document(gameId)
                                                    .delete()
                                                    .addOnSuccessListener {
                                                        Log.i(
                                                            "Decline",
                                                            "Invite declined and game $gameId deleted"
                                                        )
                                                    }
                                                    .addOnFailureListener {
                                                        Log.e(
                                                            "Error",
                                                            "Error declining game: $gameId"
                                                        )
                                                    }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color.Red,       // Röd
                                                contentColor = Color.White       // Textfärg
                                            ),
                                            //TODO : edit button size
                                        ) {
                                            Text("Decline invite")
                                        }
                                    }
                                    hasGame = true
                                }
                            }
                            if (!hasGame) {
                                Button(onClick = {
                                    model.db.collection("games")
                                        .add(
                                            Game(
                                                gameState = "invite",
                                                player1Id = model.localPlayerID.value!!,
                                                player2Id = documentId
                                            )
                                        )
                                        .addOnSuccessListener { documentRef ->
                                            // TODO: Navigate?
                                        }
                                }) {
                                    Text("Challenge")
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(navController: NavController, model: GameModel, gameId: String?) {
    val players by model.playerMap.asStateFlow().collectAsStateWithLifecycle()
    val games by model.gameMap.asStateFlow().collectAsStateWithLifecycle()

    if (gameId != null && games.containsKey(gameId)) {
        Scaffold(
            topBar = { TopAppBar(title = { Text("TicTacToe - $gameId") }) }
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) {

                Text("Game state: ${games[gameId]!!.gameState}")
                Text("Player 1: ${players[games[gameId]!!.player1Id]!!.name}")
                Text("Player 2: ${players[games[gameId]!!.player2Id]!!.name}")

                for (i in 0..2) {
                    Row {
                        for (j in 0..2) {
                            Button(onClick = { }) {
                                Text("Cell $i x $j")
                            }
                        }
                    }
                }

            }
        }
    } else {
        Log.e(
            "HugoError",
            "Error Game not found: $gameId"
        )
        navController.navigate("lobby")
    }
}


