Noughts Coding Test
===================

The task is to implement the server side of a multiplayer
[noughts and crosses or tic-tac-toe](http://en.wikipedia.org/wiki/Tic-tac-toe) game.  We've provided a skeleton
Dropwizard app that defines the API but lacks an implementation.  You will need to provide an implementation that will be tested using an automated test framework. Please complete at least one of the extra tasks. 

Rules
-----

Two players take turns to place a mark in one of the spaces on a 3x3 grid.  Each player cannot place a mark where either
player has placed one previously.  The first player who places three consecutive marks in a horizontal, vertical or
diagonal row wins the game.  If all of the spaces are taken and no player has succeeded in placing 3 marks in a row
then the game is a draw. 

API
---

Please ensure that your application meets this API.  The NoughtsResource and NoughtsTest classes provided should make it
clear how to achieve this.  Please return appropriate http error codes to enforce both the rules described above and
the restrictions described below.

### Create a game ###

    method                : POST
    url                   : /game?player1Id=<id of player 1>&player2Id=<id of player 2>
    example response body : "<id of the new game>"

The client will provide the ids of the players. Players can create games against multiple different players concurrently
but an appropriate error code should be returned if a player tries to create a  new game against a player that they
currently have an unfinished game against.  The response should be a json string containing an id that identifies the
new game.

### Make a move ###

    method                : PUT
    url                   : /game/<id of the game>
    example request body  : {"playerId": "<id of player making the move>", "x": <column index to make a mark in>, , "y": <row index to make a mark in>}
    example response body : <empty>

The <id of the game> will be the id of a game previously created via a call the *Create a game* endpoint.  The player id
will be the id of the player making the move.  An error code should be returned if a player makes a move out of turn.
Assume that player 1 will always go first.

### Get the game state ###

    method                                   : GET
    url                                      : /game/<id of the game>
    example response body (game in progress) : {"winnerId": null, "gameOver": false}
    example response body (win)              : {"winnerId": "<id of the winning player>", "gameOver": true}
    example response body (draw)             : {"winnerId": null, "gameOver": true}

The <id of the game> will be the id of a game previously created via a call the *Create a game* endpoint.  If the game
is still in progress the winnerId should be null and gameOver should be false.  If a player has won then then the
winnerId should be the the id of that player and gameOver should be true. If the game is complete and a draw then the
winnerId should be null and gameOver should be true.

For bonus points:

### Extend the tests ###

Extend the NoughtsTest class to cover more of the rules and restrictions described above.


Extra Tasks
-----------

### Concurrency handling ###

Ensure that your application runs efficiently and without error despite serving multiple players playing concurrently.
Assume that players will try and break the game by making concurrent requests against the same player.

### Persistence ###

Store the state of the games in an external data store of your choice.  Aim to handle 1000s of concurrent games on a
modern mid-range laptop.

### Leaderboard ###

Add a leaderboard to the game, allowing clients to get the top 10 player ids and scores. Players should get 1 point per
game won and no points for draws.

Candidate Comments
------------------
###Work done###
- This is the first piece of code I have ever written in Scala language, so maybe some part of it is not idiomatic Scala.
- The main task + test extension + extra tasks are done. The code is compiling and tests are all passing.
- I have added 3 tags to the repository to separate changes: 
-   v1 tag - basic task + bonus point done
-   v2 tag - redis support added
-   v3 tag - leaderBoard is added
-   latest commit - Added some changes and comments for concurrency task
- I have also enhanced gameState API call to return number of moves made in the game and currently active player
- For the concurrency task, I have just added a simple synchronization lock + made some further commets in the code. 
    There are more possibilities for enhancements here as discussed in the comments.
- For persistence, I chose Redis as an in-memory storage. You can achieve real persistence by configuring Redis to 
    save data to the disk.  The reason I chose Redis was my previous experience with it and also it's high performance.
- Before using Redis, I store everything on an in-process HashMap
- For the Leaderboard task, I have also used Redis with scoring to keep track of scores of all players, update them and 
    get top 10 players.
- I could not dedicate a good chunk of time to this project. That's why it took 4 days.
- A stress test is added which will create 500 games. I did not increase this number because test execution would 
    take too long. But generally these tests are done in external tools like JMeter.
- I have added Jedis dependency to  pom.xml.

###Suggestions###
- Maybe it's a good idea to implement some expiration for games, so when no move is done in X minutes, we will just 
    expire them (although I have not implemented this).
- It is a best practice to use some type of authentication to prevent DoS attacks on publicly available API.
- As I previously mentioned, further work can be done for concurrency to increase performance and reduce locking periods.
- I tried to break 'NoughtsResource.scala' into two parts (GameUtils). But still this module can be more simplified with 
    less code and fewer methods.

###How to build and run?###
- To compile the project run 'mvn compile' on the root directory.
- To create a fat jar of the project: 'mvn package'
- To execute fat jar (start the web server and process requests): 'java -jar target/noughts-coding-test-0.0.1-SNAPSHOT-bin.jar server test.yml' 
- To install Redis you just need to do default installation (on localhost and with default configuration), according to redis.io
- To run tests: 'mvn test' (Before sending any request, make sure redis is up and running)
- You can use curl to send requests. Make sure the server is up and running and in another terminal run these commands:
  Create a new game: 
      `curl -X POST "http://localhost:8080/game?player1Id=a&player2Id=b"` (between players 'a' and 'b')
  Get game status: 
      `curl -i -H "Accept: application/json" -H "Content-Type: application/json" http://localhost:8080/game/0` (0 is game id)
  Make a move: 
      `curl  -H "Content-Type: application/json" -H 'Accept: application/json' -X PUT -d '{"playerId":"a", "x":0, "y":0}' http://localhost:8080/game/0`





