package com.checkup.checkup.domain.volunteer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.volunteer.dto.response.VolunteerResponse;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.security.AdminVerifier;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 봉사 관리 명단이 관리자에게만 전체 학생을 봉사 횟수와 함께 보여주는지 검증한다.
 */
class VolunteerServiceTest {

    private static final Long MEMBER_ID = 1L;

    private final AdminVerifier adminVerifier = mock(AdminVerifier.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final VolunteerService service = new VolunteerService(adminVerifier, studentRepository);

    @Test
    @DisplayName("전체 학생을 DataGSM id·이름·학번·호실·봉사 횟수로 응답하고 최근 활동은 비운다")
    void listIsConverted() {
        Student student = Student.create(Member.create(100L, "학생", MemberRole.STUDENT), 200L, 2, 1, 5, 2105, 301);
        ReflectionTestUtils.setField(student, "volunteerCount", 2);
        given(studentRepository.findAllByOrderByMember_NameAscStudentNumberAsc()).willReturn(List.of(student));

        List<VolunteerResponse> list = service.getVolunteers(MEMBER_ID, null, null);

        assertThat(list).containsExactly(new VolunteerResponse(200L, "학생", 2105, 301, 3, 2, null));
    }

    @Test
    @DisplayName("호실 → 이름 → 학번순으로 정렬하고 호실이 없는 학생은 맨 뒤에 둔다")
    void sortedByRoomThenNameThenNumber() {
        givenStudents(
                student(1L, "나학생", 2102, 412),
                student(2L, "가학생", 2101, null),
                student(3L, "다학생", 2103, 301),
                student(4L, "가학생", 2104, 412));

        assertThat(service.getVolunteers(MEMBER_ID, null, null))
                .extracting(VolunteerResponse::studentNumber)
                .containsExactly(2103, 2104, 2102, 2101);
    }

    @Test
    @DisplayName("관리자가 아니면 403이고 학생을 조회하지 않는다")
    void nonAdminIsRejected() {
        willThrow(new CustomException(ErrorCode.ADMIN_ONLY)).given(adminVerifier).verify(MEMBER_ID);

        assertThatThrownBy(() -> service.getVolunteers(MEMBER_ID, null, null))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADMIN_ONLY));
        verify(studentRepository, never()).findAllByOrderByMember_NameAscStudentNumberAsc();
    }

    private void givenStudents(Student... students) {
        given(studentRepository.findAllByOrderByMember_NameAscStudentNumberAsc()).willReturn(List.of(students));
    }

    private static Student student(Long datagsmId, String name, int studentNumber, Integer room) {
        return Student.create(Member.create(datagsmId + 1000, name, MemberRole.STUDENT), datagsmId, 2, 1, 1,
                studentNumber, room);
    }
}
