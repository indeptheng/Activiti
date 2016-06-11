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

  private Expression url;
  private Expression name;

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
      LOG.warn("Could not create document link. Please make sure TaskListener Fields 'url' and 'name' are set correctly.");
    }
  }

  private String getFileUrl(DelegateTask delegateTask) {
    String fileUrl = null;
    if (url != null) {
      fileUrl = url.getValue(delegateTask.getExecution()).toString();
      Object fileUrlVar = delegateTask.getExecution().getVariable(fileUrl);
      if (fileUrlVar != null) {
        fileUrl = fileUrlVar.toString();
      }
    }
    return fileUrl;
  }

  private String getDocName(DelegateTask delegateTask) {
    String nameStr = null;
    if (name != null) {
      nameStr = name.getValue(delegateTask.getExecution()).toString();
      Object nameStrVar = delegateTask.getExecution().getVariable(nameStr);
      if (nameStrVar != null) {
        nameStr = nameStrVar.toString();
      }
    }
    return nameStr;
  }
}
