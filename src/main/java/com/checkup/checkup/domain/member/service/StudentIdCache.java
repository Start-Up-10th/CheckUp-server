package com.checkup.checkup.domain.member.service;

import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;

/**
 * 회원 id로 학생 id를 찾고, 찾은 값을 서버 메모리에 잠시 기억해 같은 회원의 다음 요청에서는 DB를 조회하지 않는다.
 *
 * - 학생이 보내는 요청마다 세션의 회원 id로 학생을 찾는다. 이 연결은 첫 로그인 때 만들어진 뒤 바뀌지 않아 기억해도 틀릴 일이 거의 없다.
 * - 학생 id만 기억한다. 호실·동의·봉사 횟수처럼 바뀌는 값은 기억하지 않으므로 그런 값이 필요한 곳은 계속 DB에서 읽는다.
 * - 학생이 없는 회원(교사, 아직 연결되지 않은 계정)은 기억하지 않는다. 나중에 연결되면 바로 찾을 수 있어야 한다.
 * - 기억한 값은 10분 뒤 버린다. 학생이 새 DataGSM 계정으로 다시 연결되면 예전 계정은 그동안 같은 학생으로 조회될 수 있다.
 * - 서버 프로세스 안에만 있다. 서버를 여러 대로 늘려도 값이 바뀌지 않는 연결이라 서버마다 따로 기억해도 된다.
 * - 적중·실패 횟수를 {@code cache.gets} 지표(cache=studentIdByMember)로 내보낸다. 지표 수집기가 있으면 Spring Boot가 {@link #bindTo}를 부른다.
 */
@Component
public class StudentIdCache implements MeterBinder {

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final long MAX_SIZE = 5_000;
    private static final String CACHE_NAME = "studentIdByMember";

    private final StudentRepository studentRepository;
    private final Cache<Long, Long> studentIdByMemberId;

    @Autowired
    public StudentIdCache(StudentRepository studentRepository) {
        this(studentRepository, Ticker.systemTicker());
    }

    StudentIdCache(StudentRepository studentRepository, Ticker ticker) {
        this.studentRepository = studentRepository;
        this.studentIdByMemberId = Caffeine.newBuilder()
                .expireAfterWrite(TTL)
                .maximumSize(MAX_SIZE)
                .ticker(ticker)
                .recordStats()
                .build();
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        new CaffeineCacheMetrics<>(studentIdByMemberId, CACHE_NAME, Tags.empty()).bindTo(registry);
    }

    /**
     * 회원 id에 연결된 학생 id를 돌려준다. 기억한 값이 있으면 DB를 조회하지 않는다.
     *
     * @param memberId 세션의 회원 id
     * @return 학생 id. 학생이 아닌 회원이면 비어 있다
     */
    public Optional<Long> findStudentId(Long memberId) {
        Long cached = studentIdByMemberId.getIfPresent(memberId);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<Long> found = studentRepository.findIdByMemberId(memberId);
        found.ifPresent(studentId -> studentIdByMemberId.put(memberId, studentId));
        return found;
    }
}
