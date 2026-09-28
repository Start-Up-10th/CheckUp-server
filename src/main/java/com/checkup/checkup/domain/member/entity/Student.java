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
     * Assigned floor derived from the student's room number. A missing room has no floor.
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
