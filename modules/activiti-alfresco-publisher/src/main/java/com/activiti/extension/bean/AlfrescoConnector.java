package com.activiti.extension.bean;

import org.apache.chemistry.opencmis.client.api.*;
import org.apache.chemistry.opencmis.client.runtime.SessionFactoryImpl;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.SessionParameter;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.definitions.PropertyDefinition;
import org.apache.chemistry.opencmis.commons.enums.BindingType;
import org.apache.chemistry.opencmis.commons.enums.VersioningState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Jonathan Mulieri
 */
public class AlfrescoConnector {

  private static final Logger LOG = LoggerFactory.getLogger(AlfrescoConnector.class);

  private Session session;
  private Map<String, String> credentials;
  
  public AlfrescoConnector(Map<String, String> credentials) {
    this.credentials = credentials;
    connect();
  }

  public Folder getFolder(String folderPath) {
    ObjectType type                   = session.getTypeDefinition("cmis:folder");
    PropertyDefinition<?> namePropDef = type.getPropertyDefinitions().get(PropertyIds.PATH);
    PropertyDefinition<?> idPropDef   = type.getPropertyDefinitions().get(PropertyIds.OBJECT_ID);
    String nameQueryName              = namePropDef.getQueryName();
    String idQueryName                = idPropDef.getQueryName();
    String folderName                 = getFolderName(folderPath);
    Folder folder                     = null;

    ItemIterable<QueryResult> results = session.query("SELECT * FROM cmis:folder WHERE cmis:name='" + folderName + "'", false);

    for (QueryResult qResult : results) {
      String path = qResult.getPropertyValueByQueryName(nameQueryName);
      if (folderPath.equals(path)) {
        String objectId = qResult.getPropertyValueByQueryName(idQueryName);
        folder = (Folder) session.getObject(session.createObjectId(objectId));
        break;
      }
    }
    return folder;
  }

  public Document createDocument(Folder folder, String filename, String mimeType, InputStream in) throws Exception {
    Map<String, Object> docProps = new HashMap<String, Object>();
    docProps.put(PropertyIds.OBJECT_TYPE_ID, "cmis:document");
    String availFilename = nextAvailableFilename(folder, filename);
    LOG.info("Writing file: '{}/{}'", folder.getPath(), availFilename);
    docProps.put(PropertyIds.NAME, availFilename);

    ContentStream contentStream = session.getObjectFactory().createContentStream(availFilename, -1, mimeType, in);
    return folder.createDocument(docProps, contentStream, VersioningState.MAJOR);
  }

  private String nextAvailableFilename(Folder folder, String filename) {
    int dupDocNum = 1;
    String availFilename = filename;
    while (folderContainsFile(folder, availFilename)) {
      LOG.info("{} already exists in {}", availFilename, folder.getName());
      availFilename = filename.replaceAll("(\\.[^\\.]+$)", String.format(" (%d)$1", dupDocNum++));
    }
    return availFilename;
  }

  private boolean folderContainsFile(Folder folder, String filename) {
    for (CmisObject object : folder.getChildren()) {
      if (filename.equals(object.getName())) {
        return true;
      }
    }
    return false;
  }

  private void connect() {
    // default factory implementation
    SessionFactory factory = SessionFactoryImpl.newInstance();
    Map<String, String> parameter = new HashMap<String, String>();

    // user credentials
    parameter.put(SessionParameter.USER,     credentials.get("user"));
    parameter.put(SessionParameter.PASSWORD, credentials.get("password"));

    // connection settings
    parameter.put(SessionParameter.ATOMPUB_URL,  credentials.get("url"));
    parameter.put(SessionParameter.BINDING_TYPE, BindingType.ATOMPUB.value());

    // initialize session
    List<Repository> repositories = factory.getRepositories(parameter);
    session = repositories.get(0).createSession();
  }

  private static String getFolderName(String folderPath) {
    String[] parts = folderPath.split("/");
    return parts[parts.length-1];
  }
}
