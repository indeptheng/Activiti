package com.activiti.extension.bean;

import com.activiti.domain.idm.User;
import com.activiti.domain.runtime.RelatedContent;
import com.activiti.security.SecurityUtils;
import org.activiti.engine.delegate.DelegateTask;
import org.activiti.engine.delegate.Expression;
import org.activiti.engine.delegate.TaskListener;
import com.activiti.service.runtime.RelatedContentService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jonathan Mulieri
 */
@Component("documentAttacher")
public class DocumentAttacher implements TaskListener {
  private static final Logger LOG = LoggerFactory.getLogger(DocumentAttacher.class);

  // There are 2 ways a user can pass in the url, either the url directly,
  // or the name of the variable containing the url
  private Expression url;
  private Expression url_variable;

  // There are 2 ways a user can pass in the name, either the name directly,
  // or the name of the variable containing the name
  private Expression name;
  private Expression name_variable;

  @Autowired
  private RelatedContentService relatedContentService;

  public DocumentAttacher() { }

  @Override
  public void notify(DelegateTask delegateTask) {
    String fileUrl = getFileUrl(delegateTask);
    String docName = getDocName(delegateTask);
    if (fileUrl != null && docName != null) {
      User user = SecurityUtils.getCurrentUserObject();
      RelatedContent relatedContent = relatedContentService.createRelatedContent(user, docName, null, null, delegateTask.getId(),
          delegateTask.getProcessInstanceId(), "text/plain", null, null, true, true);
      relatedContent.setLinkUrl(fileUrl);
      relatedContentService.storeRelatedContent(relatedContent);
    } else {
      LOG.warn("Could not create document link. Please make sure TaskListener Fields " +
          "('url' or 'url_variable') and ('name' or 'name_variable') are set correctly.");
    }
  }

  private String getFileUrl(DelegateTask delegateTask) {
    String fileUrl = null;
    if (url != null) {
      fileUrl = url.getValue(delegateTask.getExecution()).toString();
    } else if (url_variable != null) {
      fileUrl = delegateTask.getExecution().getVariable(
          url_variable.getValue(delegateTask.getExecution()).toString()
      ).toString();
    }
    return fileUrl;
  }

  private String getDocName(DelegateTask delegateTask) {
    String nameStr = null;
    if (name != null) {
      nameStr = name.getValue(delegateTask.getExecution()).toString();
    } else if (name_variable != null) {
      nameStr = delegateTask.getExecution().getVariable(
          name_variable.getValue(delegateTask.getExecution()).toString()
      ).toString();
    }
    return nameStr;
  }
}
