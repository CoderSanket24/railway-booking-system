package com.vit.railway_os.security;

import com.vit.railway_os.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Look for the "Authorization" header in the incoming request
        final String authorizationHeader = request.getHeader("Authorization");

        String username = null;
        String jwt = null;

        // 2. Check if the header contains a Bearer token
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7); // Remove "Bearer " to get just the token
            try {
                username = jwtUtil.extractUsername(jwt);
            } catch (Exception e) {
                System.out.println("[SECURITY] Invalid JWT Token intercepted.");
            }
        }

        // 3. If a token was found and the user isn't already logged into the session
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // 4. Verify the user actually exists in the MySQL database
            if (userRepository.findByUsername(username).isPresent()) {

                // Create a temporary UserDetails object for Spring Security to hold
                UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                        username, "", new ArrayList<>());

                // 5. Cryptographically validate the token
                if (jwtUtil.validateToken(jwt, userDetails)) {

                    // 6. Token is valid! Tell Spring Security to unlock the door for this request.
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        }

        // 7. Pass the request further down the chain (to your Controller)
        filterChain.doFilter(request, response);
    }
}