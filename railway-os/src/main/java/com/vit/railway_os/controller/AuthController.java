package com.vit.railway_os.controller;

import com.vit.railway_os.model.User;
import com.vit.railway_os.repository.UserRepository;
import com.vit.railway_os.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/register")
    public String register(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        String password = payload.get("password");

        if (userRepository.findByUsername(username).isPresent()) {
            return "FAILED: User already exists!";
        }

        // Hash the password BEFORE saving it to the MySQL database
        User newUser = new User(username, passwordEncoder.encode(password));
        userRepository.save(newUser);

        return "SUCCESS: User " + username + " registered successfully!";
    }

    @PostMapping("/login")
    public String login(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        String password = payload.get("password");

        Optional<User> userOpt = userRepository.findByUsername(username);

        // Check if user exists AND if the raw password matches the hashed password in the DB
        if (userOpt.isPresent() && passwordEncoder.matches(password, userOpt.get().getPassword())) {

            // Success! Generate the digital ID card (JWT)
            String token = jwtUtil.generateToken(username);
            System.out.println("[AUTH] User " + username + " logged in. JWT Generated.");
            return token;
        }

        return "FAILED: Invalid username or password.";
    }
}