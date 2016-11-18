package com.spaceape.hiring.model

case class GameState(winnerId: Option[String], gameOver: Boolean, 
  moveCount: Int, activePlayerIndex: Int)
