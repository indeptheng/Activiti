package org.activiti.editor.language.json.converter;

import java.util.Map;

import org.activiti.bpmn.model.BaseElement;
import org.activiti.bpmn.model.FlowElement;
import org.activiti.bpmn.model.ServiceTask;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * @author Jonathan Mulieri
 */
public class AlfrescoTaskJsonConverter extends BaseBpmnJsonConverter {

  public static void fillTypes(Map<String, Class<? extends BaseBpmnJsonConverter>> convertersToBpmnMap, Map<Class<? extends BaseElement>, Class<? extends BaseBpmnJsonConverter>> convertersToJsonMap) {
    fillJsonTypes(convertersToBpmnMap);
    fillBpmnTypes(convertersToJsonMap);
  }

  public static void fillJsonTypes(Map<String, Class<? extends BaseBpmnJsonConverter>> convertersToBpmnMap) {
    convertersToBpmnMap.put(STENCIL_TASK_ALFRESCO, AlfrescoTaskJsonConverter.class);
  }

  public static void fillBpmnTypes(Map<Class<? extends BaseElement>, Class<? extends BaseBpmnJsonConverter>> convertersToJsonMap) {
  }

  protected String getStencilId(BaseElement baseElement) {
    return STENCIL_TASK_ALFRESCO;
  }

  protected void convertElementToJson(ObjectNode propertiesNode, BaseElement baseElement) {
    // done in service task
  }

  protected FlowElement convertJsonToElement(JsonNode elementNode, JsonNode modelNode, Map<String, JsonNode> shapeMap) {
    ServiceTask task = new ServiceTask();
    task.setType("alfresco");
    addField("inputfiles", PROPERTY_ALFRESCOTASK_INPUTFILE_VARIABLE, elementNode, task);
    addField("destinationdir", PROPERTY_ALFRESCOTASK_DESTINATIONDIR_VARIABLE, elementNode, task);
    return task;
  }
}
