package com.spaceape.hiring

import javax.ws.rs._
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response.Status
import javax.ws.rs.core.Response
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
      if ( v.player1Id == player1 && v.player2Id == player2 && !v.isGameOver) {
        //These players have already another game in progress -> Request is forbidden
        throw new WebApplicationException(403);
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
    if ( !games.contains(gameId) ) {
      //Cannot find this game
      throw new WebApplicationException(404);
    }

    val game = games(gameId);
    val winnerIndex = game.winnerIndex;
    val gameOver = game.isGameOver;
    var winnerId: Option[String] = None;

    if ( winnerIndex == 1 ) winnerId = Some(game.player1Id);
    if ( winnerIndex == 2 ) winnerId = Some(game.player2Id);

    return GameState(winnerId, gameOver);
  }


  @PUT
  @Path("/{gameId}")
  def makeMove(@PathParam("gameId") gameId: String, move: Move): Response = {
    printf("inside makemove\n");
    //First find corresponding game 
    if ( !games.contains(gameId) ) {
      //Cannot find this game
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
    printf("exitting makemove\n");
    return Response.status(Response.Status.ACCEPTED).build();
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
