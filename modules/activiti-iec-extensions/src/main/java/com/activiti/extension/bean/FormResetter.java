package com.activiti.extension.bean;

import com.activiti.domain.runtime.Form;
import com.activiti.repository.runtime.FormRepository;
import org.activiti.engine.FormService;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.DelegateTask;
import org.activiti.engine.delegate.TaskListener;
import org.activiti.engine.form.TaskFormData;
import org.activiti.engine.impl.transformer.StringToLong;
import org.activiti.engine.impl.util.json.JSONArray;
import org.activiti.engine.impl.util.json.JSONObject;
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
  private static final StringToLong stringToLong = new StringToLong();

  @Autowired
  FormRepository formRepository;

  @Autowired
  FormService formService;

  public FormResetter() { }

  @Override
  public void notify(DelegateTask delegateTask) {
    DelegateExecution execution = delegateTask.getExecution();
    TaskFormData taskFormData = formService.getTaskFormData(delegateTask.getId());
    Form form = formRepository.getOne((Long)stringToLong.transform(taskFormData.getFormKey()));
    JSONObject formDefinition = new JSONObject(form.getDefinition());
    JSONArray fields = (JSONArray) formDefinition.get("fields");
    for (int i = 0; i < fields.length(); i++) {
      JSONObject field = fields.getJSONObject(i);
      String fieldId = field.getString("id");
      if (execution.getVariable(fieldId) != null) {
        execution.setVariable(fieldId, null);
      }
    }
  }
}
