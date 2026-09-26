package com.checkup.checkup.domain.member.entity;

import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Table(name = "student")
@Entity
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, unique = true)
    private Member member;

    @Column(nullable = false)
    private int number;

    @Column(nullable = false)
    private int grade;

    @Column(nullable = false)
    private int classNumber;

    @Column(nullable = false)
    private int studentNumber;

    @Column(nullable = true)
    private Integer roomNumber;
}
