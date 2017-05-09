# Real world sample app backend implementation tester

This program goal is to test that a backend implementation of a real world sample app works as expected,
by complying with the API spec.

## Usage

    java -jar realworld-server-tester URL

exemple:

    java -jar realworld-server-tester http://localhost:8080

## Demo

[![asciicast](https://asciinema.org/a/6whqohoubgyf4wfpsfqpv09vl.png)](https://asciinema.org/a/6whqohoubgyf4wfpsfqpv09vl)

## Building

You'll need Java and leiningen to build the app.

    cd realworld-server-tester
    lein uberjar
    java -jar target/realworld-server-tester.jar URL

## TODO

- [X] Login
  - [X] correct login
  - [X] invalid email
  - [X] missing email
  - [X] invalid password
  - [X] missing password
- [X] Register
  - [X] correct register
  - [X] invalid email
  - [X] missing email
  - [X] already taken email
  - [X] invalid username
  - [X] missing username
  - [X] already taken username
  - [X] invalid password
  - [X] missing password
- [X] Get Current User
  - [X] correct call
  - [X] invalid token
- [X] Update User
  - [X] correct call
  - [X] invalid token
  - [X] invalid email
  - [X] already taken email
  - [X] invalid username
  - [X] already taken username
  - [X] invalid password
- [X] Get Profile
  - [X] correct call
  - [X] unknown username
- [X] Follow User
  - [X] correct call
  - [X] invalid token
- [X] Unfollow User
  - [X] correct call
  - [X] invalid token
- [X] List Articles
  - [X] no filters
  - [X] filter by tag
  - [X] filter by author
  - [ ] filter by favorited
  - [X] limit results
- [X] Feed Articles
  - [X] correct call
  - [X] correct call with limited results
  - [X] invalid token
- [X] Get Article
  - [X] correct call
  - [X] unknown article
  - [X] createdAt is iso8601
- [X] Create Article
  - [X] correct call
  - [X] invalid token
  - [X] missing title
  – [X] blank title
  - [X] already taken title (generates different slug)
  – [X] missing description
  – [X] blank description
  - [X] missing body
  – [X] blank body
- [X] Update Article
  - [X] correct call
  - [X] invalid token
  – [X] blank title
  - [X] already taken title (generates different slug)
  – [X] blank description
  - [X] blank body
  - [X] can't update article of another author
- [X] Delete Article
  - [X] correct call
  - [X] invalid token
  - [X] unknown article
  - [X] can't delete article of another author
- [X] Add Comments to an Article
  - [X] correct call
  - [X] invalid token
  - [X] missing body
  - [X] blank body
- [X] Get Comments from an Article
- [X] Delete Comment
  - [X] correct call
  - [X] invalid token
  - [X] can't delete comment of another user
- [X] Favorite Article
  - [X] correct call
  - [X] invalid token
- [X] Unfavorite Article
  - [X] correct call
  - [X] invalid token
- [X] Get Tags


## License

MIT

## Author

Alexandre Grison