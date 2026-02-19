# QueryDSL 학습 (김영한 Inflearn) - 0hakxx/QueryDSL

> 실무에서 자주 쓰는 조회 요구사항(동적 조건, DTO 조회, 페이징, 정렬, 조인)을 QueryDSL로 구현하고 테스트/실행으로 검증하는 학습 레포지토리다.  
> 목표는 “문자열 JPQL을 버리고 타입 세이프로 동적 쿼리를 설계한다”에 있다.

---

## 1. 프로젝트 개요

### QueryDSL을 사용하는 이유
JPA에서 복잡한 동적 쿼리를 작성하면 다음 문제가 자주 발생한다.

- 문자열 기반 JPQL은 컴파일 타임에 오류를 잡기 어렵다
- 조건이 늘어날수록 문자열 조합 + `if` 분기로 가독성이 급격히 하락한다
- DTO 조회, 페이징, 정렬, 조인처럼 실무에서 흔한 요구사항에서 코드가 쉽게 지저분해진다

QueryDSL은 이를 다음처럼 해결한다.

- 타입 세이프(type-safe) 쿼리 작성이 가능하다
- 동적 쿼리를 BooleanBuilder/BooleanExpression 조합으로 깔끔하게 구성한다
- 복잡한 조회 로직을 Repository 계층에 읽기 좋은 형태로 정리한다

---

## 2. 기술 스택

- Java: **17**
- Spring Boot: **3.2.0**
- Spring Data JPA
- QueryDSL: **5.0.0 (jakarta)**
- DB: **H2**
- SQL 로깅: **P6Spy**
- Build Tool: **Gradle**



---

## 3. 실행 방법

### 3.1 로컬 DB 준비(H2)
`application.yaml` 기준으로 H2 접속 URL은 아래와 같다.

- `jdbc:h2:tcp://localhost/~/querydsl`

즉, H2를 TCP 모드로 띄운 뒤 접속해야 한다.

### 3.2 실행
```bash
./gradlew clean test
./gradlew bootRun
```

서버 포트는 `7070`을 사용한다.

---

## 4. 설정(application.yaml) 요약

- profile: `local`을 기본 활성화한다
- JPA ddl-auto: `create`로 실행 시 스키마를 생성한다
- SQL 로그:
  - `org.hibernate.SQL: debug`
  - 바인딩 파라미터: `org.hibernate.type.descriptor.sql.BasicBinder: trace`
  - P6Spy 로그: `p6spy: info`

---

## 5. 초기 데이터 로딩

`local` 프로필에서 초기 데이터를 자동 생성한다.

- Team: teamA, teamB 생성
- Member: member0 ~ member99, 나이는 0~99
- 팀은 짝수/홀수로 번갈아 소속시킨다

이 데이터는 동적 검색/조인/페이징 테스트에 사용한다.

---

## 6. 핵심 구현 포인트

### 6.1 JPA 기반 조회 vs QueryDSL 기반 조회
`MemberJpaRepository`에서 아래를 함께 제공한다.

- 순수 JPA: `findAll()`, `findByUsername()`
- QueryDSL: `findAll_QueryDSL()`, `findByUsername_QueryDSL()`

이를 통해 “문자열 JPQL ↔ 타입 세이프 QueryDSL”을 비교한다.

### 6.2 동적 쿼리 - BooleanBuilder 방식
`MemberSearchCondition`을 입력으로 받아 조건이 있을 때만 where 절을 조립한다.

- username
- teamName
- ageGoe(>=)
- ageLoe(<=)

### 6.3 동적 쿼리 - BooleanExpression 조합 방식
조건 메서드를 분리하고 `where(...)`에 null-safe로 조합한다.

- `where(usernameEq(...), teamNameEq(...), ageGoe(...), ageLoe(...))`
- QueryDSL은 where 파라미터가 null이면 무시하므로 코드가 단순해진다

### 6.4 DTO 조회(프로젝션)
- `MemberTeamDto` / `QMemberTeamDto` 기반으로 DTO를 직접 조회한다
- Member + Team 조인 결과를 “조회 전용 DTO”로 묶어 반환한다

---

## 7. 프로젝트 패키지 구조(확인된 범위)

```text
src/main/java/study/querydsl
 ├─ QuerydslApplication.java
 ├─ InitMember.java
 ├─ entity
 ├─ dto
 └─ repository
```

---

## 8. 트러블슈팅

### Q1. Q클래스가 생성되지 않는다
- Gradle 빌드를 먼저 수행한다: `./gradlew clean build`
- IntelliJ 사용 시 Annotation Processing 설정을 확인한다
- `clean` 태스크가 `src/main/generated`를 삭제하도록 설정되어 있으므로, 빌드 후 다시 생성되는지 확인한다

### Q2. H2 접속 오류가 발생한다
- `jdbc:h2:tcp://localhost/~/querydsl`이므로 H2 서버를 TCP 모드로 띄워야 한다
- 파일 모드가 아니라 TCP 모드 URL을 사용한다는 점을 확인한다

---

## 9. 로드맵

- [ ] 페이징 최적화(count 쿼리 분리) 정리
- [ ] QueryDSL projection 방식 비교(Projections vs @QueryProjection) 정리
- [ ] 실무 검색 조건 확장(기간/키워드/상태) 예제 추가
- [ ] 테스트 케이스를 README에서 링크로 바로 탐색 가능하게 정리

---

## 10. 참고

- 김영한 Inflearn QueryDSL 강의 기반 실습
- QueryDSL 공식 문서: https://querydsl.com/
