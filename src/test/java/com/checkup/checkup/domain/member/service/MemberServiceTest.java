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
 * 로그인할 때 DataGSM 학생 정보로 학생의 호실이 저장·갱신되는지 검증한다.
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
    @DisplayName("로그인 학생의 DataGSM 호실을 dormitoryRoom으로 저장한다")
    void savesDormitoryRoomOnLogin() {
        UserInfo userInfo = studentUser(301);
        given(memberRepository.findByDatagsmId(DATAGSM_USER_ID)).willReturn(Optional.empty());
        given(memberRepository.save(any(Member.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(studentRepository.findByMember(any(Member.class))).willReturn(Optional.empty());

        memberService.saveOrUpdate(userInfo, MemberRole.STUDENT);

        verify(studentRepository).save(org.mockito.ArgumentMatchers.argThat(student ->
                student.getDormitoryRoom() == 301 && student.getDormitoryFloor() == 3
                        && student.getStudentNumber() == 1101 && student.getClassNumber() == 1));
    }

    @Test
    @DisplayName("로그인 학생의 변경된 호실을 기존 dormitoryRoom에 반영한다")
    void updatesDormitoryRoomOnLogin() {
        Member member = Member.create(DATAGSM_USER_ID, "학생", MemberRole.STUDENT);
        Student savedStudent = Student.create(member, DATAGSM_STUDENT_ID, 1, 1, 1, 1101, 301);
        given(memberRepository.findByDatagsmId(DATAGSM_USER_ID)).willReturn(Optional.of(member));
        given(studentRepository.findByMember(member)).willReturn(Optional.of(savedStudent));

        memberService.saveOrUpdate(studentUser(425), MemberRole.STUDENT);

        assertThat(savedStudent.getDormitoryRoom()).isEqualTo(425);
        assertThat(savedStudent.getDormitoryFloor()).isEqualTo(4);
        verify(studentRepository, never()).save(any(Student.class));
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
