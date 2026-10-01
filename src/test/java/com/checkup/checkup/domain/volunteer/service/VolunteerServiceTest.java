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
    @DisplayName("층을 고르면 호실 맨 앞자리가 그 층인 학생만 나오고 호실이 없는 학생은 빠진다")
    void filteredByFloor() {
        givenStudents(
                student(1L, "학생1", 2101, 412),
                student(2L, "학생2", 2102, 501),
                student(3L, "학생3", 2103, null),
                student(4L, "학생4", 2104, 401));

        assertThat(service.getVolunteers(MEMBER_ID, 4, null))
                .extracting(VolunteerResponse::studentNumber)
                .containsExactly(2104, 2101);
    }

    @Test
    @DisplayName("숫자로 검색하면 호실 번호나 학번이 정확히 같은 학생만 나온다")
    void numberSearchMatchesRoomOrStudentNumberExactly() {
        givenStudents(
                student(1L, "학생1", 2101, 412),
                student(2L, "학생2", 2102, 412),
                student(3L, "학생3", 2103, 413),
                student(4L, "학생4", 4120, 301));

        assertThat(service.getVolunteers(MEMBER_ID, null, " 412 "))
                .extracting(VolunteerResponse::studentNumber)
                .containsExactly(2101, 2102);
        assertThat(service.getVolunteers(MEMBER_ID, null, "2103"))
                .extracting(VolunteerResponse::studentNumber)
                .containsExactly(2103);
    }

    @Test
    @DisplayName("글자로 검색하면 이름에 포함된 학생만 나오고, 층 필터와 함께 쓸 수 있다")
    void nameSearchWithFloor() {
        givenStudents(
                student(1L, "강민우", 2101, 412),
                student(2L, "김민우", 2102, 501),
                student(3L, "홍길동", 2103, 413));

        assertThat(service.getVolunteers(MEMBER_ID, null, "민우"))
                .extracting(VolunteerResponse::name)
                .containsExactly("강민우", "김민우");
        assertThat(service.getVolunteers(MEMBER_ID, 4, "민우"))
                .extracting(VolunteerResponse::name)
                .containsExactly("강민우");
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
