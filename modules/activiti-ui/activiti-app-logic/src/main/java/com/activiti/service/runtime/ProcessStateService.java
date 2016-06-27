/**
 * Activiti app component part of the Activiti project
 * Copyright 2005-2015 Alfresco Software, Ltd. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package com.activiti.service.runtime;

import com.activiti.content.storage.api.ContentStorage;
import com.activiti.domain.runtime.ProcessState;
import com.activiti.repository.runtime.ProcessStateRepository;
import com.activiti.repository.runtime.RelatedContentRepository;
import org.activiti.engine.runtime.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author Jonathan Mulieri
 */
@Service
public class ProcessStateService {

  @Autowired
  protected ProcessStateRepository processStateRepository;

  @Autowired
  protected Clock clock;

  public ProcessState getProcessState(String stateId) {
    ProcessState processState = processStateRepository.findByStateId(stateId);
    return processState;
  }

  public ProcessState saveProcessState(String processInstanceId, String stateId, String state) {
    ProcessState processState = getProcessState(stateId);
    if (processState == null) {
      processState = new ProcessState();
    }
    processState.setStateId(stateId);
    processState.setProcessInstanceId(processInstanceId);
    processState.setProcessState(state);
    return processStateRepository.save(processState);
  }
}
