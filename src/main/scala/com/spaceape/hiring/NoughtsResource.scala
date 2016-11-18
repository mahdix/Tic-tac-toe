package com.spaceape.hiring

import javax.ws.rs._
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response.Status
import javax.ws.rs.core.Response
import collection.mutable.HashMap
import redis.clients.jedis.Jedis
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import scala.collection.JavaConversions._

import com.spaceape.hiring.model.{GameState, Move, Game};

@Path("/game")
@Produces(Array(MediaType.APPLICATION_JSON))
@Consumes(Array(MediaType.APPLICATION_JSON))
class NoughtsResource() {
  val jedis = new Jedis("localhost");

  val objectMapper = new ObjectMapper()
  objectMapper.registerModule(DefaultScalaModule)

  @POST
  def createGame(@QueryParam("player1Id") player1: String, @QueryParam("player2Id") player2: String): String = {

    //You cannot play against yourself, unless your are very bored!
    if ( player1 == player2 ) {
        //I prefer to use a separate error code for this case, but HTTP error codes
        //are limited and no other code has a more meaningful type
        throw new WebApplicationException(403);
    }

    //First make sure there is no other game between these two players
    for( id <- getAllGames() ) {
      val v = loadGame(id).get

      if ( v.player1Id == player1 && v.player2Id == player2 && !v.isGameOver) {
        //These players have already another game in progress -> Request is forbidden
        throw new WebApplicationException(403);
      }
    }

    //Note that by choosing an integer number, we are limited to ~2^32 games
    return saveGame(new Game(player1, player2))
  }

  //Player has won a game, so increment his score
  def updatePlayerScore(playerId: String) {
    jedis.zincrby("BOARD", 1, playerId)
  }

  def getAllGames(): Set[String] = {
    val gameIds = jedis.keys("GAME::*");
    var result: Set[String] = Set()

    for(k <- gameIds ) {
      result += k.substring(6)
    }

    return result
  }

  def updateGame(game: Game, gameId: String) {
    val json = objectMapper.writeValueAsString(game)
    jedis.set("GAME::"+gameId, json);
  }

  //Save a new game
  def saveGame(game: Game): String = {
    var gameId = jedis.get("NEXT_ID");
    if ( gameId == null ) gameId = "0";

    val json = objectMapper.writeValueAsString(game)
    jedis.set("GAME::"+gameId, json);

    //update redis counter for next game id
    var intId = gameId.toInt;
    intId = intId + 1
    jedis.set("NEXT_ID", intId.toString)

    return gameId;
  }

  def loadGame(gameId: String): Option[Game] = {
    val json = jedis.get("GAME::"+gameId); 
    if ( json == null ) return None;

    return Some(objectMapper.readValue(json, classOf[Game]));
  }

  @GET
  def getLeaderboard(): Set[LeaderboardEntry] = {
    //Return 10 highest scores in descending order
    val redisBoard = jedis.zrevrange("BOARD", -10, -1);
    var result: Set[String] = Set()

    //Seems I need to convert java created mutable set to a Scala native immutable set
    for(k <- redisBoard ) {
      val score = jedis.zscore("BOARD", k)
      result += LeaderboardEntry(k, score)
    }

    return result
  }

  @GET
  @Path("/{gameId}")
  def getGame(@PathParam("gameId") gameId: String): GameState = {
    val maybeGame = loadGame(gameId);

    if ( maybeGame == None ) {
      //Cannot find this game
      throw new WebApplicationException(404);
    }

    val game = maybeGame.get

    val winnerIndex = game.winnerIndex;
    val gameOver = game.isGameOver;
    var winnerId: Option[String] = None;

    if ( winnerIndex == 1 ) winnerId = Some(game.player1Id);
    if ( winnerIndex == 2 ) winnerId = Some(game.player2Id);

    return GameState(winnerId, gameOver, getMoveCount(game.matrix) , game.activePlayer);
  }


