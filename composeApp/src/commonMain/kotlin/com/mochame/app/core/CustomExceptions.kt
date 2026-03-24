package com.mochame.app.core


// CATEGORIES
class DomainAlreadyExistsException(val name: String) : Exception("Domain '$name' already exists.")
class DomainInUseException(val count: Int) : Exception()
class DomainNotFoundException(val id: String) : Exception("Domain with ID $id not found.")

// TOPICS
class TopicAlreadyExistsException(val name: String, val id: String) : Exception("Topic '$id/$name' already exists.")
class TopicInUseException(val count: Int) : Exception()
class TopicNotFoundException(val id: String) : Exception("Topic with ID $id not found.")

// SPACES
class SpaceInUseException(val spaceId: String, val momentCount: Int) :
    Exception("Cannot recordDelete space: $spaceId. $momentCount moments are still anchored here.")
class SpaceAlreadyExistsException(val name: String) :
    Exception("A space with the name '$name' already exists.")

class SpaceNotFoundException(val id: String) : Exception("Space with ID $id was not found.")

class BookInUseException(val id: String, val quoteCount: Int) :
    Exception("Cannot recordDelete Book[$id]. It is currently anchoring $quoteCount quotes.")

class AuthorInUseException(
    val authorId: String,
    val bookCount: Int
) : Exception("Cannot recordDelete author $authorId: It still contains $bookCount books.")

// SYNC

// @Serializable?
class SyncInitializationException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)


class HlcParseException(rawString: String) :
    RuntimeException("Failed to parse HLC string: '$rawString'. Data integrity at risk.")