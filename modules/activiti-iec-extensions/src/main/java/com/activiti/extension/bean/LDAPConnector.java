package com.activiti.extension.bean;

import com.activiti.domain.idm.User;
import com.activiti.repository.idm.UserRepository;
import com.activiti.service.api.UserService;
import org.apache.directory.api.ldap.model.cursor.CursorException;
import org.apache.directory.api.ldap.model.cursor.EntryCursor;
import org.apache.directory.api.ldap.model.entry.Attribute;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.exception.LdapInvalidAttributeValueException;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.ldap.client.api.LdapConnection;
import org.apache.directory.ldap.client.api.LdapConnectionConfig;
import org.apache.directory.ldap.client.api.LdapNetworkConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

/**
 * @author Jonathan Mulieri
 */
@EnableScheduling
@EnableAsync
@Component("ldapConnector")
public class LDAPConnector {
  private static final Logger LOG = LoggerFactory.getLogger(LDAPConnector.class);

  private static final String UID            = "uid";
  private static final String COMMON_NAME    = "cn";
  private static final String FIRST_NAME     = "first_name";
  private static final String LAST_NAME      = "last_name";
  private static final String EMAIL          = "mail";
  private static final String ADMIN_USERNAME = "admin";

  private static final List<String> LDAP_FIELDS             = Arrays.asList(UID, COMMON_NAME, EMAIL);
  private static final String LDAP_ACTIVE                   = "ldap.authentication.active";
  private static final String LDAP_ACTIVE_DEFAULT           = "false";
  private static final String LDAP_PROVIDER_URL             = "ldap.authentication.java.naming.provider.url";
  private static final String LDAP_PROVIDER_URL_DEFAULT     = "ldap://localhost:389";
  private static final int    LDAP_PORT_DEFAULT             = 389;
  private static final String LDAP_USERNAME_FORMAT          = "ldap.authentication.userNameFormat";
  private static final String LDAP_USERNAME_FORMAT_DEFAULT  = "uid=%s,ou=User,dc=example,dc=com";
  private static final String LDAP_ADMIN_OBJECTNAME         = "ldap.authentication.adminObjectName";
  private static final String LDAP_ADMIN_OBJECTNAME_DEFAULT = "uid=admin,ou=system";
  private static final String LDAP_PRINCIPAL                = "ldap.synchronization.java.naming.security.principal";
  private static final String LDAP_PRINCIPAL_DEFAULT        = "uid=admin,ou=system";
  private static final String LDAP_CREDENTIALS              = "ldap.synchronization.java.naming.security.credentials";
  private static final String LDAP_CREDENTIALS_DEFAULT      = "secret";
  private static final String LDAP_USER_SEARCH_BASE         = "ldap.synchronization.userSearchBase";
  private static final String LDAP_USER_SEARCH_BASE_DEFAULT = "ou=User,dc=example,dc=com";

  @Autowired
  private Environment environment;

  @Autowired
  protected UserService userService;

  @Autowired
  protected UserRepository userRepository;

  public boolean isValidPassword(String username, String password) {
    String userObjectName;
    if (ADMIN_USERNAME.equals(username)) {
      userObjectName = getAdminObjectName();
    } else {
      userObjectName = getUserObjectName(username);
    }
    LdapConnection connection = createConnection(userObjectName, password);
    boolean authenticated = connection.isAuthenticated();
    closeConnection(connection);
    return authenticated;
  }

  @Scheduled(initialDelay = 20000, fixedRate = 3600000)
  private void syncUsers() {
    if (isLDAPActive()) {
      LOG.info("Syncing LDAP Users {} {}", getProviderUrl(), getUserSearchBase());
      LdapConnection connection = createConnection(getPrincipal(), getCredentials());
      if (connection.isAuthenticated()) {
        try {
          EntryCursor cursor = connection.search(getUserSearchBase(), "(objectClass=inetOrgPerson)", SearchScope.ONELEVEL);
          while (cursor.next()) {
            Entry entry = cursor.get();
            Map<String, String> attributes = getUserAttributes(entry.getAttributes());
            LOG.info("Found user with attributes {}", attributes);
            createUserIfNeeded(attributes);
          }
        } catch (LdapException e) {
          LOG.error("Exception while querying for LDAP users", e);
        } catch (CursorException e) {
          LOG.error("Exception while iterating on LDAP users", e);
        }
      }
      closeConnection(connection);
    }
  }

