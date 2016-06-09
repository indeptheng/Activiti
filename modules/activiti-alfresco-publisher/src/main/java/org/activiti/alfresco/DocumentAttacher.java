package org.activiti.alfresco;

import com.activiti.domain.idm.User;
import com.activiti.domain.runtime.RelatedContent;
import com.activiti.security.SecurityUtils;
import org.activiti.engine.delegate.DelegateTask;
import org.activiti.engine.delegate.TaskListener;
import com.activiti.service.runtime.RelatedContentService;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jonathan Mulieri
 */
public class DocumentAttacher implements TaskListener {
  private static final Logger LOG = LoggerFactory.getLogger(AlfrescoPublisherActivityBehavior.class);

  private AnnotationConfigApplicationContext applicationContext;
  private RelatedContentService relatedContentService;

  public DocumentAttacher() {
    try {
      Class<?> theClass = Class.forName("com.activiti.conf.ApplicationConfiguration");
      applicationContext = new AnnotationConfigApplicationContext(theClass);
      relatedContentService = applicationContext.getBean(RelatedContentService.class);
    } catch (ClassNotFoundException e) {
      LOG.error("Could not load ApplicationConfiguration", e);
    }
  }

  @Override
  public void notify(DelegateTask delegateTask) {
    String fileUrl = delegateTask.getVariable(AlfrescoPublisherActivityBehavior.ALFRESCO_FILE_URL).toString();
    User user = SecurityUtils.getCurrentUserObject();
    RelatedContent relatedContent = relatedContentService.createRelatedContent(user, "Alfresco DOR", null, null, delegateTask.getId(),
        delegateTask.getProcessInstanceId(), "text/plain", null, null, true, true);
    relatedContent.setLinkUrl(fileUrl);
    relatedContentService.storeRelatedContent(relatedContent);
    /*
    Context.getProcessEngineConfiguration().getTaskService().createAttachment(
        "url",
        delegateTask.getId(),
        null,
        "Alfresco DOR",
        "Alfresco DOR Link",
        fileUrl);
        */
  }
}
