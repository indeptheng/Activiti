package com.activiti.extension.bean;

import com.activiti.content.storage.api.ContentObject;
import com.activiti.content.storage.fs.FileSystemContentStorage;
import com.activiti.domain.runtime.RelatedContent;
import com.activiti.service.api.UserCache;
import com.activiti.service.runtime.RelatedContentService;
import com.aspose.words.*;
import org.activiti.engine.ActivitiException;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.Expression;
import org.activiti.engine.delegate.JavaDelegate;
import org.activiti.engine.impl.juel.ObjectValueExpression;
import org.activiti.engine.impl.persistence.entity.VariableInstance;
import org.activiti.engine.impl.util.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component("documentActivityBehavior")
public class DocumentActivityBehavior implements JavaDelegate {

  private static final long serialVersionUID = 1L;

  private static final Logger LOG          = LoggerFactory.getLogger(DocumentActivityBehavior.class);
  private static final String SCRIPT_DIR   = "document-generator";
  private static final String SCRIPT_NAME  = "docGenerator.js";
  private static final String LICENSE_FILE = "Aspose.Words.lic";

  protected Expression inputfile;
  protected Expression outputfile;
  protected Expression sectionbreaks;

  private String scriptPath;
  private String nodejsPath = "nodejs";
  private Boolean setupComplete = false;

  @Autowired
  private RelatedContentService relatedContentService;

  @Autowired
  private Environment environment;

  @Autowired
  private UserCache userCache;

  public DocumentActivityBehavior() { }

  @Override
  public void execute(DelegateExecution execution) {
    setup();
    FileSystemContentStorage contentStorage = (FileSystemContentStorage) relatedContentService.getContentStorage();

    // Get variable names for input(template) and output(generated) files
    String inputFileField = inputfile.getValue(execution).toString();
    String outputFileField = outputfile.getValue(execution).toString();
    Set<String> omitSectionBreaks = getOmitSectionBreakFiles(execution);
    try {
      if (inputFileField != null && !inputFileField.isEmpty()) {
        Document document = null;
        RelatedContent originalContent = null;
        for (RelatedContent content : getInputFiles(execution, inputFileField)) {
          ContentObject contentObject = contentStorage.getContentObject(content.getContentStoreId());
          if (document == null) {
            originalContent = content;
            document = new Document(contentObject.getContent());
          } else {
            document.appendDocument(new Document(contentObject.getContent()), ImportFormatMode.KEEP_SOURCE_FORMATTING);
            if (omitSectionBreaks.contains(content.getField())) {
              int count = document.getSections().getCount();
              if (count > 1) {
                document.getSections().get(count - 2).appendContent(document.getLastSection());
                document.getLastSection().remove();
              }
            }
          }
        }
        if (document != null) {
          MailMerge merger = document.getMailMerge();
          merger.setUseNonMergeFields(true);
          List<String> keys = getProcessVariableKeys(execution);
          List<Object> vals = getProcessVariableVals(execution, keys);
          merger.execute(keys.toArray(new String[]{}), vals.toArray(new Object[]{}));
          File outputFile = getOutputFile();
          OutputStream os = new FileOutputStream(outputFile);
          document.save(os, SaveOptions.createSaveOptions(outputFile.getName()));
          os.close();
          // Store generated document as RelatedContent for use by other process activities
          createRelatedContentForGeneratedOutput(execution, originalContent, outputFileField, outputFile.getPath());
        }
      }
    } catch (Exception e) {
      LOG.error("Error generating document", e);
      throw new ActivitiException("Error in DocumentActivityBehavior "+e.getMessage());
    }
  }

  private Set<String> getOmitSectionBreakFiles(DelegateExecution execution) {
    Set<String> omitSectionBreaks = new HashSet<String>();
    if (sectionbreaks != null) {
      String sectionBreaksVal = sectionbreaks.getValue(execution).toString();
      for (String fileName : sectionBreaksVal.split(",")) {
        omitSectionBreaks.add(fileName.trim());
      }
    }
    return omitSectionBreaks;
  }

  private List<String> getProcessVariableKeys(DelegateExecution execution) {
    return new ArrayList<String>(execution.getVariableNames());
  }

  private List<Object> getProcessVariableVals(DelegateExecution execution, List<String> keys) {
    List<Object> vals = new ArrayList<Object>();
    for (String key : keys) {
      try {
        VariableInstance variableInstance = execution.getVariableInstance(key);
        if (variableInstance != null && key.startsWith("user_")) {
          UserCache.CachedUser cachedUser = userCache.getUser(variableInstance.getLongValue());
          if (cachedUser != null) {
            vals.add(cachedUser.getUser().getFullName());
          } else {
            vals.add(variableInstance.getTextValue());
          }
        } else {
          vals.add(execution.getVariable(key));
        }
      } catch (Exception e) {
        vals.add(null);
      }
    }
    return vals;
  }

  private File getOutputFile() throws IOException {
    return File.createTempFile("document-activity-behavior", ".docx");
  }

  private List<RelatedContent> getInputFiles(DelegateExecution execution, String inputFileField) {
    List<RelatedContent> list = new ArrayList<RelatedContent>();
    for (String fileField : inputFileField.split(",")) {
      fileField = fileField.trim();

      // Find template file stored as RelatedContent, query by field and process_id
      Page<RelatedContent> page = relatedContentService.getFieldContentForProcessInstance(
          execution.getProcessInstanceId(), fileField, 1, 0);

      // If there are multiple RelatedContent matching field and process_id, use first one
      List<RelatedContent> contentList = page.getContent();
      if (contentList.size() > 0) {
        list.add(contentList.get(0));
      }
    }
    return list;
  }

  private boolean runDocumentTemplater(JSONObject json) {
    boolean success;
    try {
      String cmd = "echo '"+ json.toString() + "' | " + nodejsPath + " " + scriptPath;
      LOG.info("Running " + cmd);
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

  protected void createRelatedContentForGeneratedOutput(DelegateExecution execution, RelatedContent content, String field, String filePath) {
    try {
      // TEMP - need to plumb in via Expression and add field to Task definition
      String fileName = (String) execution.getVariable("doc_title");
      if (fileName == null) {
        fileName = content.getName();
      } else {
        fileName += ".docx";
      }

      File generatedFile = new File(filePath);
      FileInputStream generatedFileStream = new FileInputStream(generatedFile);
      relatedContentService.createRelatedContent(
          content.getCreatedBy(),
          fileName,
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
    String path = environment.getProperty("document.nodejs.path");
    if (path != null) {
      nodejsPath = path;
    }
  }

  private void setup() {
    if (!setupComplete) {
      copyScriptToLocalFilesystem();
      setNodeJSPath();
      registerAsposeLicense();
      setupComplete = true;
    }
  }

  private void registerAsposeLicense() {
    try {
      ClassLoader classLoader = getClass().getClassLoader();
      InputStream is = classLoader.getResourceAsStream(LICENSE_FILE);
      License license = new License();
      license.setLicense(is);
    } catch (Exception e) {
      LOG.error("Error applying Aspose license", e);
    }
  }
}
