package com.ibrasoft.lensbridge.security.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.repository.auth.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {
  private final UserRepository userRepository;
  private final AuthorityResolver authorityResolver;

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    User user = userRepository.findByEmail(username)
        .orElseThrow(() -> new UsernameNotFoundException("User Not Found with username: " + username));

      // No collision with lensbridge.model.auth.User
        return org.springframework.security.core.userdetails.User
      .withUsername(user.getEmail())
      .password(user.getPassword())
      .authorities(authorityResolver.resolveAuthorities(user))
      .disabled(!user.isVerified())
      .build();
  }

}
