
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
      .queryString("player1Id", "1")
      .queryString("player2Id", "2")
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

    /* getState(gameId) should be (GameState(Some("1"), true)) */
	}
}
