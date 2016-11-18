
package com.spaceape.hiring

import javax.ws.rs.core.Response.Status

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.spaceape.hiring.model.{Move, GameState}
import io.dropwizard.testing.junit.DropwizardAppRule
import org.scalatest.junit.JUnitSuite
import redis.clients.jedis.Jedis

import org.junit.Test
import org.junit.ClassRule
import org.junit.Before
import com.mashape.unirest.http.Unirest
import org.scalatest.Matchers

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
      return "";
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

	@Test
	def testPlayer1Win {
    val gameId = initGame("1", "2")
    runMoves(gameId, Seq(
      Move("1", 0, 0),
      Move("2", 1, 0),
      Move("1", 0, 1),
      Move("2", 1, 1),
      Move("1", 0, 2)))

    val game = getState(gameId);
    if ( game.winnerId != Some("1") || game.gameOver != true ) {
      throw new RuntimeException(s"Invalid game state: ${game.winnerId}, ${game.gameOver}")
    }
	}

  //A general purpose test helper method which does some moves and checks the game status
	def testGameFlow(p1: String, p2: String, moves: Seq[Move], 
                   expectedWinner: Option[String], expectedGameOver: Boolean, 
                   expectedMoveCount: Int, expectedActivePlayer: Int) {
    val gameId = initGame(p1, p2)
    runMoves(gameId, moves); 

    val game = getState(gameId);
    if ( game.winnerId != expectedWinner || game.gameOver != expectedGameOver ) {
      throw new RuntimeException(s"Invalid game state: ${game.winnerId}, ${game.gameOver}")
    }

    if ( expectedMoveCount != game.moveCount ) {
      throw new RuntimeException(s"Invalid move count reported: ${game.moveCount}, expecting: ${expectedMoveCount}");
    }

    if ( expectedActivePlayer != game.activePlayerIndex ) {
      throw new RuntimeException(s"Invalid active player reported: ${game.activePlayerIndex}, expecting: ${expectedActivePlayer}");
    }

    //Try to make a move after game is over and make sure HTTP error 403 is returned
    if ( expectedGameOver ) {
      runMoves(gameId, Seq(
        Move(p1, 0, 0)), 403);
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
      Move("1", 0, 0)), 404);

    val response = Unirest.get(s"$baseUrl/DSADSA_").asString()

    if(response.getStatus != 404) {
      throw new RuntimeException("Game state should not be allowed for non-existing games");
    }

    //Edge case 4 - No player cannot make a move out of his turn
    var gameId = initGame("d", "e")
    runMoves(gameId, Seq(
      Move("e", 0, 0)), 401);

    gameId = initGame("f", "g")
    runMoves(gameId, Seq( Move("f", 0, 0)));
    runMoves(gameId, Seq( Move("f", 1, 0)), 401);

    gameId = initGame("f1", "g1")
    runMoves(gameId, Seq( Move("f1", 0, 0)));
    runMoves(gameId, Seq( Move("g1", 1, 0)));
    runMoves(gameId, Seq( Move("g1", 2, 0)), 401);

    //Edge case 5 - Only game players can submit a move
    runMoves(gameId, Seq(
      Move("DSA", 0, 0)), 409);

    //Edge case 6 - You cannot make a move which lies outside board
    gameId = initGame("h", "i")
    runMoves(gameId, Seq( Move("h", -1, -1)), 400);
    runMoves(gameId, Seq( Move("h", 3, 1)), 400);
    runMoves(gameId, Seq( Move("h", 3, 3)), 400);

    //Edge case 7 - You cannot make a move if game is over
    //this is tested in testGameFlow

    //Edge case 8 - You cannot make a move to a pre-occupied cell
    gameId = initGame("j", "k")
    runMoves(gameId, Seq( Move("j", 0, 0)));
    runMoves(gameId, Seq( Move("k", 0, 0)), 406);
    runMoves(gameId, Seq( Move("k", 0, 1)));
    runMoves(gameId, Seq( Move("j", 0, 1)), 406);
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
      Some("4"), true, 6, 1);

    //Another win scenario - This time for the second player
    testGameFlow("3a", "4a",
      Seq(
        Move("3a", 0, 0),
        Move("4a", 0, 1),
        Move("3a", 0, 2),
        Move("4a", 1, 1),
        Move("3a", 1, 0),
        Move("4a", 2, 1)), 
      Some("4a"), true, 6, 1);

    //Without any move, game state should be correct
    testGameFlow("5", "6",
      Seq(), 
      None, false, 0, 1);

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
      None, true, 9, 2);
  }

  @Before
  def setUp() {
    val jedis = new Jedis("localhost");
    jedis.flushAll()
  }
}
