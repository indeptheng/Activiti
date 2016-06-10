package org.activiti.document;

import com.activiti.content.storage.api.ContentObject;
import com.activiti.content.storage.fs.FileSystemContentStorage;
import com.activiti.domain.runtime.RelatedContent;
import com.activiti.service.runtime.RelatedContentService;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.Expression;
import org.activiti.engine.impl.bpmn.behavior.AbstractBpmnActivityBehavior;
import org.activiti.engine.impl.util.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.domain.Page;

import java.io.*;
import java.util.List;

/**
 * @author Jonathan Mulieri
 */
public class DocumentActivityBehavior extends AbstractBpmnActivityBehavior {
  private static final long serialVersionUID = 1L;

  private static final Logger LOG         = LoggerFactory.getLogger(DocumentActivityBehavior.class);
  private static final String SCRIPT_DIR  = "document-generator";
  private static final String SCRIPT_NAME = "docGenerator.js";

  protected Expression inputfile;
  protected Expression outputfile;

  private AnnotationConfigApplicationContext applicationContext;
  private RelatedContentService relatedContentService;
  private FileSystemContentStorage contentStorage;
  private String scriptPath;
  private String nodejsPath = "nodejs";

  public DocumentActivityBehavior() {
    try {
      Class<?> theClass = Class.forName("com.activiti.conf.ApplicationConfiguration");
      applicationContext = new AnnotationConfigApplicationContext(theClass);
      relatedContentService = applicationContext.getBean(RelatedContentService.class);
      contentStorage = (FileSystemContentStorage) relatedContentService.getContentStorage();
      copyScriptToLocalFilesystem();
      setNodeJSPath();
    } catch (ClassNotFoundException e) {
      LOG.error("Could not load ApplicationConfiguration {}", e);
    }
  }

  @Override
  public void execute(DelegateExecution execution) {
    // Get variable names for input(template) and output(generated) files
    String inputFileField = inputfile.getValue(execution).toString();
    String outputFileField = outputfile.getValue(execution).toString();

    // Find template file stored as RelatedContent, query by field and process_id
    Page<RelatedContent> page = relatedContentService.getFieldContentForProcessInstance(
        execution.getProcessInstanceId(), inputFileField, 1, 0);

    // If there are multiple RelatedContent matching field and process_id, use first one
    List<RelatedContent> contentList = page.getContent();
    if (contentList.size() > 0) {
      RelatedContent content = contentList.get(0);
      ContentObject contentObject = contentStorage.getContentObject(content.getContentStoreId());
      File templateFile = createTemplateFile(contentObject.getContent());

      // Create JSON object to pass process variables into templating engine
      JSONObject json = new JSONObject(execution.getVariables());
      String outputFilePath = templateFile.getPath() + ".out";
      json.put("inputFile", templateFile.getPath());
      json.put("outputFile", outputFilePath);

      // Run document templating engine
      if (runDocumentTemplater(json)) {
        // Store generated document as RelatedContent for use by other process activities
        createRelatedContentForGeneratedOutput(execution, content, outputFileField, outputFilePath);
      }
    }
  }

  private boolean runDocumentTemplater(JSONObject json) {
    boolean success;
    try {
      LOG.info("Running nodejs docGenerator.js");
      LOG.info(json.toString());
      String cmd = "echo '"+ json.toString() + "' | " + nodejsPath + " " + scriptPath;
      ShellCommandRunner.Result result = ShellCommandRunner.shellOut(cmd);
      success = true;
      StringBuilder builder = new StringBuilder();
      String line;
      while ((line = result.inputReader.readLine()) != null)
        builder.append(line+"\n");
      while ((line = result.errorReader.readLine()) != null) {
        success = false;
        builder.append(line + "\n");
      }
      LOG.info("cmd output: " + builder.toString());
    } catch (Exception e) {
      success = false;
      LOG.error("Something went wrong! " + e.getMessage());
    }

    return success;
  }

  private void createRelatedContentForGeneratedOutput(DelegateExecution execution, RelatedContent content, String field, String filePath) {
    try {
      File generatedFile = new File(filePath);
      FileInputStream generatedFileStream = new FileInputStream(generatedFile);
      relatedContentService.createRelatedContent(
          content.getCreatedBy(),
          content.getName(),
          null,
          null,
          null,
          execution.getProcessInstanceId(),
          field,
          content.getMimeType(),
          generatedFileStream,
          generatedFile.length()
      );
    } catch (FileNotFoundException e) {
      LOG.error("Could not create related content for document generator output file: {}", e);
    }
  }

  private File createTemplateFile(InputStream content) {
    File templateFile = null;
    try {
      templateFile = File.createTempFile("document-activity-behavior", ".docx");
      OutputStream os = new FileOutputStream(templateFile);
      IOUtils.copy(content, os);
      os.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return templateFile;
  }

  private void copyScriptToLocalFilesystem() {
    ensureDirectoryExists(SCRIPT_DIR);
    copyNodeModulesToLocalFilesystem();
    scriptPath = copyResourceToLocalFilesystem(SCRIPT_NAME, SCRIPT_DIR);
  }

  private void copyNodeModulesToLocalFilesystem() {
    File nodeModulesDir = new File(SCRIPT_DIR+File.separator+"node_modules");
    if (!nodeModulesDir.exists()) {
      String zipFile = copyResourceToLocalFilesystem("node_modules.zip", SCRIPT_DIR);
      File scriptDir = new File(SCRIPT_DIR);
      String cmd = "unzip " + zipFile + " -d " + scriptDir.getAbsolutePath();
      try {
        LOG.info("Unzipping node_modules.zip with command: {}", cmd);
        ShellCommandRunner.Result result = ShellCommandRunner.shellOut(cmd);
        LOG.info(result.getOutput());
      } catch (Exception e) {
        LOG.error("Something went wrong {}", e);
      }
    } else {
      LOG.info("node_modules directory exists, no need to extract zip archive");
    }
  }

  private String copyResourceToLocalFilesystem(String resourceName, String path) {
    String absolutePath = null;
    try {
      ClassLoader classLoader = getClass().getClassLoader();
      File destFile = new File(path+File.separator+resourceName);
      absolutePath = destFile.getAbsolutePath();
      InputStream is = classLoader.getResourceAsStream(resourceName);
      OutputStream os = new FileOutputStream(destFile);
      IOUtils.copy(is, os);
      is.close();
      os.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return absolutePath;
  }

  private void ensureDirectoryExists(String path) {
    File dir = new File(path);
    if (!dir.exists()) {
      dir.mkdir();
    }
  }

  private void setNodeJSPath() {
    String path = applicationContext.getEnvironment().getProperty("document.nodejs.path");
    if (path != null) {
      nodejsPath = path;
    }
  }
}
