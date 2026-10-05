package com.checkup.checkup.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

/**
 * 개발·테스트용 관리자 허용 목록({@code CHECKUP_ADMIN_DATAGSM_IDS})을 읽는다.
 *
 * 목록에 있는 DataGSM 계정 id({@code member.datagsm_id})는 DataGSM 역할과 상관없이 관리자다.
 * 전교생 정보를 볼 수 있으므로 운영 전에는 비우거나 최소 인원만 둔다. 값은 로그에 남기지 않는다.
 *
 * @param datagsmIds 관리자로 허용할 DataGSM 계정 id. 설정이 없으면 빈 목록이다.
 */
@ConfigurationProperties("checkup.admin")
public record AdminProperties(Set<Long> datagsmIds) {
    public AdminProperties {
        if (datagsmIds == null) {
            datagsmIds = Set.of();
        }
    }

    /**
     * 허용 목록에 있는 DataGSM 계정 id인지 확인한다.
     *
     * @param datagsmId DataGSM 계정 id. {@code null}이면 허용하지 않는다.
     */
    public boolean isAllowed(Long datagsmId) {
        return datagsmId != null && datagsmIds.contains(datagsmId);
    }
}
