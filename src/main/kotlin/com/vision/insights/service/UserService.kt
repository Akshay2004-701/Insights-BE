package com.vision.insights.service

import com.vision.insights.model.User
import com.vision.insights.repository.UserRepo
import org.springframework.stereotype.Service

@Service
class UserService(private val userRepository: UserRepo) {

    fun getAllUsers(): List<User> = userRepository.findAll()

    fun getUserById(id: String): User? = userRepository.findById(id).orElse(null)

    fun addUser(user: User): User = userRepository.save(user)

    fun updateUser(id: String, updatedUser: User): User? {
        return if (userRepository.existsById(id)) {
            userRepository.save(updatedUser)
        } else null
    }
}