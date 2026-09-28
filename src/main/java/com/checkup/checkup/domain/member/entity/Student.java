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
    private Integer dormitoryRoom;

    /**
     * 호실 번호를 100으로 나눈 정수 값으로 층을 계산한다. 호실이 미배정이면 null을 반환한다.
     */
    public Integer getDormitoryFloor() {
        return dormitoryRoom == null ? null : dormitoryRoom / 100;
    }

    public static Student create(
            Member member,
            Long datagsmStudentId,
            int grade,
            int classNumber,
            int number,
            int studentNumber,
            Integer dormitoryRoom) {
        Student student = new Student();
        student.member = member;
        student.datagsmStudentId = datagsmStudentId;
        student.grade = grade;
        student.classNumber = classNumber;
        student.number = number;
        student.studentNumber = studentNumber;
        student.dormitoryRoom = dormitoryRoom;

        return student;
    }

    public void update(
            Long datagsmStudentId,
            int grade,
            int classNumber,
            int number,
            int studentNumber,
            Integer dormitoryRoom) {
        this.datagsmStudentId = datagsmStudentId;
        this.grade = grade;
        this.classNumber = classNumber;
        this.number = number;
        this.studentNumber = studentNumber;
        this.dormitoryRoom = dormitoryRoom;
    }
}
