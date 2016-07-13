package com.activiti.extension.bean;

import com.activiti.service.runtime.ProcessStateService;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.Expression;
import org.activiti.engine.delegate.JavaDelegate;
import org.activiti.engine.impl.util.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Jonathan Mulieri
 */
@Component("processStateSaver")
public class ProcessStateSaver implements JavaDelegate {
  private static final Logger LOG = LoggerFactory.getLogger(ProcessStateSaver.class);

  @Autowired
  protected ProcessStateService processStateService;

  protected Expression processStateId;

  @Override
  public void execute(DelegateExecution execution) {
    Map<String, Object> processVariables = execution.getVariables();
    String stateId = (String) processStateId.getValue(execution);
    if (stateId != null && !stateId.isEmpty()) {
      JSONObject state = new JSONObject(buildProcessState(processVariables));
      LOG.info("Saving process state {} => {}", stateId, state.toString());
      processStateService.saveProcessState(execution.getProcessInstanceId(), stateId, state.toString());
    } else {
      LOG.warn("Could not save process state, field processStateId was not set");
    }
  }

  private Map<String, Object> buildProcessState(Map<String, Object> processVariables) {
    Map<String, Object> processState = new HashMap<String, Object>();
    for (Map.Entry<String, Object> entry : processVariables.entrySet()) {
      Object variableValue = entry.getValue();
      Map<String, Object> value = new HashMap<String, Object>();
      value.put("value", variableValue);
      if (variableValue != null) {
        value.put("type", variableValue.getClass().getName());
      }
      processState.put(entry.getKey(), value);
    }
    return processState;
  }
}
