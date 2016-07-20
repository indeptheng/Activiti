package com.activiti.security;

import com.activiti.extension.bean.LDAPConnector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * @author Jonathan Mulieri
 */
public class LDAPPasswordEncoder implements PasswordEncoder {

  @Autowired
  private LDAPConnector ldapConnector;

  public String username;

  public String encode(CharSequence rawPassword) {
    return rawPassword.toString();
  }

  public boolean matches(CharSequence rawPassword, String encodedPassword) {
    boolean match = false;
    if (username != null) {
      match = ldapConnector.isValidPassword(username, rawPassword.toString());
    }
    return match;
  }
}
