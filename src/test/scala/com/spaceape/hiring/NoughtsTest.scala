package com.spaceape.hiring

import javax.ws.rs.core.Response.Status

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import io.dropwizard.testing.junit.DropwizardAppRule

import org.scalatest.junit.JUnitSuite
import org.junit.Test
import org.junit.ClassRule
import org.junit.Before
import com.mashape.unirest.http.Unirest
import org.scalatest.Matchers

import redis.clients.jedis.Jedis
import com.spaceape.hiring.model.{Move, GameState, LeaderboardEntry}

object NoughtsTest {
	@ClassRule def rule = new DropwizardAppRule[NoughtsConfiguration](classOf[NoughtsApplication], "test.yml")
}

class NoughtsTest extends JUnitSuite with Matchers {

  val baseUrl = "http://localhost:8080/game"

  val objectMapper = new ObjectMapper()
  objectMapper.registerModule(DefaultScalaModule)

  def initGame(player1Id: String, player2Id: String, expectedStatus: Int = 200): String = {
    val response = Unirest.post(baseUrl)
      .queryString("player1Id", player1Id)
      .queryString("player2Id", player2Id)
      .asString()

    if(response.getStatus != expectedStatus) {
      throw new RuntimeException(s"${response.getStatus} when creating game: ${response.getBody}")
    }

    if ( expectedStatus == 200 ) {
      return objectMapper.readValue(response.getBody, classOf[String])
    } else {
      return ""
    }
  }

  def runMoves(gameId: String, moves: Seq[Move], expectedStatus: Int = 200) = {
    moves.foreach(move => {
      val response = Unirest.put(s"$baseUrl/$gameId")
        .header("Content-Type", "application/json")
        .body(objectMapper.writeValueAsString(move))
        .asString()

      if(response.getStatus != expectedStatus) {
        throw new RuntimeException(s"${response.getStatus} error when making move (Expecting ${expectedStatus})")
      }
    })
  }

  def getState(gameId: String) = {
    val response = Unirest.get(s"$baseUrl/$gameId").asString()

    if(response.getStatus != Status.OK.getStatusCode) {
      throw new RuntimeException(s"${response.getStatus} when getting state: ${response.getBody}")
    }

    objectMapper.readValue(response.getBody, classOf[GameState])
  }


  def getLeaderboard() = {
    val response = Unirest.get(s"$baseUrl/leaderBoard").asString()

    if(response.getStatus != Status.OK.getStatusCode) {
      throw new RuntimeException(s"${response.getStatus} when getting state: ${response.getBody}")
    }

    //For some reason, we don't get the original data type here
    objectMapper.readValue(response.getBody, classOf[List[Map[String, String]]])
  }

	@Test
	def testPlayer1Win {
    val gameId = initGame("1", "2")
    runMoves(gameId, Seq(
      Move("1", 0, 0),
      Move("2", 1, 0),
      Move("1", 0, 1),
      Move("2", 1, 1),
      Move("1", 0, 2)))

    val game = getState(gameId)
    if ( game.winnerId != Some("1") || game.gameOver != true ) {
      throw new RuntimeException(s"Invalid game state: ${game.winnerId}, ${game.gameOver}")
    }
	}

  //A general purpose test helper method which does some moves and checks the game status
	def testGameFlow(p1: String, p2: String, moves: Seq[Move], 
                   expectedWinner: Option[String], expectedGameOver: Boolean, 
                   expectedMoveCount: Int, expectedActivePlayer: Int) {
    val gameId = initGame(p1, p2)
    runMoves(gameId, moves) 

    val game = getState(gameId)
    if ( game.winnerId != expectedWinner || game.gameOver != expectedGameOver ) {
      throw new RuntimeException(s"Invalid game state: ${game.winnerId}, ${game.gameOver}")
    }

    if ( expectedMoveCount != game.moveCount ) {
      throw new RuntimeException(s"Invalid move count reported: ${game.moveCount}, expecting: ${expectedMoveCount}")
    }

    if ( expectedActivePlayer != game.activePlayerIndex ) {
      throw new RuntimeException(s"Invalid active player reported: ${game.activePlayerIndex}, expecting: ${expectedActivePlayer}")
    }

    //Try to make a move after game is over and make sure HTTP error 403 is returned
    if ( expectedGameOver ) {
      runMoves(gameId, Seq(
        Move(p1, 0, 0)), 403)
    }
  }

  @Test
  def testEdgeCases {
    //Edge case 1 - You cannot create a new game while another one is in progress with same players
    initGame("a", "b")
    initGame("a", "b", 403)

    //Edge case 2 - A player cannot create a game against himself
    initGame("c", "c", 403)

    //Edge case 3 - In none of requests, an invalid game id is allowed
    runMoves("MDSAKD#@$", Seq(
      Move("1", 0, 0)), 404)

    val response = Unirest.get(s"$baseUrl/DSADSA_").asString()

    if(response.getStatus != 404) {
      throw new RuntimeException("Game state should not be allowed for non-existing games")
    }

    //Edge case 4 - No player cannot make a move out of his turn
    var gameId = initGame("d", "e")
    runMoves(gameId, Seq(
      Move("e", 0, 0)), 401)

    gameId = initGame("f", "g")
    runMoves(gameId, Seq( Move("f", 0, 0)))
    runMoves(gameId, Seq( Move("f", 1, 0)), 401)

    gameId = initGame("f1", "g1")
    runMoves(gameId, Seq( Move("f1", 0, 0)))
    runMoves(gameId, Seq( Move("g1", 1, 0)))
    runMoves(gameId, Seq( Move("g1", 2, 0)), 401)

    //Edge case 5 - Only game players can submit a move
    runMoves(gameId, Seq(
      Move("DSA", 0, 0)), 409)

    //Edge case 6 - You cannot make a move which lies outside board
    gameId = initGame("h", "i")
    runMoves(gameId, Seq( Move("h", -1, -1)), 400)
    runMoves(gameId, Seq( Move("h", 3, 1)), 400)
    runMoves(gameId, Seq( Move("h", 3, 3)), 400)

    //Edge case 7 - You cannot make a move if game is over
    //this is tested in testGameFlow

    //Edge case 8 - You cannot make a move to a pre-occupied cell
    gameId = initGame("j", "k")
    runMoves(gameId, Seq( Move("j", 0, 0)))
    runMoves(gameId, Seq( Move("k", 0, 0)), 406)
    runMoves(gameId, Seq( Move("k", 0, 1)))
    runMoves(gameId, Seq( Move("j", 0, 1)), 406)
  }

