package com.checkup.checkup.domain.member.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

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

    /** 마지막으로 반영한 DataGSM 웹훅 이벤트 시각. 웹훅을 받은 적 없으면 null이다. */
    @Column(nullable = true)
    private Instant datagsmSyncedAt;

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

    /**
     * 이벤트가 이미 반영한 것보다 오래됐는지 확인한다. 재전송이나 순서가 뒤바뀐 이벤트가 최신 정보를 덮어쓰지 않게 한다.
     *
     * @param eventTime DataGSM 이벤트 발생 시각
     * @return 마지막으로 반영한 시각과 같거나 이전이면 {@code true}, 반영한 적 없거나 더 새로우면 {@code false}
     */
    public boolean isStale(Instant eventTime) {
        if (datagsmSyncedAt != null && !eventTime.isAfter(datagsmSyncedAt)) {
            return true;
        }
        return false;
    }

    /**
     * 반영한 DataGSM 이벤트 시각을 기록한다. 학생 정보를 갱신한 직후에 부른다.
     *
     * @param eventTime 반영한 이벤트의 발생 시각
     */
    public void markSynced(Instant eventTime) {
        this.datagsmSyncedAt = eventTime;
    }

    /**
     * 졸업·자퇴한 학생의 호실 배정을 비워 호실 명단에서 빠지게 한다. 학년·반·번호는 그대로 둔다.
     */
    public void leaveDormitory() {
        this.dormitoryRoom = null;
    }
}
