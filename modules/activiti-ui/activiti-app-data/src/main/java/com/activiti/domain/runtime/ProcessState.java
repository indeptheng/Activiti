package com.activiti.domain.runtime;

import com.activiti.domain.common.IdBlockSize;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

/**
 * @author Jonathan Mulieri
 */
@Entity
@Table(name = "PROCESS_STATE")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class ProcessState {

  @Id
  @GeneratedValue(strategy = GenerationType.TABLE, generator = "processStateGenerator")
  @TableGenerator(name = "processStateGenerator", allocationSize = IdBlockSize.DEFAULT_ALLOCATION_SIZE)
  @Column(name = "id")
  protected Long id;

  @Column(name = "state_id")
  private String stateId;

  @Column(name = "process_id")
  private String processInstanceId;

  @Column(name = "process_state")
  private String processState;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getStateId() {
    return stateId;
  }

  public void setStateId(String stateId) {
    this.stateId = stateId;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public void setProcessInstanceId(String processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  public String getProcessState() {
    return processState;
  }

  public void setProcessState(String processState) {
    this.processState = processState;
  }
}

