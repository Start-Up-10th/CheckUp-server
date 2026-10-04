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
    @JoinColumn(name = "member_id", unique = true)
    private Member member;

    @Column(nullable = false, length = 50)
    private String name;

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

    @Column(name = "face_consent_version", length = 40)
    private String faceConsentVersion;

    /**
     * 호실 번호를 100으로 나눈 정수 값으로 층을 계산한다. 호실이 미배정이면 null을 반환한다.
     */
    public Integer getDormitoryFloor() {
        return dormitoryRoom == null ? null : dormitoryRoom / 100;
    }

    @Column(nullable = true)
    private String email;

    @Column(nullable = true)
    @Enumerated(EnumType.STRING)
    private Sex sex;

    @Column(nullable = true)
    private Integer dormitoryFloor;

    @Column(nullable = true)
    private String specialty;

    /** 앞으로 해야 할 봉사 횟수(DEC-020). DataGSM 값이 아니라 우리 서버가 관리하므로 동기화 갱신에서 건드리지 않는다. */
    @Column(nullable = false)
    private int volunteerCount;

    /** 개인정보 수집 및 이용에 처음 동의한 시각. 동의하지 않았으면 null이다. */
    @Column(nullable = true)
    private Instant privacyAgreedAt;

    /** 얼굴 정보 처리에 처음 동의한 시각. 동의하지 않았으면 null이다. */
    @Column(nullable = true)
    private Instant faceAgreedAt;

    /** 기숙사 공지 알림 수신 여부(선택, 기본 해제). */
    @Column(nullable = false)
    private boolean noticeAlarmAgreed;

    public static Student create(
            Member member,
            Long datagsmStudentId,
            String name,
            int grade,
            int classNumber,
            int number,
            int studentNumber,
            Integer dormitoryRoom) {
        Student student = createWithoutMember(datagsmStudentId, name, grade, classNumber, number, studentNumber, dormitoryRoom);
        student.member = member;
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
            String name,
            int grade,
            int classNumber,
            int number,
            int studentNumber,
            Integer dormitoryRoom) {
        this.datagsmStudentId = datagsmStudentId;
        this.name = name;
        this.grade = grade;
        this.classNumber = classNumber;
        this.number = number;
        this.studentNumber = studentNumber;
        this.dormitoryRoom = dormitoryRoom;
    }

    public void linkMember(Member member) {
        this.member = member;
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

    /**
     * 필수 동의 두 항목을 기록하고 공지 알림 수신 여부를 정한다.
     * 이미 동의한 필수 항목은 처음 동의한 시각을 유지하고, 공지 알림 수신은 이번 선택으로 바꾼다.
     *
     * @param noticeAlarm 기숙사 공지 알림 수신 여부
     * @param now         동의 시각
     * @param faceConsentVersion 얼굴 정보 처리 동의 문구 버전
     */
    public void agree(boolean noticeAlarm, Instant now, String faceConsentVersion) {
        if (privacyAgreedAt == null) {
            privacyAgreedAt = now;
        }
        if (faceAgreedAt == null) {
            faceAgreedAt = now;
            this.faceConsentVersion = faceConsentVersion;
        }
        noticeAlarmAgreed = noticeAlarm;
    }

    /**
     * 필수 동의 두 항목(개인정보, 얼굴 정보)에 모두 동의했는지 확인한다.
     */
    public boolean hasRequiredConsent() {
        return privacyAgreedAt != null && faceAgreedAt != null;
    }

    public static Student createWithoutMember(
            Long datagsmStudentId,
            String name,
            int grade,
            int classNumber,
            int number,
            int studentNumber,
            Integer dormitoryRoom) {
        Student student = new Student();
        student.name = name;
        student.datagsmStudentId = datagsmStudentId;
        student.grade = grade;
        student.classNumber = classNumber;
        student.number = number;
        student.studentNumber = studentNumber;
        student.dormitoryRoom = dormitoryRoom;

        return student;
    }
}
