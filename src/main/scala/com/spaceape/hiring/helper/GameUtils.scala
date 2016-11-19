package com.spaceape.hiring.helper

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

object GameUtils {
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
