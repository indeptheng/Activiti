package com.activiti.extension.bean;

import com.activiti.content.storage.api.ContentObject;
import com.activiti.content.storage.fs.FileSystemContentStorage;
import com.activiti.domain.runtime.ProcessState;
import com.activiti.domain.runtime.RelatedContent;
import com.activiti.service.runtime.ProcessStateService;
import com.activiti.service.runtime.RelatedContentService;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.Expression;
import org.activiti.engine.delegate.JavaDelegate;
import org.activiti.engine.impl.util.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.List;


/**
 * @author Jonathan Mulieri
 */
@Component("processStateInjector")
public class ProcessStateInjector implements JavaDelegate {
  private static final Logger LOG = LoggerFactory.getLogger(ProcessStateInjector.class);

  @Autowired
  protected ProcessStateService processStateService;

  @Autowired
  protected RelatedContentService relatedContentService;

  protected Expression processStateId;

  @Override
  public void execute(DelegateExecution execution) {
    String stateId = (String) processStateId.getValue(execution);
    if (stateId != null && !stateId.isEmpty()) {
      ProcessState processState = processStateService.getProcessState(stateId);
      if (processState != null) {
        JSONObject processVariables = new JSONObject(processState.getProcessState());
        LOG.info("Injecting process state {}", processVariables);
        injectProcessState(processState, execution, processVariables);
      } else {
        LOG.warn("Could not find ProcessState with processStateId {}", stateId);
      }
    } else {
      LOG.warn("Could not inject process state, field processStateId was not set");
    }
  }

  private void injectProcessState(ProcessState processState, DelegateExecution execution, JSONObject processVariables) {
    FileSystemContentStorage contentStorage = (FileSystemContentStorage) relatedContentService.getContentStorage();
    Iterator keys = processVariables.keys();
    while (keys.hasNext()) {
      String key = (String) keys.next();
      Object val = processVariables.get(key);
      if (val == null || val.equals(null)) {
        // val is null, check if there is a RelatedContent for this variable
        Page<RelatedContent> page = relatedContentService.getFieldContentForProcessInstance(
            processState.getProcessInstanceId(), key, 1, 0);

        List<RelatedContent> contentList = page.getContent();
        if (contentList.size() > 0) {
          RelatedContent content = contentList.get(0);
          ContentObject contentObject = contentStorage.getContentObject(content.getContentStoreId());
          // Clone the RelatedContent into this process
          relatedContentService.createRelatedContent(
              content.getCreatedBy(),
              content.getName(),
              null, null, null,
              execution.getProcessInstanceId(),
              key,
              content.getMimeType(),
              contentObject.getContent(),
              content.getContentSize()
          );
        }
        execution.setVariable(key, null);
      } else {
        execution.setVariable(key, val);
      }
    }
  }
}
