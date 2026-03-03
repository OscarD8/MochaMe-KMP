package com.mochame.app.core


// CATEGORIES
class DomainAlreadyExistsException(val name: String) : Exception("Domain '$name' already exists.")
class DomainInUseException(val count: Int) : Exception()
class DomainNotFoundException(val id: String) : Exception("Domain with ID $id not found.")

// TOPICS
class TopicAlreadyExistsException(val name: String) : Exception("Topic '$name' already exists.")
class TopicInUseException(val count: Int) : Exception()
class TopicNotFoundException(val id: String) : Exception("Topic with ID $id not found.")
