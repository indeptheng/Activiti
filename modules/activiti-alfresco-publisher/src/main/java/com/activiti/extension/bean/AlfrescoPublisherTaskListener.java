package com.activiti.extension.bean;

import org.activiti.engine.delegate.DelegateTask;
import org.activiti.engine.delegate.Expression;
import org.activiti.engine.delegate.TaskListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Jonathan Mulieri
 */
@Component("alfrescoPublisherTaskListener")
public class AlfrescoPublisherTaskListener implements TaskListener {
  private static final Logger LOG = LoggerFactory.getLogger(AlfrescoPublisherTaskListener.class);

  protected Expression inputFiles;
  protected Expression destinationDir;
  protected Expression updateExisting;

  @Autowired
  private AlfrescoPublisherActivityBehavior alfrescoPublisherActivityBehavior;

  public AlfrescoPublisherTaskListener() { }

  @Override
  public void notify(DelegateTask delegateTask) {
    if (inputFiles != null && destinationDir != null) {
      alfrescoPublisherActivityBehavior.inputfiles = inputFiles;
      alfrescoPublisherActivityBehavior.destinationdir = destinationDir;
      alfrescoPublisherActivityBehavior.updateExisting = updateExisting;
      alfrescoPublisherActivityBehavior.execute(delegateTask.getExecution());
    } else {
      LOG.warn("Unable to publish to Alfresco, please ensure inputFiles and destinationDir fields are set");
    }
  }
}
