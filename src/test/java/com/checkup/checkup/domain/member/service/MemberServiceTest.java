package com.checkup.checkup.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.MemberRepository;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.themoment.datagsm.sdk.oauth.model.UserInfo;

/**
 * 로그인할 때 DataGSM 학생 정보로 학생의 호실·이름이 저장·갱신되고, 동기화로 미리 저장되거나 동시에 저장된 학생에는
 * 계정을 연결하는지 검증한다(#152).
 */
@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    private static final Long DATAGSM_USER_ID = 100L;
    private static final Long DATAGSM_STUDENT_ID = 10L;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private StudentRepository studentRepository;

    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberService = new MemberService(memberRepository, studentRepository);
    }

    @Test
    @DisplayName("저장된 학생이 없으면 없을 때만 저장한 뒤 다시 조회한 학생에 계정과 호실을 반영한다")
    void insertsMissingStudentAndLinksMember() {
        Student inserted = Student.createWithoutMember(DATAGSM_STUDENT_ID, "학생", 1, 1, 1, 1101, 301);
        givenNewMember();
        given(studentRepository.findByDatagsmStudentId(DATAGSM_STUDENT_ID))
                .willReturn(Optional.empty(), Optional.of(inserted));
        given(studentRepository.insertIfAbsent(DATAGSM_STUDENT_ID, "학생", 1, 1, 1, 1101, 301, null))
                .willReturn(1);

        Member member = memberService.saveOrUpdate(studentUser(301), MemberRole.STUDENT);

        assertThat(inserted.getMember()).isSameAs(member);
        assertThat(inserted.getDormitoryRoom()).isEqualTo(301);
        assertThat(inserted.getDormitoryFloor()).isEqualTo(3);
        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    @DisplayName("동기화가 같은 학생을 먼저 저장해 없을 때만 저장이 건너뛰어도 그 학생에 계정을 연결한다")
    void linksStudentSavedConcurrentlyBySync() {
        Student savedBySync = Student.createWithoutMember(DATAGSM_STUDENT_ID, "옛이름", 1, 1, 1, 1101, 301);
        givenNewMember();
        given(studentRepository.findByDatagsmStudentId(DATAGSM_STUDENT_ID))
                .willReturn(Optional.empty(), Optional.of(savedBySync));
        given(studentRepository.insertIfAbsent(DATAGSM_STUDENT_ID, "학생", 1, 1, 1, 1101, 425, null))
                .willReturn(0);

        Member member = memberService.saveOrUpdate(studentUser(425), MemberRole.STUDENT);

        assertThat(savedBySync.getMember()).isSameAs(member);
        assertThat(savedBySync.getName()).isEqualTo("학생");
        assertThat(savedBySync.getDormitoryRoom()).isEqualTo(425);
    }

    @Test
    @DisplayName("로그인 학생의 변경된 호실을 기존 dormitoryRoom에 반영한다")
    void updatesDormitoryRoomOnLogin() {
        Member member = Member.create(DATAGSM_USER_ID, "학생", MemberRole.STUDENT);
        Student savedStudent = Student.create(member, DATAGSM_STUDENT_ID, "학생", 1, 1, 1, 1101, 301);
        given(memberRepository.findByDatagsmId(DATAGSM_USER_ID)).willReturn(Optional.of(member));
        given(studentRepository.findByMember(member)).willReturn(Optional.of(savedStudent));

        memberService.saveOrUpdate(studentUser(425), MemberRole.STUDENT);

        assertThat(savedStudent.getDormitoryRoom()).isEqualTo(425);
        assertThat(savedStudent.getDormitoryFloor()).isEqualTo(4);
        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    @DisplayName("동기화로 미리 저장된 학생이 처음 로그인하면 새로 만들지 않고 계정을 연결한다")
    void linksPreStoredStudentOnFirstLogin() {
        Student preStored = Student.createWithoutMember(DATAGSM_STUDENT_ID, "옛이름", 1, 1, 1, 1101, 301);
        given(memberRepository.findByDatagsmId(DATAGSM_USER_ID)).willReturn(Optional.empty());
        given(memberRepository.save(any(Member.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(studentRepository.findByMember(any(Member.class))).willReturn(Optional.empty());
        given(studentRepository.findByDatagsmStudentId(DATAGSM_STUDENT_ID)).willReturn(Optional.of(preStored));

        Member member = memberService.saveOrUpdate(studentUser(425), MemberRole.STUDENT);

        assertThat(preStored.getMember()).isSameAs(member);
        assertThat(preStored.getName()).isEqualTo("학생");
        assertThat(preStored.getDormitoryRoom()).isEqualTo(425);
        verify(studentRepository, never()).insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any());
    }

    private void givenNewMember() {
        given(memberRepository.findByDatagsmId(DATAGSM_USER_ID)).willReturn(Optional.empty());
        given(memberRepository.save(any(Member.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(studentRepository.findByMember(any(Member.class))).willReturn(Optional.empty());
    }

    private static UserInfo studentUser(int dormitoryRoom) {
        team.themoment.datagsm.sdk.oauth.model.Student student = new team.themoment.datagsm.sdk.oauth.model.Student();
        student.setId(DATAGSM_STUDENT_ID);
        student.setName("학생");
        student.setGrade(1);
        student.setClassNum(1);
        student.setNumber(1);
        student.setStudentNumber(1101);
        student.setDormitoryRoom(dormitoryRoom);

        UserInfo userInfo = new UserInfo();
        userInfo.setId(DATAGSM_USER_ID);
        userInfo.setStudent(student);
        return userInfo;
    }
}
