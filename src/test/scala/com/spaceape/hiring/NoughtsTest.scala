
package com.spaceape.hiring

import javax.ws.rs.core.Response.Status

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.spaceape.hiring.model.{Move, GameState}
import io.dropwizard.testing.junit.DropwizardAppRule
import org.scalatest.junit.JUnitSuite

import org.junit.Test
import org.junit.ClassRule
import com.mashape.unirest.http.Unirest
import org.scalatest.Matchers


object NoughtsTest {
	@ClassRule def rule = new DropwizardAppRule[NoughtsConfiguration](classOf[NoughtsApplication], "test.yml")
}

class NoughtsTest extends JUnitSuite with Matchers {

  val baseUrl = "http://localhost:8080/game"

  val objectMapper = new ObjectMapper()
  objectMapper.registerModule(DefaultScalaModule)

  def initGame(player1Id: String, player2Id: String) = {
    val response = Unirest.post(baseUrl)
      .queryString("player1Id", player1Id)
      .queryString("player2Id", player2Id)
      .asString()

    if(response.getStatus != Status.OK.getStatusCode) {
      throw new RuntimeException(s"${response.getStatus} when creating game: ${response.getBody}")
    }

    objectMapper.readValue(response.getBody, classOf[String])
  }

  def runMoves(gameId: String, moves: Seq[Move]) = {
    moves.foreach(move => {
      val response = Unirest.put(s"$baseUrl/$gameId")
        .header("Content-Type", "application/json")
        .body(objectMapper.writeValueAsString(move))
        .asString()

      if(response.getStatus != Status.ACCEPTED.getStatusCode) {
        throw new RuntimeException(s"${response.getStatus} when making move: ${response.getBody}")
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
    expectedWinner: Option[String], expectedGameOver: Boolean) {

    val gameId = initGame(p1, p2)
    runMoves(gameId, moves); 

    val game = getState(gameId);
    if ( game.winnerId != expectedWinner || game.gameOver != expectedGameOver ) {
      throw new RuntimeException(s"Invalid game state: ${game.winnerId}, ${game.gameOver}")
    }
	}

  @Test
  def testEdgeCases {
    //Edge case 1 - You cannot create a new game while another one is in progress with same players
    //Edge case 2 - A player cannot create a game against himself
    //Edge case 3 - In no request, an invalid game id is allowed
    //Edge case 4 - No player cannot make a move out of his turn
    //Edge case 5 - The first player must make the first move
    //Edge case 6 - You cannot make a move which lies outside board
    //Edge case 7 - You cannot make a move if game is over
    //Edge case 8 - You cannot pass invalid playerId in moves
    //Edge case 9 - You cannot make a move to a pre-occupied cell
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
      Some("4"), true);

    //Without any move, game state should be correct
    testGameFlow("5", "6",
      Seq(), 
      None, false);

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
      None, true);
  }
}
