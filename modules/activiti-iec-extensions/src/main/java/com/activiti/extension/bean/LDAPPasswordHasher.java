package com.activiti.extension.bean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * @author Jonathan Mulieri
 */
@Component("ldapPasswordHasher")
public class LDAPPasswordHasher {
  private static final Logger LOG = LoggerFactory.getLogger(LDAPPasswordHasher.class);
  private static final String ALGORITHM = "AES";
  private static final String DEFAULT_SECRET = "LDAP_SECRET";

  @Autowired
  private Environment environment;

  public String encryptPassword(String password) {
    String encryptedVal = null;
    try {
      final Key key = generateKey();
      final Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.ENCRYPT_MODE, key);
      final byte[] encodedVal = cipher.doFinal(password.getBytes());
      encryptedVal = new BASE64Encoder().encode(encodedVal);
    } catch(Exception e) {
      LOG.error("Unable to encrypt password", e);
    }

    return encryptedVal;
  }

  public String decryptPassword(String passwordHash) {
    String decryptedVal = null;
    try {
      final Key key = generateKey();
      final Cipher c = Cipher.getInstance(ALGORITHM);
      c.init(Cipher.DECRYPT_MODE, key);
      final byte[] decorVal = new BASE64Decoder().decodeBuffer(passwordHash);
      final byte[] decValue = c.doFinal(decorVal);
      decryptedVal = new String(decValue);
    } catch(Exception e) {
      LOG.error("Unable to decrypt password", e);
    }

    return decryptedVal;
  }

  private String getLDAPSecret() {
    return environment.getProperty("ldap.synchronization.java.naming.security.credentials", String.class, DEFAULT_SECRET);
  }

  private Key generateKey() throws Exception {
    byte[] key = getLDAPSecret().getBytes("UTF-8");
    MessageDigest sha = MessageDigest.getInstance("SHA-1");
    key = sha.digest(key);
    key = Arrays.copyOf(key, 16); // first 128 bits
    final Key outputKey = new SecretKeySpec(key, ALGORITHM);
    return outputKey;
  }
}
