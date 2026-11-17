package com.ledger.security;

import com.ledger.entity.User;
import com.ledger.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final AnalystAccess analystAccess;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                analystAccess.isAnalyst(user.getEmail(), user.getRole()) && !AnalystAccess.ROLE.equals(user.getRole())
                        ? List.of(new SimpleGrantedAuthority(user.getRole()), new SimpleGrantedAuthority(AnalystAccess.ROLE))
                        : List.of(new SimpleGrantedAuthority(user.getRole()))
        );
    }
}