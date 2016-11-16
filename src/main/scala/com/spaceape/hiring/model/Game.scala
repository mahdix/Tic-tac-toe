package com.spaceape.hiring.model


class Game(val gameId: Int, val p1: String, val p2: String) {
  var id: Int = gameId;   
  var p1Id: String = p1;
  var p2Id: String = p2;

  /* These fields have fixed value upon game creation */
  var activePlayer: Int = 1;
  var isGameOver: Boolean = false;
  var winnerIndex: Int = 0;  /*0 means there is no winner */
  var matrix = Array.ofDim[Int](3, 3);
}

