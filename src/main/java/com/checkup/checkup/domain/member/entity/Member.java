package com.checkup.checkup.domain.member.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(name = "member")
@Entity
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long datagsmId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private MemberRole role;

    @CreationTimestamp
    private Instant createdAt;

    public static Member create(Long datagsmId, String name, MemberRole role) {
        Member member = new Member();
        member.datagsmId = datagsmId;
        member.name = name;
        member.role = role;

        return member;
    }

    public void update(String name, MemberRole role) {
        this.name = name;
        this.role = role;
    }
}
