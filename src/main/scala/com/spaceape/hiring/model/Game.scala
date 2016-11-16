package com.spaceape.hiring.model


class Game(val p1: String, val p2: String) {
  var player1Id: String = p1;
  var player2Id: String = p2;

  /* These fields have fixed value upon game creation */

  //whose turn is it?
  var activePlayer: Int = 1;
  var isGameOver: Boolean = false;
  var winnerIndex: Int = 0;  /*0 means there is no winner */
 
  //In this matrix, 0 means empty, 1 means occupied by player1
  //and 2 means occupied by player2
  var matrix = Array.ofDim[Int](3, 3);
}

