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
    //First find corresponding game 
    if ( !games.contains(gameId) ) {
      throw new WebApplicationException(404);
    }

    val game = games(gameId);
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
    }
    else if ( isDraw(game.matrix) ) {
      game.isGameOver = true;
      game.winnerIndex = 0;
    }
    //No need to return anything
  }

  def isWinner(matrix: Array[Array[Int]], playerIndex: Int): Boolean = {
    return false;
  }

  def isDraw(matrix: Array[Array[Int]]): Boolean = {
    return false;
  }

  def validateMove(game: Game, playerIndex: Int, x: Int, y: Int) {
    //If it's player1's turn and player 2 is sending a move, or the other way,
    //just return HTTP error
    if ( playerIndex == 1 && game.activePlayer == 2 ) {
      throw new WebApplicationException(404);
    }

    if ( playerIndex == 2 && game.activePlayer == 1 ) {
      throw new WebApplicationException(404);
    }
    //If given playerId does not belong to this game, just return HTTP error
    if ( playerIndex == 0 ) {
      throw new WebApplicationException(404);
    }

    //Make sure given position is not outside game matrix
    if ( x < 1 || y < 1 || x > 3 || y > 3 ) {
      throw new WebApplicationException(404);
    }

    //Make sure the matrix position player wants to put a piece, is already empty
    if ( game.matrix(x)(y) != 0 ) {
      throw new WebApplicationException(404);
    }

    if ( game.isGameOver ) {
      throw new WebApplicationException(404);
    }

  }
}