  def winGame(winner: String, looser: String) {
    testGameFlow(looser, winner,
      Seq(
        Move(looser, 0, 0),
        Move(winner, 0, 1),
        Move(looser, 0, 2),
        Move(winner, 1, 1),
        Move(looser, 1, 0),
        Move(winner, 2, 1)), 
      Some(winner), true, 6, 1)
  }

  @Test
  def testLeaderboard {
    //a must win 5 games
    winGame("a", "b")
    winGame("a", "c")
    winGame("a", "d")
    winGame("a", "e")
    winGame("a", "f")

    //b must win 2 games
    winGame("b", "a")
    winGame("b", "c")

    //c must win 3 games
    winGame("c", "d")
    winGame("c", "d")
    winGame("c", "d")

    //d, e, f, g, h, i and j each must win 1 game
    winGame("d", "a")
    winGame("e", "a")
    winGame("f", "a")
    winGame("g", "a")
    winGame("h", "a")
    winGame("i", "a")
    winGame("j", "a")

    //now get the leaderBoard as a Seq of LeaderboardEntry
    val leaderBoard = getLeaderboard
    var correctCount = 0
    //Trying to write clean code to compare result structure with expected
    //This is not the best possible way (especially because I am relying on the order of 
    // elements with equal score)
    if (leaderBoard(0)("playerId") == "a" && leaderBoard(0)("score") == 5 ) correctCount+=1
    if (leaderBoard(1)("playerId") == "c" && leaderBoard(1)("score") == 3 ) correctCount+=1
    if (leaderBoard(2)("playerId") == "b" && leaderBoard(2)("score") == 2 ) correctCount+=1
    if (leaderBoard(3)("playerId") == "j" && leaderBoard(4)("score") == 1 ) correctCount+=1
    if (leaderBoard(4)("playerId") == "i" && leaderBoard(5)("score") == 1 ) correctCount+=1
    if (leaderBoard(5)("playerId") == "h" && leaderBoard(6)("score") == 1 ) correctCount+=1
    if (leaderBoard(6)("playerId") == "g" && leaderBoard(7)("score") == 1 ) correctCount+=1
    if (leaderBoard(7)("playerId") == "f" && leaderBoard(8)("score") == 1 ) correctCount+=1
    if (leaderBoard(8)("playerId") == "e" && leaderBoard(9)("score") == 1 ) correctCount+=1
    if (leaderBoard(9)("playerId") == "d" && leaderBoard(9)("score") == 1 ) correctCount+=1
    
    if ( correctCount != 10 ) {
      throw new RuntimeException(s"Invalid leaderBoard: ${leaderBoard}")
    }
  }

  @Test
  def stressTest {
    for( a <- 1 to 500){
      initGame(randomString(20), randomString(20))
    }
  }

  def randomString(length: Int) = {
    val r = new scala.util.Random
    val sb = new StringBuilder
    for (i <- 1 to length) {
      sb.append(r.nextPrintableChar)
    }
    sb.toString
  }

  @Test
  def testFlows {
    //Player 4 wins in a normal flow
    testGameFlow("3", "4",
      Seq(
        Move("3", 1, 0),
        Move("4", 0, 0),
        Move("3", 1, 1),
        Move("4", 0, 1),
        Move("3", 2, 2),
        Move("4", 0, 2)), 
      Some("4"), true, 6, 1)

    //Another win scenario - This time for the second player
    testGameFlow("3a", "4a",
      Seq(
        Move("3a", 0, 0),
        Move("4a", 0, 1),
        Move("3a", 0, 2),
        Move("4a", 1, 1),
        Move("3a", 1, 0),
        Move("4a", 2, 1)), 
      Some("4a"), true, 6, 1)

    //Without any move, game state should be correct
    testGameFlow("5", "6",
      Seq(), 
      None, false, 0, 1)

    //Test draw outcome
    testGameFlow("7", "8",
      Seq(
        Move("7", 0, 1),
        Move("8", 0, 0),
        Move("7", 0, 2),
        Move("8", 1, 2),
        Move("7", 1, 0),
        Move("8", 2, 0), 
        Move("7", 1, 1), 
        Move("8", 2, 1), 
        Move("7", 2, 2)), 
      None, true, 9, 2)
  }

  @Before
  def setUp() {
    //We are going to create lots of games in each test. Empty the storage to prevent
    //any conflict
    val jedis = new Jedis("localhost")
    jedis.flushAll()
  }
}
