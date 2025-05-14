package com.vision.insights.controller

import com.vision.insights.model.User
import com.vision.insights.service.UserService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/users")
class UserController(private val userService: UserService) {

    @GetMapping
    fun getAllUsers(): List<User> = userService.getAllUsers()

    @GetMapping("/{id}")
    fun getUserById(@PathVariable id: String): User? = userService.getUserById(id)

    @PostMapping
    fun addUser(@RequestBody user: User): User = userService.addUser(user)

    @PutMapping("/{id}")
    fun updateUser(@PathVariable id: String, @RequestBody updatedUser: User): User? =
        userService.updateUser(id, updatedUser)
}
