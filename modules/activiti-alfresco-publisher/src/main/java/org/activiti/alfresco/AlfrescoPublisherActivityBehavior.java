package org.activiti.alfresco;

import com.activiti.content.storage.api.ContentObject;
import com.activiti.content.storage.fs.FileSystemContentStorage;
import com.activiti.domain.runtime.RelatedContent;
import com.activiti.service.runtime.RelatedContentService;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.Expression;
import org.activiti.engine.impl.bpmn.behavior.AbstractBpmnActivityBehavior;
import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Jonathan Mulieri
 */
public class AlfrescoPublisherActivityBehavior extends AbstractBpmnActivityBehavior {
  private static final long serialVersionUID            = 1L;
  private static final Logger LOG                       = LoggerFactory.getLogger(AlfrescoPublisherActivityBehavior.class);
  private static final String ALL_CONTENT               = "ALL_CONTENT";
  private static final String ALFRESCO_DESTINATION_PATH = "alfresco_destination_path";
  protected static final String ALFRESCO_FILE_URL       = "alfresco_file_url";

  protected Expression inputfiles;
  protected Expression destinationdir;

  private AnnotationConfigApplicationContext applicationContext;
  private RelatedContentService relatedContentService;
  private FileSystemContentStorage contentStorage;
  private Map<String, String> alfrescoCredentials;

  public AlfrescoPublisherActivityBehavior() {
    try {
      Class<?> theClass = Class.forName("com.activiti.conf.ApplicationConfiguration");
      applicationContext = new AnnotationConfigApplicationContext(theClass);
      relatedContentService = applicationContext.getBean(RelatedContentService.class);
      contentStorage = (FileSystemContentStorage) relatedContentService.getContentStorage();
      setAlfrescoCredentials(applicationContext.getEnvironment());
    } catch (ClassNotFoundException e) {
      LOG.error("Could not load ApplicationConfiguration", e);
    }
  }

  @Override
  public void execute(DelegateExecution execution) {
    // Get variable names for input files and alfresco destination directory
    String inputFileVariables = inputfiles.getValue(execution).toString();
    String destinationDirPath = getDestinationDir(execution);
    LOG.info("inputfiles="+inputFileVariables);
    LOG.info("destinationdir="+destinationDirPath);

    AlfrescoConnector alfrescoConnector = new AlfrescoConnector(alfrescoCredentials);
    Folder folder = alfrescoConnector.getFolder(destinationDirPath);
    if (folder != null) {
      if (inputFileVariables.equals(ALL_CONTENT)) {
        // TODO - implement ALL_CONTENT for Alfresco publishing
      } else {
        String[] fileVariables = inputFileVariables.split(",");
        for (String inputFile : fileVariables) {
          LOG.info("Looking for content in {}", inputFile);
          // Find template file stored as RelatedContent, query by field and process_id
          Page<RelatedContent> page = relatedContentService.getFieldContentForProcessInstance(
              execution.getProcessInstanceId(), inputFile, 1, 0);

          // If there are multiple RelatedContent matching field and process_id, use first one
          List<RelatedContent> contentList = page.getContent();
          if (contentList.size() > 0) {
            RelatedContent content = contentList.get(0);
            LOG.info("Found content, storing in Alfresco with path {}", destinationDirPath);
            ContentObject contentObject = contentStorage.getContentObject(content.getContentStoreId());
            try {
              Document document = alfrescoConnector.createDocument(folder, content.getName(), content.getMimeType(), contentObject.getContent());
              execution.setVariable(ALFRESCO_FILE_URL, document.getContentUrl());
            } catch (Exception e) {
              LOG.error("Error creating document in Alfresco", e);
            }
          }
        }
      }
      leave(execution);
    } else {
      LOG.error("Could not find folder with path", destinationDirPath);
    }
  }

  private String getDestinationDir(DelegateExecution execution) {
    String destinationDir = null;
    // first try to read destination dir from process variable, then look in task configuration param
    Object destinationDirObj = execution.getVariable(ALFRESCO_DESTINATION_PATH);
    if (destinationDirObj == null) {
      destinationDirObj = destinationdir.getValue(execution);
    }
    if (destinationDirObj != null) {
      destinationDir = destinationDirObj.toString();
    }
    return destinationDir;
  }

  private void setAlfrescoCredentials(ConfigurableEnvironment environment) {
    alfrescoCredentials = new HashMap<String, String>();
    alfrescoCredentials.put("user",     environment.getProperty("alfresco.cmis.user"));
    alfrescoCredentials.put("password", environment.getProperty("alfresco.cmis.password"));
    alfrescoCredentials.put("url",      environment.getProperty("alfresco.cmis.url"));
  }
}