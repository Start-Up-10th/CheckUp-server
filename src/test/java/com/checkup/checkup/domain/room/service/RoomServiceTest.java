package com.checkup.checkup.domain.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.member.service.MemberService;
import com.checkup.checkup.domain.room.dto.response.RoomStudentResponse;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long STUDENT_ID = 2L;
    private static final int ROOM = 301;

    @Mock
    private MemberService memberService;

    @Mock
    private StudentRepository studentRepository;

    private RoomService roomService;

    @BeforeEach
    void setUp() {
        roomService = new RoomService(memberService, studentRepository);
    }

    @Test
    @DisplayName("학생 정보가 없는 교사 관리자도 호실 명단을 조회할 수 있다")
    void 학생_정보가_없는_교사_관리자는_호실_명단을_조회할_수_있다() {
        Member admin = Member.create(10L, "사감", MemberRole.ADMIN);
        Student rosterStudent = student(20L, "학생", 1101, ROOM);
        given(memberService.getById(ADMIN_ID)).willReturn(admin);
        given(studentRepository.findAllByDormitoryRoomOrderByMember_NameAscStudentNumberAscIdAsc(ROOM))
                .willReturn(List.of(rosterStudent));

        List<RoomStudentResponse> response = roomService.getStudents(ADMIN_ID, ROOM);

        assertThat(response).containsExactly(new RoomStudentResponse("학생", 1, 1101));
        verify(studentRepository, never()).findByMember(admin);
    }

    @Test
    @DisplayName("학생은 본인에게 배정된 호실의 명단을 조회할 수 있다")
    void 학생은_본인_호실의_명단만_조회할_수_있다() {
        Member currentMember = Member.create(20L, "학생", MemberRole.STUDENT);
        Student currentStudent = student(20L, "학생", 1101, ROOM);
        Student roommate = student(21L, "룸메이트", 1102, ROOM);
        given(memberService.getById(STUDENT_ID)).willReturn(currentMember);
        given(studentRepository.findByMember(currentMember)).willReturn(Optional.of(currentStudent));
        given(studentRepository.findAllByDormitoryRoomOrderByMember_NameAscStudentNumberAscIdAsc(ROOM))
                .willReturn(List.of(roommate, currentStudent));

        List<RoomStudentResponse> response = roomService.getStudents(STUDENT_ID, ROOM);

        assertThat(response).extracting(RoomStudentResponse::studentName).containsExactly("룸메이트", "학생");
    }

    @Test
    @DisplayName("학생의 다른 호실 접근은 명단 조회 전에 거부한다")
    void 학생은_다른_호실을_조회하기_전에_거부된다() {
        Member currentMember = Member.create(20L, "학생", MemberRole.STUDENT);
        given(memberService.getById(STUDENT_ID)).willReturn(currentMember);
        given(studentRepository.findByMember(currentMember))
                .willReturn(Optional.of(student(20L, "학생", 1101, ROOM)));

        assertThatThrownBy(() -> roomService.getStudents(STUDENT_ID, 401))
                .isInstanceOfSatisfying(CustomException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(studentRepository, never())
                .findAllByDormitoryRoomOrderByMember_NameAscStudentNumberAscIdAsc(401);
    }

    @Test
    @DisplayName("학생 정보가 없으면 403을 반환한다")
    void 학생_정보가_없으면_403이다() {
        Member currentMember = Member.create(20L, "학생", MemberRole.STUDENT);
        given(memberService.getById(STUDENT_ID)).willReturn(currentMember);
        given(studentRepository.findByMember(currentMember)).willReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.getStudents(STUDENT_ID, ROOM))
                .isInstanceOfSatisfying(CustomException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.MISSING_STUDENT_INFO));

        verify(studentRepository, never())
                .findAllByDormitoryRoomOrderByMember_NameAscStudentNumberAscIdAsc(ROOM);
    }

    @Test
    @DisplayName("호실이 미배정된 학생에게 403을 반환한다")
    void 호실이_배정되지_않은_학생은_403이다() {
        Member currentMember = Member.create(20L, "학생", MemberRole.STUDENT);
        given(memberService.getById(STUDENT_ID)).willReturn(currentMember);
        given(studentRepository.findByMember(currentMember))
                .willReturn(Optional.of(student(20L, "학생", 1101, null)));

        assertThatThrownBy(() -> roomService.getStudents(STUDENT_ID, ROOM))
                .isInstanceOfSatisfying(CustomException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("조회한 호실에 학생이 없으면 403을 반환한다")
    void 대상_호실에_학생이_없으면_403이다() {
        given(memberService.getById(ADMIN_ID)).willReturn(Member.create(10L, "사감", MemberRole.ADMIN));
        given(studentRepository.findAllByDormitoryRoomOrderByMember_NameAscStudentNumberAscIdAsc(ROOM))
                .willReturn(List.of());

        assertThatThrownBy(() -> roomService.getStudents(ADMIN_ID, ROOM))
                .isInstanceOfSatisfying(CustomException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.MISSING_STUDENT_INFO));
    }

    @Test
    @DisplayName("삭제된 회원의 요청은 401을 반환한다")
    void 삭제된_회원은_401이다() {
        given(memberService.getById(STUDENT_ID))
                .willThrow(new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        assertThatThrownBy(() -> roomService.getStudents(STUDENT_ID, ROOM))
                .isInstanceOfSatisfying(CustomException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND));

        verify(studentRepository, never())
                .findAllByDormitoryRoomOrderByMember_NameAscStudentNumberAscIdAsc(ROOM);
    }

    private static Student student(long datagsmId, String name, int studentNumber, Integer dormitoryRoom) {
        Member member = Member.create(datagsmId, name, MemberRole.STUDENT);
        return Student.create(member, datagsmId, 1, 1, 1, studentNumber, dormitoryRoom);
    }
}
