package com.spaceape.hiring

import javax.ws.rs._
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response.Status
import collection.mutable.HashMap

import com.spaceape.hiring.model.{GameState, Move, Game};

@Path("/game")
@Produces(Array(MediaType.APPLICATION_JSON))
@Consumes(Array(MediaType.APPLICATION_JSON))
class NoughtsResource() {
  val games = new HashMap[String,Game]();

  @POST
  def createGame(@QueryParam("player1Id") player1: String, @QueryParam("player2Id") player2: String): String = {
    //First make sure there is no other game between these two players
    for( (k,v) <- games ) {
      if ( v.player1Id == player1 && v.player2Id == player2 ) {
        throw new WebApplicationException("DSADSAD", 404);
      }
    }

    //Note that by choosing an integer number (size of the games map) as the key, we are limited to ~2^32 games
    val nextId = games.size.toString();
    games(nextId) = new Game(player1, player2);
    
    return nextId;
  }

  @GET
  @Path("/{gameId}")
  def getGame(@PathParam("gameId") gameId: String): GameState = {
    if ( games.contains(gameId) ) {
      val game = games(gameId);
      val winnerIndex = game.winnerIndex;
      val gameOver = game.isGameOver;
      var winnerId: Option[String] = None;

      if ( winnerIndex == 1 ) winnerId = Some(game.player1Id);
      if ( winnerIndex == 2 ) winnerId = Some(game.player2Id);

      return GameState(winnerId, gameOver);
    }

    throw new WebApplicationException(404);
  }


  @PUT
  @Path("/{gameId}")
  def makeMove(@PathParam("gameId") gameId: String, move: Move) {
    
  }
}
