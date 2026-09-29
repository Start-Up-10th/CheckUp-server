package com.checkup.checkup.domain.member.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    @Column(unique = true)
    private Long datagsmStudentId;

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

    @Column(nullable = true)
    private String email;

    @Column(nullable = true)
    @Enumerated(EnumType.STRING)
    private Sex sex;

    @Column(nullable = true)
    private Integer dormitoryFloor;

    @Column(nullable = true)
    private String specialty;

    public static Student create(
            Member member,
            Long datagsmStudentId,
            int grade,
            int classNumber,
            int number,
            int studentNumber,
            Integer roomNumber) {
        Student student = new Student();
        student.member = member;
        student.datagsmStudentId = datagsmStudentId;
        student.grade = grade;
        student.classNumber = classNumber;
        student.number = number;
        student.studentNumber = studentNumber;
        student.roomNumber = roomNumber;

        return student;
    }

    public void updateProfile(String email, Sex sex, Integer dormitoryFloor, String specialty) {
        this.email = email;
        this.sex = sex;
        this.dormitoryFloor = dormitoryFloor;
        this.specialty = specialty;
    }

    public void update(
            Long datagsmStudentId,
            int grade,
            int classNumber,
            int number,
            int studentNumber,
            Integer roomNumber) {
        this.datagsmStudentId = datagsmStudentId;
        this.grade = grade;
        this.classNumber = classNumber;
        this.number = number;
        this.studentNumber = studentNumber;
        this.roomNumber = roomNumber;
    }
}
