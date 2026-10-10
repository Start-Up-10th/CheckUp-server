package com.checkup.checkup.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

/**
 * DataGSM 학생 제외 목록({@code CHECKUP_EXCLUDED_DATAGSM_STUDENT_IDS})을 읽는다(DEC-034).
 *
 * DataGSM 학생 목록에 실제 학생이 아닌 데이터(예: 301호 "사감선생님")가 있을 때 쓴다.
 * 목록의 학생은 동기화가 저장·갱신하지 않고, 그 계정의 로그인은 거부한다. 이름이 아닌 학생 id로 정한다.
 *
 * @param excludedDatagsmIds 제외할 DataGSM 학생 id({@code student.datagsm_student_id}). 설정이 없으면 빈 목록이다.
 */
@ConfigurationProperties("checkup.student")
public record StudentExclusionProperties(Set<Long> excludedDatagsmIds) {
    public StudentExclusionProperties {
        if (excludedDatagsmIds == null) {
            excludedDatagsmIds = Set.of();
        }
    }

    /**
     * 제외 목록에 있는 DataGSM 학생 id인지 확인한다.
     *
     * @param datagsmStudentId DataGSM 학생 id. {@code null}이면 제외하지 않는다.
     */
    public boolean isExcluded(Long datagsmStudentId) {
        return datagsmStudentId != null && excludedDatagsmIds.contains(datagsmStudentId);
    }
}
