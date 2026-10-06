package com.checkup.checkup.domain.member.repository;

import com.checkup.checkup.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 로그인 회원. DataGSM 계정 id로 찾는다. */
public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByDatagsmId(Long datagsmId);
}
