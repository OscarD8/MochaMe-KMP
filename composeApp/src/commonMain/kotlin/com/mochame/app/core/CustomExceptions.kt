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
class SpaceInUseException(spaceId: String, momentCount: Int) :
    Exception("Cannot delete space: $spaceId. $momentCount moments are still anchored here.")
class SpaceAlreadyExistsException(val name: String) :
    Exception("A space with the name '$name' already exists.")