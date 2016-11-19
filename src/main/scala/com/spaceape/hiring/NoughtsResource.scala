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

import com.spaceape.hiring.model.{GameState, Move, Game, LeaderboardEntry};
import com.spaceape.hiring.helper.GameUtils

@Path("/game")
@Produces(Array(MediaType.APPLICATION_JSON))
@Consumes(Array(MediaType.APPLICATION_JSON))
class NoughtsResource() {
  val jedis = new Jedis("localhost");

  val objectMapper = new ObjectMapper()
  objectMapper.registerModule(DefaultScalaModule)

  @POST
  def createGame(@QueryParam("player1Id") player1: String, @QueryParam("player2Id") player2: String): String = {
    //Concurrency: It is assumed cocurrency issues will happen during making a move. But they are also possible when creating a game.
    //Suppose that a game between A and B is being created, during processing, another request for same players are received. This can result
    //In having two simultaneously running games between same poayers which can break our rules.
    //I ignore this issue because it is not part of problem assumptions and also this will not have any benefits for players

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
  @Path("/leaderBoard")
  def getLeaderboard(): Seq[LeaderboardEntry] = {
    //Concurrency: It is possible to have scores changed after reaing a list of highest-scored players
    //and before returning the result set. As a result, the response may not be up-to-date and accurate
    //but I think this is fine because this is not a sensitive call

    //Return 10 highest scores in descending order
    val redisBoard = jedis.zrevrange("BOARD", -10, -1);
    var result: Seq[LeaderboardEntry] = Seq()

    //Seems I need to convert java created mutable set to a Scala native immutable set
    for(k <- redisBoard ) {
      val score = jedis.zscore("BOARD", k)
      //The idiomatic way is to use "map" function to create the result sequence
      //in one shot
      result = result :+ LeaderboardEntry(k, score.toInt)
    }

    return result
  }

  @GET
  @Path("/{gameId}")
  def getGame(@PathParam("gameId") gameId: String): GameState = {
    //Concurrency: Same as getLeaderboard. It is possible to return stale data but this is fine for the same reason
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

    return GameState(winnerId, gameOver, GameUtils.getMoveCount(game.matrix) , game.activePlayer);
  }


  @PUT
  @Path("/{gameId}")
  def makeMove(@PathParam("gameId") gameId: String, move: Move): Response = {
    //Concurrency: This is a critical section and no parallel moves should be allowed.
    //We can do corse-grained locking (this.synchronized) or a fine-grained locking which only locks games for these two players
    //here I just choose coard-grained locking (because it is easier and more readable to implement), so each move will lock the whole 
    //object, but in a highly concurrent game where availability is important for us, we should use some finer-grained locking (like StampedLock)
    
    //Concurrency: This lock, prevents players making any other move, while we are processing this move because if this happens
    //game will move to inconsistent state (e.g. player1 has won but player2 has made a move after loosing the game)
    this.synchronized {
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
      GameUtils.validateMove(game, playerIndex, move.x, move.y);

      game.matrix(move.x)(move.y) = playerIndex;

      //switch active player 
      game.activePlayer = 3 - game.activePlayer;

      //see if playerIndex is a winner because of this move
      if ( GameUtils.isWinner(game.matrix, playerIndex) ) {
        game.isGameOver = true;
        game.winnerIndex = playerIndex;
        updatePlayerScore(move.playerId);
      }
      else if ( GameUtils.isDraw(game.matrix) ) {
        game.isGameOver = true;
        game.winnerIndex = 0;
      }

      updateGame(game, gameId)
    }

    return Response.status(Response.Status.OK).build();
  }
}
