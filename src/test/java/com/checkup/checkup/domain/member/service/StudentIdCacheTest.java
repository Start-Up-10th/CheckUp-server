package com.checkup.checkup.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.checkup.checkup.domain.member.repository.StudentRepository;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * StudentIdCache가 찾은 학생 id만 10분 동안 기억하고, 학생이 없는 회원은 기억하지 않는지 검증한다.
 */
class StudentIdCacheTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long STUDENT_ID = 10L;

    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final AtomicLong nanos = new AtomicLong();
    private final StudentIdCache cache = new StudentIdCache(studentRepository, nanos::get);

    @Test
    @DisplayName("같은 회원을 다시 찾으면 DB를 조회하지 않는다")
    void secondLookupSkipsDatabase() {
        given(studentRepository.findIdByMemberId(MEMBER_ID)).willReturn(Optional.of(STUDENT_ID));

        assertThat(cache.findStudentId(MEMBER_ID)).contains(STUDENT_ID);
        assertThat(cache.findStudentId(MEMBER_ID)).contains(STUDENT_ID);

        verify(studentRepository, times(1)).findIdByMemberId(MEMBER_ID);
    }

    @Test
    @DisplayName("학생이 없는 회원은 기억하지 않아 나중에 연결되면 바로 찾는다")
    void missingStudentIsNotRemembered() {
        given(studentRepository.findIdByMemberId(MEMBER_ID))
                .willReturn(Optional.empty(), Optional.of(STUDENT_ID));

        assertThat(cache.findStudentId(MEMBER_ID)).isEmpty();
        assertThat(cache.findStudentId(MEMBER_ID)).contains(STUDENT_ID);
    }

    @Test
    @DisplayName("10분이 지나면 DB에서 다시 읽는다")
    void expiresAfterTenMinutes() {
        given(studentRepository.findIdByMemberId(MEMBER_ID)).willReturn(Optional.of(STUDENT_ID));
        cache.findStudentId(MEMBER_ID);

        nanos.addAndGet(Duration.ofMinutes(9).toNanos());
        cache.findStudentId(MEMBER_ID);
        verify(studentRepository, times(1)).findIdByMemberId(MEMBER_ID);

        nanos.addAndGet(Duration.ofMinutes(2).toNanos());
        cache.findStudentId(MEMBER_ID);
        verify(studentRepository, times(2)).findIdByMemberId(MEMBER_ID);
    }

    @Test
    @DisplayName("회원마다 따로 기억한다")
    void remembersPerMember() {
        given(studentRepository.findIdByMemberId(1L)).willReturn(Optional.of(10L));
        given(studentRepository.findIdByMemberId(2L)).willReturn(Optional.of(20L));

        assertThat(cache.findStudentId(1L)).contains(10L);
        assertThat(cache.findStudentId(2L)).contains(20L);
        assertThat(cache.findStudentId(1L)).contains(10L);
    }

    @Test
    @DisplayName("적중과 실패 횟수를 지표로 내보낸다")
    void exportsHitAndMissMetrics() {
        given(studentRepository.findIdByMemberId(MEMBER_ID)).willReturn(Optional.of(STUDENT_ID));
        cache.bindTo(meterRegistry);

        cache.findStudentId(MEMBER_ID);
        cache.findStudentId(MEMBER_ID);
        cache.findStudentId(MEMBER_ID);

        assertThat(meterRegistry.get("cache.gets").tag("cache", "studentIdByMember").tag("result", "hit")
                .functionCounter().count()).isEqualTo(2);
        assertThat(meterRegistry.get("cache.gets").tag("cache", "studentIdByMember").tag("result", "miss")
                .functionCounter().count()).isEqualTo(1);
    }
}