  @PUT
  @Path("/{gameId}")
  def makeMove(@PathParam("gameId") gameId: String, move: Move): Response = {
    //First find corresponding game 
    val maybeGame = loadGame(gameId);

    if ( maybeGame == None ) {
      //Cannot find this game
      throw new WebApplicationException(404);
    }

    val game = maybeGame.get;

    var playerIndex = 0;

    if ( move.playerId == game.player1Id ) {
      playerIndex = 1;
    }

    if ( move.playerId == game.player2Id ) {
      playerIndex = 2;
    }

    //Validate the move and return HTTP error if its not valid
    validateMove(game, playerIndex, move.x, move.y);

    game.matrix(move.x)(move.y) = playerIndex;

    //switch active player 
    game.activePlayer = 3 - game.activePlayer;

    //see if playerIndex is a winner because of this move
    if ( isWinner(game.matrix, playerIndex) ) {
      game.isGameOver = true;
      game.winnerIndex = playerIndex;
      updatePlayerScore(move.playerId);
    }
    else if ( isDraw(game.matrix) ) {
      game.isGameOver = true;
      game.winnerIndex = 0;
    }

    updateGame(game, gameId)

    return Response.status(Response.Status.OK).build();
  }

  def isWinner(matrix: Array[Array[Int]], playerIndex: Int): Boolean = {
    var winner = false;

    //check for row wins
    for(r <- 0 to 2) {
      winner = true;
      for(c <- 0 to 2) {
        if ( matrix(r)(c) != playerIndex ) {
          winner = false;
        }
      }
      if ( winner ) return true;
    }

    //check for column wins
    for(c <- 0 to 2) {
      winner = true;
      for(r <- 0 to 2) {
        if ( matrix(r)(c) != playerIndex ) {
          winner = false;
        }
      }
      if ( winner ) return true;
    }

    //check for diagonal wins
    winner = true;
    for( x <- 0 to 2 ) {
      if ( matrix(x)(x) != playerIndex ) winner = false;
    }
    if ( winner ) return true;

    winner = true;
    for( x <- 0 to 2 ) {
      if ( matrix(x)(2-x) != playerIndex ) winner = false;
    }
    if ( winner ) return true;

    return false;
  }

  def isDraw(matrix: Array[Array[Int]]): Boolean = {
    //If at least there is one empty cell, then game is not over
    for(r <- 0 to 2) {
      for(c <- 0 to 2) {
        if ( matrix(r)(c) == 0 ) return false;
      }
    }

    return true;
  }

  def getMoveCount(matrix: Array[Array[Int]]): Int = {
    var result = 0;

    //If at least there is one empty cell, then game is not over
    for(r <- 0 to 2) {
      for(c <- 0 to 2) {
        if ( matrix(r)(c) != 0 ) result = result+1;
      }
    }

    return result;
  }

  def validateMove(game: Game, playerIndex: Int, x: Int, y: Int) {
    //you cannot make a move for a game which is finished
    if ( game.isGameOver ) {
      throw new WebApplicationException(403);
    }

    //If given playerId does not belong to this game, just return HTTP error of conflict
    if ( playerIndex == 0 ) {
      throw new WebApplicationException(409);
    }

    //If it's player1's turn and player 2 is sending a move, or the other way,
    //just return HTTP error as not authorized
    if ( playerIndex == 1 && game.activePlayer == 2 ) {
      throw new WebApplicationException(401);
    }

    if ( playerIndex == 2 && game.activePlayer == 1 ) {
      throw new WebApplicationException(401);
    }

    //Make sure given position is not outside game matrix - If not return Bad Request error code
    if ( x < 0 || y < 0 || x > 2 || y > 2 ) {
      throw new WebApplicationException(400);
    }

    //Make sure the matrix position player wants to put a piece, is already empty
    //If not return HTTP Error: Not Acceptable
    if ( game.matrix(x)(y) != 0 ) {  
      throw new WebApplicationException(406);
    }
  }
}