  private void createUserIfNeeded(Map<String, String> attributes) {
    String email      = attributes.get(EMAIL);
    String firstName  = attributes.get(FIRST_NAME);
    String lastName   = attributes.get(LAST_NAME);
    String externalId = attributes.get(UID);
    if (email != null) {
      User user = userRepository.findByEmail(email);
      if (user == null) {
        user = userService.createNewUser(email, firstName, lastName, " ", ""); // pwd must not be empty, company is empty
        user.setExternalId(externalId);
        userRepository.save(user);
        LOG.info("LDAP user sync: created user {} - {} - {} {}", email, externalId, firstName, lastName);
      } else if (user.getExternalId() == null) {
        user.setExternalId(externalId);
        userRepository.save(user);
        LOG.info("LDAP updated external_id={} for user={}", externalId, email);
      } else if (user.getPassword() == null || user.getPassword().isEmpty()) {
        user.setPassword(" "); // pwd cannot be empty
        userRepository.save(user);
        LOG.info("LDAP updated to non-empty pwd for user={}", email);
      } else {
        LOG.info("LDAP user already existed in Activiti... doing nothing");
      }
    } else {
      LOG.info("LDAP no email present on user {}... doing nothing", externalId);
    }
  }

  private Map<String, String> getUserAttributes(Collection<Attribute> attributes) {
    Map<String, String> attributesMap = new HashMap<String, String>();
    for (Attribute attribute : attributes) {
      try {
        String key = attribute.getId();
        if (LDAP_FIELDS.contains(key)) {
          String value = attribute.getString();
          if (key.equals(COMMON_NAME)) {
            String[] parts = value.split("\\s+");
            attributesMap.put(FIRST_NAME, parts[0]);
            if (parts.length > 1) {
              attributesMap.put(LAST_NAME, parts[1]);
            }
          } else {
            attributesMap.put(key, value);
          }
        }
      } catch (LdapInvalidAttributeValueException e) {
        LOG.error("Invalid LDAP user attribute", e);
      }
    }
    return attributesMap;
  }

  private boolean isLDAPActive() {
    return Boolean.parseBoolean(environment.getProperty(LDAP_ACTIVE, LDAP_ACTIVE_DEFAULT));
  }

  private String getUserObjectName(String username) {
    return String.format(getUserFormat(), username);
  }

  private String getUserFormat() {
    return environment.getProperty(LDAP_USERNAME_FORMAT, LDAP_USERNAME_FORMAT_DEFAULT);
  }

  private String getAdminObjectName() {
    return environment.getProperty(LDAP_ADMIN_OBJECTNAME, LDAP_ADMIN_OBJECTNAME_DEFAULT);
  }

  private String getPrincipal() {
    return environment.getProperty(LDAP_PRINCIPAL, LDAP_PRINCIPAL_DEFAULT);
  }

  private String getCredentials() {
    return environment.getProperty(LDAP_CREDENTIALS, LDAP_CREDENTIALS_DEFAULT);
  }

  private String getHost() {
    String[] url = getParsedProviderUrl();
    return url[0];
  }

  private int getPort() {
    String[] url = getParsedProviderUrl();
    int port = LDAP_PORT_DEFAULT;
    if (url.length > 1) {
      port = Integer.parseInt(url[1]);
    }
    return port;
  }

  private String[] getParsedProviderUrl() {
    String url = getProviderUrl();
    if (url.startsWith("ldap://")) {
      url = url.substring(7);
    }
    return url.split(":");
  }

  private String getProviderUrl() {
    return environment.getProperty(LDAP_PROVIDER_URL, LDAP_PROVIDER_URL_DEFAULT);
  }

  public String getUserSearchBase() {
    return environment.getProperty(LDAP_USER_SEARCH_BASE, LDAP_USER_SEARCH_BASE_DEFAULT);
  }

  private LdapConnection createConnection(String objectName, String password) {
    LdapConnectionConfig config = new LdapConnectionConfig();
    config.setLdapHost(getHost());
    config.setLdapPort(getPort());
    config.setName(objectName);
    config.setCredentials(password);
    LdapConnection connection = new LdapNetworkConnection(config);
    try {
      connection.bind();
    } catch (LdapException e) {
      LOG.error("Exception while binding to LDAP connection", e);
    }
    return connection;
  }

  private void closeConnection(LdapConnection connection) {
    try {
      if (connection.isConnected()) {
        connection.close();
      }
    } catch (IOException e) {
      LOG.error("Exception while closing LDAP connection", e);
    }
  }
}
