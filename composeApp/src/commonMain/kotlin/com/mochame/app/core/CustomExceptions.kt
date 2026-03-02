package com.mochame.app.core


// CATEGORIES
class CategoryAlreadyExistsException(val name: String) : Exception("Category '$name' already exists.")
class CategoryInUseException(val count: Int) : Exception()
class CategoryNotFoundException(val id: String) : Exception("Category with ID $id not found.")

// TOPICS
class TopicAlreadyExistsException(val name: String) : Exception("Topic '$name' already exists.")
class TopicInUseException(val count: Int) : Exception()
class TopicNotFoundException(val id: String) : Exception("Topic with ID $id not found.")
