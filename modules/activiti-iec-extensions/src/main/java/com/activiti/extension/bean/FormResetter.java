package com.activiti.extension.bean;

import org.activiti.engine.FormService;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.DelegateTask;
import org.activiti.engine.delegate.TaskListener;
import org.activiti.engine.form.TaskFormData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Jonathan Mulieri
 */
@Component("formResetter")
public class FormResetter implements TaskListener {
  private static final Logger LOG = LoggerFactory.getLogger(FormResetter.class);

  @Autowired
  FormService formService;

  public FormResetter() { }

  @Override
  public void notify(DelegateTask delegateTask) {
    DelegateExecution execution = delegateTask.getExecution();
    TaskFormData taskFormData = formService.getTaskFormData(delegateTask.getId());
    execution.setVariable(getResetId(taskFormData.getFormKey()), true);
  }

  private String getResetId(String formKey) {
    return "reset-form-" + formKey;
  }
}
