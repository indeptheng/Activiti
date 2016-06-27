package com.activiti.repository.runtime;

import com.activiti.domain.runtime.ProcessState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * @author Jonathan Mulieri
 */
public interface ProcessStateRepository extends JpaRepository<ProcessState, Long> {

  @Query("from ProcessState p where p.processInstanceId = :processInstanceId")
  ProcessState findByProcessInstanceId(@Param("processInstanceId") String processInstanceId);

  @Query("from ProcessState p where p.stateId = :stateId")
  ProcessState findByStateId(@Param("stateId") String stateId);
}
